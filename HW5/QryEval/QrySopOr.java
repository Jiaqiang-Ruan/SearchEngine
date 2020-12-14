/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

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

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    }

    //  STUDENTS::
    //  Add support for other retrieval models here.
    else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    }
    else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    }
    else {
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
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      double res = Double.MIN_VALUE;
      for (Qry q: this.args) {
        if (q.docIteratorHasMatch(r) &&
                q.docIteratorGetMatch() == this.docIteratorGetMatch())
          res = Math.max(res, ((QrySop) q).getScore(r));
      }
      return res;
    }
  }

  private double getScoreIndri (RetrievalModel r) throws IOException {
    int docid = this.docIteratorGetMatch();
    double res = 1.0;
    double cur;
    for (Qry q: this.args) {
      if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch()==docid)
        cur = ((QrySop) q).getScore(r);
      else
        cur = ((QrySop) q).getDefaultScore(r, docid);
      res *= 1.0 - cur;
    }
    return res;
  }

  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    double res = 1.0;
    for (Qry q: this.args) {
      res *= 1.0 - ((QrySop) q).getDefaultScore(r, docid);
    }
    return res;
  }
}
