/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
  double mu, lambda;
  RetrievalModelIndri(Map<String, String> parameters) {
    this.mu = Double.parseDouble(parameters.get ("Indri:mu"));
    this.lambda = Double.parseDouble(parameters.get("Indri:lambda"));
  }

  public String defaultQrySopName () {
    return new String ("#and");
  }

}
