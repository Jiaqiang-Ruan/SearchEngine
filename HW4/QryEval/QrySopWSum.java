/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopWSum extends QrySop {

  QrySopWSum() {
    this.weights = new ArrayList<>();
  }

  public void appendWeight(double weight) {
    this.weights.add(weight);
  }
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    if (r instanceof RetrievalModelIndri) return this.docIteratorHasMatchMin(r);
    return this.docIteratorHasMatchAll (r);
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
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    } else if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25(r);
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
      double res = Double.MAX_VALUE;
      for (Qry q: this.args) {
        res = Math.min(res, ((QrySop) q).getScore(r));
      }
      return res;
    }
  }

  private double getScoreIndri (RetrievalModel r) throws IOException {
    // Whether match or not, there will be a score
    int docid = this.docIteratorGetMatch();
    double res = 0.0;
    double total = 0.0;
    for (double w: this.weights) {
      total += w;
    }
    for (int i=0;i<this.args.size();i++) {
      QrySop q = (QrySop) this.args.get(i);
      double w = this.weights.get(i);
      if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch()==docid)
        res += q.getScore(r)* w/total;
      else
        res += q.getDefaultScore(r, docid)* w/total;
    }
    return res;
  }

  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    double res = 0.0;
    double total = 0.0;
    for (double w: this.weights) {
      total += w;
    }
    for (int i=0;i<this.args.size();i++) {
      QrySop q = (QrySop) this.args.get(i);
      double w = this.weights.get(i);
      res += q.getDefaultScore(r, docid)* w/total;
    }
    return res;
  }

  private double getScoreBM25(RetrievalModel r) throws IOException {
    // Whether match or not, there will be a score
    double ans = 0.0;
    int docid = this.docIteratorGetMatch();
    for (Qry qi: this.args) {
      QrySop q = (QrySop) qi;
      if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch()==docid) {
        ans += q.getScore(r);
      }
    }
    return ans;
  }
}
