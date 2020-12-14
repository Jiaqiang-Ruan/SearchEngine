import org.apache.lucene.index.Term;

import java.io.*;
import java.util.*;

public class QryExpand {
    private final boolean isExpand;
    private RetrievalModel model;
    private Map<String, ScoreList> scoreLists;
    private int fbDocs;
    private int fbTerms;
    private double fbMu;
    private double fbOrigWeight;
    private String fbExpansionQueryFile;
    private String fbDefaultQryName;

    QryExpand(Map<String, String> params) {
        String fb = params.get("fb");
        isExpand = (fb!=null) && (!fb.toLowerCase().equals("false"));

        if (isExpand && fb.toLowerCase().equals("bm25")){
            model = new RetrievalModelBM25(params);
            fbDefaultQryName = "#wsum";
        } else if (isExpand && fb.toLowerCase().equals("indri")) {
            model = new RetrievalModelIndri(params);
            fbDefaultQryName = "#wand";
        }

        String fbInitialRankingFile = params.get("fbInitialRankingFile");
        if (isExpand && fbInitialRankingFile != null){
            scoreLists = loadRankFile(fbInitialRankingFile);
        }

        if (isExpand) {
            fbDocs = Integer.parseInt(params.get("fbDocs"));
            fbTerms = Integer.parseInt(params.get("fbTerms"));
            fbMu = Double.parseDouble(params.get("fbMu"));
            fbOrigWeight = Double.parseDouble(params.get("fbOrigWeight"));
            fbExpansionQueryFile = params.get("fbExpansionQueryFile");
            File output = new File(fbExpansionQueryFile);
            if (output.exists()) output.delete();
        }
    }

    private Map<String, ScoreList> loadRankFile(String fbInitialRankingFile) {
        scoreLists = new HashMap<>();
        try {
            BufferedReader input = new BufferedReader(
                    new FileReader(fbInitialRankingFile));
            String line;
            while ((line = input.readLine()) != null) {
                String[] tokens = line.split(" ");
                String qid = tokens[0].trim();
                if (!scoreLists.containsKey(qid)) {
                    scoreLists.put(qid, new ScoreList());
                }
                String docId = tokens[2].trim();
                double score = Double.parseDouble(tokens[4].trim());
                scoreLists.get(qid).add(Idx.getInternalDocid(docId), score);
            }
            input.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return scoreLists;
    }

    public String expand(String qLine, String defaultQryName) throws IOException {
        if (!isExpand) return qLine;

        // Build results from model
        String[] pair = qLine.split(":");
        String qid = pair[0].trim();
        String query = pair[1].trim();
        ScoreList results=null;
        if (scoreLists != null) {
            results = scoreLists.get(qid);
        }
        if (results == null && model != null) {
            results = QryEval.processQuery(query, model);
        }

        results.sort();

        Map<Integer, TermVector> info = new HashMap<>();

        // Collect all terms
        Set<String> terms = new HashSet<>();
        for (int i=0;i<Math.min(results.size(), fbDocs); i++) {
            int docId = results.getDocid(i);
            TermVector termVector = new TermVector(docId, "body");
            info.put(docId, termVector);
            for (int j=0;j<termVector.stemsLength();j++) {
                String term = termVector.stemString(j);
                if (term!=null && !term.contains(",") && !term.contains("."))
                    terms.add(term);
            }
        }

        // Build score
        Map<String, Double> scores = new HashMap<>();
        double fieldLen = Idx.getSumOfFieldLengths("body");
        for (String term: terms) {
            double ctf = Idx.getTotalTermFreq("body", term);
            for (int i=0;i<Math.min(results.size(), fbDocs);i++) {
                int docId = results.getDocid(i);
                double modelScore = results.getDocidScore(i);
                double docLen = Idx.getFieldLength("body", docId);
                TermVector termVector = info.get(docId);

                int termId = termVector.indexOfStem(term);
                double tf;
                if (termId==-1) {
                    tf = 0.0;
                } else {
                    tf = termVector.stemFreq(termId);
                }

                double ptd = (tf+fbMu*ctf/fieldLen)/(docLen+fbMu);
                double idf = Math.log(fieldLen/ctf);
                scores.put(term,
                        scores.getOrDefault(term, 0.0) + ptd*modelScore*idf);
            }
        }

        // Sort scores
        List<Map.Entry<String, Double>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        // Build fbQuery
        StringBuilder fbQuerySB = new StringBuilder();

        fbQuerySB.append(fbDefaultQryName+"(");
        for (int i=0;i<Math.min(fbTerms, sortedScores.size());i++) {
            fbQuerySB.append(String.format("%.4f %s ", sortedScores.get(i).getValue(),
                                    sortedScores.get(i).getKey()));
        }
        fbQuerySB.append(")");
        String fbQuery = fbQuerySB.toString();

        // Append fbQuery to file
        appendToFile(String.format("%s: %s\n", qid, fbQuery), fbExpansionQueryFile);

        // Construct final qLine
        return String.format("%s: %s(%f %s(%s) %f %s)", qid, fbDefaultQryName,
                fbOrigWeight, defaultQryName, query, 1.0-fbOrigWeight, fbQuery);
    }

    private static void appendToFile(String line, String filename) throws IOException {
        BufferedWriter output = new BufferedWriter(
                new FileWriter(filename, true));
        output.write(line);
        output.close();
    }
}
