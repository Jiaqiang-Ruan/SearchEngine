/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *  The SYN operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

  private int distance;

  QryIopWindow(int distance) {
    this.distance = distance;
  }

  /**
   *  Evaluate the query operator; the result is an internal inverted
   *  list that may be accessed via the internal iterators.
   *  @throws IOException Error accessing the Lucene index.
   */
  protected void evaluate () throws IOException {

    //  Create an empty inverted list.  If there are no query arguments,
    //  this is the final result.
    
    this.invertedList = new InvList (this.getField());

    if (args.size () == 0) {
      return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until all of the argument inverted lists are depleted.
    while (this.docIteratorHasMatchAll(null)) {
      int matchDocid = args.get(0).docIteratorGetMatch();
      List<Integer> positions = new ArrayList<>();

      List<Integer> locations = new ArrayList<>();
      while (true) {
        for (Qry q:this.args) {
          if (((QryIop)q).locIteratorHasMatch()) {
            locations.add(((QryIop)q).locIteratorGetMatch());
          } else {
            locations.clear();
            break;
          }
        }
        if (locations.isEmpty()) break;

        int locMin = Collections.min(locations);
        int locMax = Collections.max(locations);
        locations.clear();

        if (locMax-locMin<this.distance) {
          positions.add(locMax);
          for (Qry q:this.args) {
            ((QryIop)q).locIteratorAdvance();
          }
        } else {
          for (Qry q:this.args) {
            ((QryIop)q).locIteratorAdvancePast(locMin);
          }
        }
      }

      if (positions.size() != 0) {
        this.invertedList.appendPosting (matchDocid, positions);
      }
      args.get(0).docIteratorAdvancePast(matchDocid);
    }
  }

}
