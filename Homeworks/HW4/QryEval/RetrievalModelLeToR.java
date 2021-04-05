/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.SingleTermsEnum;
import org.apache.lucene.index.Term;

import java.io.*;
import java.util.*;

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLeToR extends RetrievalModel {
  double[] minValue;
  double[] maxValue;
  double BM25k1, BM25b, BM25k3;
  double IndriMu, IndriLambda;
  Set<Integer> disable;
  RetrievalModelLeToR(Map<String, String> parameters) {
    BM25k1 = Double.parseDouble(parameters.get("BM25:k_1"));
    BM25b = Double.parseDouble(parameters.get("BM25:b"));
    BM25k3 = Double.parseDouble(parameters.get("BM25:k_3"));
    IndriMu = Double.parseDouble(parameters.get("Indri:mu"));
    IndriLambda = Double.parseDouble(parameters.get("Indri:lambda"));
    disable = new HashSet<>();
    String disFeatString = parameters.get("letor:featureDisable");
    if (disFeatString != null) {
      String[] disFeat = disFeatString.split(",");
      for (String featId:disFeat) {
        disable.add(Integer.parseInt(featId));
      }
    }
    train_svm(parameters);
    test_svm(parameters);
  }

  private double[] getFeat(String query, int docId) {
    double[] feat = new double[16];
    // f1
    try{
      feat[0] = (double) Float.parseFloat(Idx.getAttribute("spamScore", docId));
    } catch (Exception e) {
      feat[0] = Double.MIN_VALUE;
    }

    // f2
    try{
      String url = Idx.getAttribute("rawUrl", docId);
      feat[1] = url.length() - url.replace("/","").length();
    }catch (Exception e) {
      feat[1] = Double.MIN_VALUE;
    }

    // f3
    try{
      String url = Idx.getAttribute("rawUrl", docId);
      if (url.contains("wikipedia.org")) feat[2] = 1.0;
      else feat[2] = 0.0;
    }catch (Exception e) {
      feat[2] = Double.MIN_VALUE;
    }

    // f4
    try{
      feat[3] = (double) Float.parseFloat(Idx.getAttribute("PageRank", docId));
    }catch (Exception e) {
      feat[3] = Double.MIN_VALUE;
    }

    String[] fields = new String[]{"body", "title", "url", "inlink"};
    for (int i=0;i<4;i++) {
      // f5 8 11 14
      feat[4+3*i] = BM25Score(query, docId, fields[i]);
      // f6 9 12 15
      feat[5+3*i] = IndriScore(query, docId, fields[i]);
      // f7 10 13 16
      feat[6+3*i] = overlapScore(query, docId, fields[i]);
    }

    // // f17 customize
    // try {
    //   feat[16] = Double.parseDouble(Idx.getAttribute("date", docId));
    // } catch (Exception e) {
    //   feat[16] = Double.MIN_VALUE;
    // }

    // // f18 customize
    // feat[17] = posStd(query, docId, "body");

    return feat;
  }

  private double posStd(String query, int docid, String field) {
    try {
      String[] terms = QryParser.tokenizeString(query);
      List<Integer> pos = new ArrayList<>();
      Set<String> termSet = new HashSet<>(Arrays.asList(terms));
      TermVector vec = new TermVector(docid, field);
      for (int i=0;i<vec.positionsLength();i++) {
        if (termSet.contains(vec.stemString(i)))
          pos.add(i);
      }

      double mean = 0.0;
      for (int p:pos) {
        mean += p;
      }
      mean = mean / ((double) pos.size() + 0.1);
      double ans = 0.0;
      for (int p:pos) {
        ans += Math.pow(p-mean, 2);
      }
      ans = ans / ((double) pos.size() + 0.1);
      return Math.sqrt(ans);
    } catch (Exception e) {
      return Double.MIN_VALUE;
    }
  }

  private double BM25Score(String query, int docid, String field) {
    try {
      String[] terms = QryParser.tokenizeString(query);
      double score=0.0;
      double docLen = Idx.getFieldLength(field, docid);
      double docNum = Idx.getNumDocs();
      double AveDocLen = (double) Idx.getSumOfFieldLengths(field) /
              (double) Idx.getDocCount(field);
      TermVector vec = new TermVector(docid, field);
      if (vec.positionsLength()==0 || vec.stemsLength()==0) {
        return Double.MIN_VALUE;
      }
      int id;
      double tf, df, term1, term2;
      for (String term: terms) {
        id = vec.indexOfStem(term);
        if (id!=-1) {
          tf = vec.stemFreq(id);
          df = vec.stemDf(id);
          term1 = Math.max(0.0, Math.log((docNum-df+0.5)/(df+0.5)));
          term2 = tf/(tf+BM25k1*(1-BM25b+BM25b*docLen/AveDocLen));

          score += term1*term2;
        }
      }
      return score;
    } catch (Exception e){
      return Double.MIN_VALUE;
    }
  }

  private double IndriScore(String query, int docid, String field) {
    try {
      String[] terms = QryParser.tokenizeString(query);
      double score=1.0;
      double denominate = 1.0/(double) terms.length;
      TermVector vec = new TermVector(docid, field);
      if (vec.positionsLength()==0 || vec.stemsLength()==0) {
        return Double.MIN_VALUE;
      }

      double tf, ctf, clen, p, docLen;
      clen = Idx.getSumOfFieldLengths(field);
      docLen = Idx.getFieldLength(field, docid);

      boolean isFound=false;

      for (String term : terms) {
        if (vec.indexOfStem(term) != -1) {
          tf = vec.stemFreq(vec.indexOfStem(term));
          isFound=true;
        } else {
          tf = 0.0;
        }
        ctf = Idx.getTotalTermFreq(field, term);
        if (ctf < 1.0) ctf = 0.5;
        p = ctf / clen;
        score *= Math.pow((1-IndriLambda)* (tf+IndriMu*p) / (docLen+IndriMu)
                + IndriLambda * p, denominate);

      }
      if (!isFound) return 0.0;
      return score;
    } catch (Exception e) {
      return Double.MIN_VALUE;
    }

  }

  private double overlapScore(String query, int docid, String field) {
    try {
      String[] terms = QryParser.tokenizeString(query);
      double cnt=0.0;
      TermVector vec = new TermVector(docid, field);
      if (vec.positionsLength()==0||vec.stemsLength()==0) {
        return Double.MIN_VALUE;
      }
      for (String term: terms) {
        if (vec.indexOfStem(term)!=-1){
          cnt += 1.0;
        }
      }
      return cnt / (double) terms.length;
    } catch (Exception e) {
      return Double.MIN_VALUE;
    }
  }

  private void normalize(List<double[]> feats) {
    int N=feats.get(0).length;

      minValue = new double[N];
      Arrays.fill(minValue,0,N,Double.MAX_VALUE);
      maxValue = new double[N];
      Arrays.fill(maxValue,0,N,Double.MIN_VALUE);
      for (double[] feat : feats) {
        for (int j = 0; j < N; j++) {
          if (feat[j] == Double.MIN_VALUE) continue;
          minValue[j] = Math.min(minValue[j], feat[j]);
          maxValue[j] = Math.max(maxValue[j], feat[j]);
        }
      }
//      System.out.println("==========");
//      System.out.println(Arrays.toString(minValue));
//      System.out.println(Arrays.toString(maxValue));

    for (int i=0;i<feats.size();i++) {
      normalize(feats.get(i));
    }
  }

  private void normalize(double[] feat) {
    int N=feat.length;
    for (int i=0;i<N;i++) {
      if (feat[i]==Double.MIN_VALUE) continue;
      if (maxValue[i]!=minValue[i]) {
        feat[i] = (feat[i]-minValue[i])/(maxValue[i]-minValue[i]);
      }
    }
  }

  private String svm_line(String rank, String qid, double[] feat, String comment) {
    StringBuilder line = new StringBuilder();
    line.append(String.format("%s qid:%s ", rank, qid));

    for (int j = 0; j < feat.length; j++) {
      if (disable.contains(j+1)) continue;
      if (feat[j]==Double.MIN_VALUE) continue;
      line.append(j + 1).append(":").append(feat[j]).append(" ");
    }
    line.append(String.format(" # %s\n", comment));
    return line.toString();
  }

  private void train_svm(Map<String, String> params) {


    try {
      String trainQryFile = params.get("letor:trainingQueryFile");
      String trainQrelsFile = params.get("letor:trainingQrelsFile");
      String trainFeatFile = params.get("letor:trainingFeatureVectorsFile");

      BufferedReader trainQryReader;
      BufferedReader trainQrelsReader;
      BufferedWriter trainFeatWriter;

      trainQryReader = new BufferedReader(new FileReader(trainQryFile));
      trainQrelsReader = new BufferedReader(new FileReader(trainQrelsFile));
      File file = new File(trainFeatFile);
      if (file.exists()) file.delete();
      file.createNewFile();
      trainFeatWriter = new BufferedWriter(new FileWriter(trainFeatFile));

      // prepare svm data
      String qLine = trainQryReader.readLine();
      String qrelLine = trainQrelsReader.readLine();
      List<double[]> feats = new ArrayList<>();
      List<String> ranks = new ArrayList<>();
      List<String> externalDocids = new ArrayList<>();

      while (qLine != null && qrelLine != null) {
        String[] qToken = qLine.split(":");
        String qQid = qToken[0].trim();
        String[] qrelToken = qrelLine.split(" ");
        String qrelQid = qrelToken[0].trim();

        if (qQid.equals(qrelQid)) {
          String query = qToken[1].trim();
          String externalDocid = qrelToken[2].trim();
          int docId = Idx.getInternalDocid(externalDocid);
          double[] feat = getFeat(query, docId);
          feats.add(feat);
          ranks.add(qrelToken[3].trim());
          externalDocids.add(externalDocid);
          // for next loop
          qrelLine = trainQrelsReader.readLine();
        } else {
          normalize(feats);
          for (int i = 0; i < feats.size(); i++) {
            String rank = Integer.toString(Integer.parseInt(ranks.get(i))+3);
            String line = svm_line(rank, qQid,
                    feats.get(i), externalDocids.get(i));
            trainFeatWriter.write(line);
          }
          feats.clear();
          ranks.clear();
          externalDocids.clear();
          // for next loop
          qLine = trainQryReader.readLine();
        }
      }

      String[] qToken = qLine.split(":");
      String qQid = qToken[0].trim();
      normalize(feats);
      for (int i = 0; i < feats.size(); i++) {
        String rank = Integer.toString(Integer.parseInt(ranks.get(i))+3);
        String line = svm_line(rank, qQid,
                feats.get(i), externalDocids.get(i));
        trainFeatWriter.write(line);
      }

      feats.clear();
      ranks.clear();
      externalDocids.clear();

      trainQryReader.close();
      trainQrelsReader.close();
      trainFeatWriter.close();

      // run svm
      String svmRankLearnPath = params.get("letor:svmRankLearnPath");
      String svmRankParamC = params.get("letor:svmRankParamC");
      String svmRankModelFile = params.get("letor:svmRankModelFile");
      String cmd = String.format("%s -c %s %s %s\n",
              svmRankLearnPath, svmRankParamC,
              trainFeatFile, svmRankModelFile);
      Process cmdProc = Runtime.getRuntime().exec(cmd);
      // consume stdout and print it out for debugging purposes
      BufferedReader stdoutReader = new BufferedReader(
              new InputStreamReader(cmdProc.getInputStream()));
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        System.out.println(line);
      }
      // consume stderr and print it for debugging purposes
      BufferedReader stderrReader = new BufferedReader(
              new InputStreamReader(cmdProc.getErrorStream()));
      while ((line = stderrReader.readLine()) != null) {
        System.out.println(line);
      }
      stdoutReader.close();
      stderrReader.close();

    } catch (Exception e) {
      System.out.println("Error during train svm!");
      e.printStackTrace();
    }
  }

  private void test_svm(Map<String, String> params) {
    Map<String, Map<String, Double>> info = new HashMap<>();
    List<String> uniqueQids = new ArrayList<>();
    List<String> qids = new ArrayList<>();
    List<String> externalDocids = new ArrayList<>();

    try {
      // initialize
      String qryFile = params.get("queryFilePath");
      String testFeatFile = params.get("letor:testingFeatureVectorsFile");
      int outputLen = Integer.parseInt(params.get("trecEvalOutputLength"));
      BufferedReader testQryReader;
      BufferedWriter testFeatWirter;
      RetrievalModel model;
      testQryReader = new BufferedReader(new FileReader(qryFile));
      File file = new File(testFeatFile);
      if (file.exists()) file.delete();
      file.createNewFile();
      testFeatWirter = new BufferedWriter(new FileWriter(testFeatFile));
      model = new RetrievalModelBM25(params);

      // prepare svm data
      String qLine = null;
      List<double[]> feats = new ArrayList<>();
      List<String> tmpExternalDocids = new ArrayList<>();
      while ((qLine = testQryReader.readLine()) != null) {
        System.out.println("Query " + qLine);
        String[] pair = qLine.split(":");

        if (pair.length != 2) {
          throw new IllegalArgumentException
                  ("Syntax error:  Each line must contain one ':'.");
        }

        String qid = pair[0].trim();
        String query = pair[1].trim();
        ScoreList results = QryEval.processQuery(query, model);
        results.sort();

        uniqueQids.add(qid);
        for (int i=0;i<Math.min(results.size(), outputLen);i++) {
          int docId = results.getDocid(i);
          double[] feat = getFeat(query, docId);
          String externalDocid = Idx.getExternalDocid(docId);

          feats.add(feat);
          qids.add(qid);
          externalDocids.add(externalDocid);
          tmpExternalDocids.add(externalDocid);
        }

        normalize(feats);
        for (int i=0;i<feats.size();i++) {
          String line = svm_line("0", qid, feats.get(i), tmpExternalDocids.get(i));
          testFeatWirter.write(line);
        }
        feats.clear();
        tmpExternalDocids.clear();
      }

      testQryReader.close();
      testFeatWirter.close();

      // test svm
      String svmRankClassifyPath = params.get("letor:svmRankClassifyPath");
      String svmRankModelFile = params.get("letor:svmRankModelFile");
      String testScoreFile = params.get("letor:testingDocumentScores");
      String cmd = String.format("%s %s %s %s\n",
              svmRankClassifyPath, testFeatFile,
              svmRankModelFile, testScoreFile);
      Process cmdProc = Runtime.getRuntime().exec(cmd);
      // consume stdout and print it out for debugging purposes
      BufferedReader stdoutReader = new BufferedReader(
              new InputStreamReader(cmdProc.getInputStream()));
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        System.out.println(line);
      }
      // consume stderr and print it for debugging purposes
      BufferedReader stderrReader = new BufferedReader(
              new InputStreamReader(cmdProc.getErrorStream()));
      while ((line = stderrReader.readLine()) != null) {
        System.out.println(line);
      }
      stdoutReader.close();
      stderrReader.close();

      // load score
      BufferedReader testScoreReader = new BufferedReader(new FileReader(testScoreFile));
      int i = 0;
      while ((line = testScoreReader.readLine()) != null) {
        info.computeIfAbsent(qids.get(i),
                k->new HashMap<>()).put(externalDocids.get(i),
                Double.parseDouble(line));
        i+=1;
      }

      // print test result
      printResults(uniqueQids, info, params);

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  static void printResults(List<String> qids, Map<String, Map<String, Double>> info,
                           Map<String, String> params)
          throws IOException {
    String outputFile = params.get("trecEvalOutputPath");
    File file = new File(outputFile);
    if (file.exists()) file.delete();
    file.createNewFile();
    BufferedWriter output = new BufferedWriter(
            new FileWriter(outputFile));

    String format = "%s Q0 %s %d %.12f reference\n";
    for (String qid: qids) {
      List<Map.Entry<String, Double>> list =
              new ArrayList<>(info.get(qid).entrySet());
      list.sort(Map.Entry.<String, Double>comparingByValue().reversed());
      int rank = 1;
      for (Map.Entry<String, Double> entry: list) {
        String line = String.format(format, qid, entry.getKey(), rank, entry.getValue());
        rank += 1;
        output.write(line);
      }
    }
    output.close();
  }

  public String defaultQrySopName () {
    return null;
  }

}
