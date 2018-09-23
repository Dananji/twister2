//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.comms.dfw.io.reduce.keyed;

import java.util.logging.Logger;

import edu.iu.dsc.tws.comms.api.ReduceFunction;
import edu.iu.dsc.tws.comms.api.SingularReceiver;

/**
 * Created by pulasthi on 9/20/18.
 */
public class KReduceBatchFinalReceiver extends KReduceReceiver {
  private static final Logger LOG = Logger.getLogger(KReduceBatchFinalReceiver.class.getName());

  /**
   * Final receiver that get the reduced values for the operation
   */
  private SingularReceiver singularReceiver;

  public KReduceBatchFinalReceiver(ReduceFunction reduce, SingularReceiver receiver) {
    this.reduceFunction = reduce;
    this.singularReceiver = receiver;
    this.limitPerKey = 1;
    this.isFinalReceiver = true;
  }

  @Override
  public boolean progress() {
    boolean needsFurtherProgress = false;
    boolean sourcesFinished = false;
    for (int target : messages.keySet()) {

      if (batchDone.get(target)) {
        continue;
      }

      sourcesFinished = isSourcesFinished(target);
      if (!sourcesFinished) {
        needsFurtherProgress = true;
      }

      if (sourcesFinished && dataFlowOperation.isDelegeteComplete()) {
        batchDone.put(target, true);
        //TODO: check if we can simply remove the data, that is use messages.remove()
        singularReceiver.receive(target, messages.get(target));
      }


    }

    return needsFurtherProgress;
  }
}