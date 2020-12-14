/*
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.6.
 *  
 *  Compatible with Lucene 8.1.1.
 */
import java.io.*;
import java.util.*;

/**
 *  This software illustrates the architectrue for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {
    
    Timer timer = new Timer();
    timer.start ();

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    Idx.open (parameters.get ("indexPath"));



    if (parameters.get("rerank")!=null &&
            parameters.get("rerank").toLowerCase().equals("true")) {
      new RetrievalModelDRMM(parameters);
    } else if (parameters.get ("retrievalAlgorithm")!=null &&
            parameters.get ("retrievalAlgorithm").toLowerCase().equals("letor")) {
      new RetrievalModelLeToR(parameters);
    } else {
      RetrievalModel model = initializeRetrievalModel (parameters);
      processQueryFile(parameters, model);
    }

    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  public static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    if (parameters.get("retrievalAlgorithm") == null && parameters.get("diversity").toLowerCase().equals("true")) {
      return new RetrievalModelUnrankedBoolean();
    }

    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    } else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    } else if (modelString.equals("indri")) {
      model = new RetrievalModelIndri(parameters);
    } else if (modelString.equals("bm25")) {
      model = new RetrievalModelBM25(parameters);
    } else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc 
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {
      ScoreList results = new ScoreList ();
      if (q.args.size () > 0) {		// Ignore empty queries
        q.initialize (model);
        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          results.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return results;
    } else
      return null;
  }

  /**
   *  Process the query file.
   *  @param params Params
   *  @param model A retrieval model that will guide matching and scoring
   *  @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(Map<String, String> params,
                               RetrievalModel model)
      throws IOException {
    QryExpand expander = new QryExpand(params);
    QryDiversity diversity = new QryDiversity(params);

    BufferedReader input = null;
    File output = new File(params.get("trecEvalOutputPath"));
    try {
      if (output.exists()) output.delete();
      output.createNewFile();

      String qLine = null;
      input = new BufferedReader(new FileReader(params.get("queryFilePath")));

      //  Each pass of the loop processes one query.
      while ((qLine = input.readLine()) != null) {
        qLine = expander.expand(qLine, model.defaultQrySopName());
        printMemoryUsage(false);
        System.out.println("Query " + qLine);
    	String[] pair = qLine.split(":");

        if (pair.length != 2) {
              throw new IllegalArgumentException
                ("Syntax error:  Each line must contain one ':'.");
        }

        String qid = pair[0];
        String query = pair[1];
//        ScoreList results = processQuery(query, model);
        ScoreList results = diversity.processQuery(qid, query, model);

        if (results != null) {
          results.sort();
          printResults(qid, results, params);
          System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * 
   * STUDENTS:: 
   * This is not the correct output format. You must change this method so
   * that it outputs in the format specified in the homework page, which is:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result, Map<String, String> params)
          throws IOException {
    int number = Integer.parseInt(params.get("trecEvalOutputLength"));
    BufferedWriter output = new BufferedWriter(
            new FileWriter(params.get("trecEvalOutputPath"), true));

    String format = "%s Q0 %s %d %.18f reference\n";
    StringBuilder sb = new StringBuilder();
    if (result.size() < 1) {
      sb.append(String.format(format, queryName, "dummy", 1, 0.0));
    } else {
      for (int i = 0; i < Math.min(number, result.size()); i++) {
        sb.append(String.format(format, queryName,
                Idx.getExternalDocid(result.getDocid(i)),
                i+1, result.getDocidScore(i)));
      }
    }
    System.out.print(sb.toString());
    output.write(sb.toString());
    output.close();
  }

  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();
    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    //  Store (all) key/value parameters in a hashmap.

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    //  Confirm that some of the essential parameters are present.
    //  This list is not complete.  It is just intended to catch silly
    //  errors.

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

}
