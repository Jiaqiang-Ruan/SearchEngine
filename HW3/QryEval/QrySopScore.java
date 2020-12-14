/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      Qry q = this.args.get(0);
      // the arg for QryScore must be a QryIop with Inveted List
      return ((QryIop) q).docIteratorGetMatchPosting().tf;
    }
  }

  public double getScoreIndri(RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      System.out.println("ERROR! this function shouldn't be " +
              "called if there is no match.");
      return -1.0;
    }
    QryIop q = (QryIop) this.args.get(0);
    double mu = ((RetrievalModelIndri) r).mu;
    double lambda = ((RetrievalModelIndri) r).lambda;

    double tf = q.docIteratorGetMatchPosting().tf;
    double ctf = q.getCtf();
    double lengthDoc = Idx.getFieldLength(q.field, q.docIteratorGetMatch());
    double lengthCol = Idx.getSumOfFieldLengths(q.field);

    double p = ctf / lengthCol;
    return (1.0-lambda) * (tf+mu*p) / (lengthDoc+mu) +
            lambda * p;
  }

  public double getDefaultScore(RetrievalModel r, long docid) throws IOException {
    QryIop q = (QryIop) this.args.get(0);
    double mu = ((RetrievalModelIndri) r).mu;
    double lambda = ((RetrievalModelIndri) r).lambda;

    double tf = 0.0;
    double ctf = q.getCtf();
    if (ctf < 1.0) ctf = 0.5;
    double lengthDoc = Idx.getFieldLength(q.field, (int) docid);
    double lengthCol = Idx.getSumOfFieldLengths(q.field);

    double p = ctf / lengthCol;
    return  (1.0-lambda) * (tf+mu*p) / (lengthDoc+mu) +
            lambda * p;
  }

  private double getScoreBM25(RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    }
    QryIop q = (QryIop) this.args.get(0);
    double k1 = ((RetrievalModelBM25)r).k1;
    double b = ((RetrievalModelBM25)r).b;
    double k3 = ((RetrievalModelBM25)r).k3;

    double tf = q.docIteratorGetMatchPosting().tf;
    double df = q.getDf();

    double N = Idx.getNumDocs();
    double lengthDoc = Idx.getFieldLength(q.field, q.docIteratorGetMatch());
    double aveLengthDoc = (double) Idx.getSumOfFieldLengths(q.field) /
            (double) Idx.getDocCount(q.field);

    return Math.max(0.0, Math.log((N-df+0.5)/(df+0.5))) *
            tf/(tf+k1*(1-b+b*lengthDoc/aveLengthDoc));
  }
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
