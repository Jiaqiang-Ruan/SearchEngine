import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class QryDiversity {
    private final boolean isDiversity;
    private String initialRankingFile;
    private int maxInputRankingsLength;
    private int maxResultRankingLength;
    private String algorithm;
    private String intentsFile;
    private double lambda;

    QryDiversity(Map<String, String> params) {
        isDiversity = params.get("diversity") != null &&
                params.get("diversity").toLowerCase().equals("true");
        System.out.printf("isdiversity: %b", isDiversity);
        System.out.println("==============");
        if (isDiversity) {
            initialRankingFile = params.get("diversity:initialRankingFile");
            maxInputRankingsLength = Integer.parseInt(params.get(
                    "diversity:maxInputRankingsLength"));
            maxResultRankingLength = Integer.parseInt(params.get(
                    "diversity:maxResultRankingLength"));
            algorithm = params.get("diversity:algorithm");
            intentsFile = params.get("diversity:intentsFile");
            lambda = Double.parseDouble(params.get("diversity:lambda"));

            System.out.printf("algorithm %s\n", algorithm);
        }

    }

    private void getScoresLists(Map<String, ScoreList> scoresLists, Set<String> subqids,
                         String qid, String query, RetrievalModel model)
            throws IOException {
        if (initialRankingFile != null) {
            BufferedReader input = new BufferedReader(new FileReader(initialRankingFile));
            String qLine;
            String prevQid = null;
            ScoreList tmp = new ScoreList();

            while ((qLine = input.readLine()) != null) {

                String[] tokens = qLine.split(" ");
                if (tokens[0].equals(qid) || tokens[0].startsWith(qid+".")) {

                    if (tokens[0].startsWith(qid+".")) {
                        subqids.add(tokens[0]);
                    }
                    if (prevQid != null && !tokens[0].equals(prevQid)) {
                        scoresLists.put(prevQid, tmp);
                        tmp = new ScoreList();
                    }
                    int docid = -1;
                    try {
                        docid = Idx.getInternalDocid(tokens[2]);
                        if (!tokens[2].equals(Idx.getExternalDocid(docid))){
                            System.out.println(tokens[2]);
                            System.out.println(docid);
                            System.out.println(Idx.getExternalDocid(docid));
                            System.out.println("======docid error ======");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    double score = Double.parseDouble(tokens[4]);
                    tmp.add(docid, score);
                    prevQid = tokens[0];
                }
            }
            if (prevQid != null) {
                scoresLists.put(prevQid, tmp);
            }
            input.close();
        } else {
            BufferedReader input = new BufferedReader(new FileReader(intentsFile));
            String qLine;
            while ((qLine = input.readLine()) != null) {
                String[] tokens = qLine.split(":");
                System.out.println(Arrays.toString(tokens));
                if (tokens[0].startsWith(qid + ".")) {
                    subqids.add(tokens[0]);
                    scoresLists.put(tokens[0], QryEval.processQuery(tokens[1], model));
                }
            }
            input.close();
        }
        if (! scoresLists.containsKey(qid)) {
            scoresLists.put(qid, QryEval.processQuery(query, model));
        }
        for (ScoreList tmp:scoresLists.values()) {
            tmp.sort();
        }
    }

    private void getDocidToScore(String qid, Map<Integer, Map<String, Double>> docidToScore, List<Integer> orthodoxRank,
                                 Set<String> subqids, Map<String, ScoreList> scoresLists) {
        double base = 0.0;
        for (int i=0;i<Math.min(scoresLists.get(qid).size(), maxInputRankingsLength);i++) {
            orthodoxRank.add(scoresLists.get(qid).getDocid(i));
            docidToScore.put(scoresLists.get(qid).getDocid(i), new HashMap<>());
            base += scoresLists.get(qid).getDocidScore(i);
        }

        for (String tmpQid:subqids) {
            double tmpBase = 0.0;
            for (int i=0;i<Math.min(scoresLists.get(tmpQid).size(), maxInputRankingsLength);i++) {
                int docid = scoresLists.get(tmpQid).getDocid(i);
                if (docidToScore.containsKey(docid)) {
                    tmpBase += scoresLists.get(tmpQid).getDocidScore(i);
                }
            }
            base = Math.max(base, tmpBase);
        }


        if (base < 1.0) base = 1.0;

        for (int i=0;i<Math.min(scoresLists.get(qid).size(), maxInputRankingsLength);i++) {
            docidToScore.get(scoresLists.get(qid).getDocid(i)).put(
                    qid,scoresLists.get(qid).getDocidScore(i)/base);
        }
        for (String tmpQid:subqids) {
            for (int i=0;i<Math.min(scoresLists.get(tmpQid).size(), maxInputRankingsLength);i++) {
                int docid = scoresLists.get(tmpQid).getDocid(i);
                if (docidToScore.containsKey(docid)){
                    docidToScore.get(docid).put(tmpQid,scoresLists.get(tmpQid).getDocidScore(i)/base);
                }
            }
        }
    }

    public ScoreList processQuery(String qid, String query, RetrievalModel model)
        throws IOException {
        if (! isDiversity) {
            return QryEval.processQuery(query, model);
        }

        Map<String, ScoreList> scoresLists = new HashMap<>();
        Set<String> subqids = new HashSet<>();
        getScoresLists(scoresLists, subqids, qid, query, model);


        Map<Integer, Map<String, Double>> docidToScore = new HashMap<>();
        List<Integer> orthodoxRank = new ArrayList<>();
        getDocidToScore(qid, docidToScore, orthodoxRank, subqids, scoresLists);

        if (algorithm.toLowerCase().equals("xquad")) {
            return xquad(qid, subqids, docidToScore, orthodoxRank);
        } else if (algorithm.toLowerCase().equals("pm2")) {
            return pm2(qid, subqids, docidToScore, orthodoxRank);
        }
        return null;

    }

    private ScoreList xquad(String qid, Set<String> subqids, Map<Integer, Map<String, Double>> docidToScore,
                            List<Integer> orthodoxRank) {
        ScoreList result = new ScoreList();
        while (!orthodoxRank.isEmpty() && result.size() < maxResultRankingLength) {
            int maxDocid = -1;
            double maxDocidScore = -Double.MAX_VALUE;

            for (int docid:orthodoxRank) {
                double tmpDocidScore = (1-lambda)*docidToScore.get(docid).getOrDefault(qid, 0.0);
                for (String tmpQid:subqids) {
                    double already = 1.0;
                    for (int i=0;i<result.size();i++) {
                        already *= 1-docidToScore.get(result.getDocid(i)).getOrDefault(tmpQid,0.0);
                    }
                    tmpDocidScore += lambda/(double)subqids.size()*docidToScore.get(docid).getOrDefault(tmpQid, 0.0)
                            * already;
                }
                if (tmpDocidScore > maxDocidScore) {
                    maxDocid = docid;
                    maxDocidScore = tmpDocidScore;
                }
            }

            result.add(maxDocid, maxDocidScore);
            orthodoxRank.remove((Integer) maxDocid);
        }
        result.sort();
        return result;
    }

    private ScoreList pm2(String qid, Set<String> subqids, Map<Integer, Map<String, Double>> docidToScore,
                        List<Integer> orthodoxRank) {

        int N = subqids.size();
        double v = (double) maxResultRankingLength / (double) N;
        Map<String, Double> q = new HashMap<>();
        Map<String, Double> s = new HashMap<>();

        for (String subqid: subqids) {
            s.put(subqid, 0.0);
        }

        ScoreList result = new ScoreList();
        while (!orthodoxRank.isEmpty() && result.size() < maxResultRankingLength) {
            String maxQid = null;
            double maxQidScore = -Double.MAX_VALUE;
            for (String tmpQid:subqids) {
                double tmpQidScore = v / (2*s.get(tmpQid)+1);
                q.put(tmpQid, tmpQidScore);
                if (tmpQidScore > maxQidScore) {
                    maxQid = tmpQid;
                    maxQidScore = tmpQidScore;
                }
            }

            int maxDocid = -1;
            double maxDocidScore = -Double.MAX_VALUE;
            double maxDocidSum = -Double.MAX_VALUE;
            for (int docid:orthodoxRank) {
                double tmpDocidScore = lambda*q.get(maxQid)*docidToScore.get(
                        docid).getOrDefault(maxQid,0.0);
                double sum = docidToScore.get(docid).getOrDefault(maxQid,0.0);
                for (String tmpQid:subqids) {
                    if (!tmpQid.equals(maxQid)) {
                        tmpDocidScore += (1.0-lambda)*q.get(tmpQid)*docidToScore.get(
                                docid).getOrDefault(tmpQid, 0.0);
                        sum += docidToScore.get(docid).getOrDefault(tmpQid,0.0);
                    }
                }

                if (tmpDocidScore > maxDocidScore) {
                    maxDocid = docid;
                    maxDocidScore = tmpDocidScore;
                    maxDocidSum = sum;
                }
            }


            result.add(maxDocid, maxDocidScore);
            if (maxDocidScore != 0.0 ) {
                for (String tmpQid:subqids) {
                    s.put(tmpQid, s.get(tmpQid)+docidToScore.get(maxDocid).getOrDefault(tmpQid, 0.0) / maxDocidSum);
                }
            }

            docidToScore.remove(maxDocid);
            orthodoxRank.remove((Integer) maxDocid);
        }
        result.sort();
        return result;
    }
}