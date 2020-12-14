/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {

    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreBM25 (RetrievalModel r) throws IOException {
    int docid = this.docIteratorGetMatch();
    double res = 0.0;
    for (Qry q: this.args) {
      if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch()==docid)
        res += ((QrySop) q).getScore(r);
    }
    return res;
  }
}
