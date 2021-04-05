/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
  double k1,b,k3;
  RetrievalModelBM25(Map<String, String> parameters) {
    this.k1 = Double.parseDouble(parameters.get ("BM25:k_1"));
    this.b = Double.parseDouble(parameters.get("BM25:b"));
    this.k3 = Double.parseDouble(parameters.get("BM25:k_3"));
  }

  public String defaultQrySopName () {
    return new String ("#sum");
  }

}
