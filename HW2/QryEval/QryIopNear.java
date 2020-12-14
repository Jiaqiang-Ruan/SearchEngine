/**
 *  Copyright (c) 2020, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

/**
 *  The SYN operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

  private int distance;

  QryIopNear(int distance) {
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

      QryIop firstArg = (QryIop) args.get(0);
      while (firstArg.locIteratorHasMatch()) {
        QryIop prev, cur;
        int prevId, curId;
        boolean found = true;
        for (int i=1;i<args.size();i++) {
          prev = (QryIop) args.get(i-1);
          cur = (QryIop) args.get(i);
          prevId = prev.locIteratorGetMatch();
          cur.locIteratorAdvancePast(prevId);

          if (!cur.locIteratorHasMatch()) {
            firstArg.locIteratorFinish();
            found = false;
            break;
          }

          curId = cur.locIteratorGetMatch();
          if (curId-prevId>distance) {
            firstArg.locIteratorAdvance();
            found = false;
            break;
          }
        }

        if (found) {
          positions.add(((QryIop)args.get(args.size()-1)).locIteratorGetMatch());
          for (Qry q:args) {
            ((QryIop) q).locIteratorAdvance();
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
