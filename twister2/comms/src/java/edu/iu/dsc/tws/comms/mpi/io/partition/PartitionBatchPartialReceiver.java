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
package edu.iu.dsc.tws.comms.mpi.io.partition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.mpi.MPIDataFlowPartition;

/**
 * Created by pulasthi on 5/10/18.
 */
public class PartitionBatchPartialReceiver extends PartitionBatchReceiver {
  private Map<Integer, Map<Integer, Boolean>> finished;
  private MPIDataFlowPartition dataFlowOperation;
  protected Map<Integer, Map<Integer, Queue<Object>>> messages = new HashMap<>();
  protected Map<Integer, Map<Integer, Queue<Integer>>> flagsMap = new HashMap<>();
  protected Map<Integer, Map<Integer, Integer>> bufferCounts = new HashMap<>();
  protected int totalPendingMessages = 0;

  public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    finished = new ConcurrentHashMap<>();
    dataFlowOperation = (MPIDataFlowPartition) op;
    for (Integer source : expectedIds.keySet()) {
      Map<Integer, Boolean> perTarget = new ConcurrentHashMap<>();
      Map<Integer, Queue<Integer>> perTargetFlags = new ConcurrentHashMap<>();
      Map<Integer, Queue<Object>> perTargetMessages = new ConcurrentHashMap<>();
      Map<Integer, Integer> perTargetBufferCounts = new ConcurrentHashMap<>();
      for (Integer target : expectedIds.get(source)) {
        perTarget.put(target, false);
        perTargetFlags.put(target, new ArrayBlockingQueue<Integer>(sendPendingMax));
        perTargetMessages.put(target, new ArrayBlockingQueue<Object>(sendPendingMax));
        perTargetBufferCounts.put(target, 0);
      }
      finished.put(source, perTarget);
      flagsMap.put(source, perTargetFlags);
      messages.put(source, perTargetMessages);
      bufferCounts.put(source, perTargetBufferCounts);
    }
  }

  @Override
  public boolean onMessage(int source, int finalTarget, int target, int flags, Object object) {
    // add the object to the map
    if (messages.get(source).get(finalTarget).size() < sendPendingMax
        || totalPendingMessages > sendPendingMaxTotal) {
      messages.get(source).get(finalTarget).add(object);
      flagsMap.get(source).get(finalTarget).add(flags);
      bufferCounts.get(source).put(finalTarget, bufferCounts.get(source).get(finalTarget) + 1);
      totalPendingMessages += 1;
    } else {
      // pending message limits have been met
      return false;
    }

//    if ((flags & MessageFlags.FLAGS_LAST) == MessageFlags.FLAGS_LAST) {
//      finished.get(target).put(source, true);
//    }

//    if (((flags & MessageFlags.FLAGS_LAST) == MessageFlags.FLAGS_LAST) && isAllFinished(target)) {
//      System.out.println(Arrays.toString((byte[]) object));
//      System.out.printf("All Done for Task %d \n", target);
//    }
    return true;
  }

  @Override
  public void progress() {
    //buffer code
    List<Object> dataList = new ArrayList<>(bufferSize);
    for (Integer source : bufferCounts.keySet()) {
      for (Integer target : bufferCounts.get(source).keySet()) {
        dataList = new ArrayList<>();
        if (bufferCounts.get(source).get(target) > bufferSize) {
          //Send message
          Queue<Object> temp = messages.get(source).get(target);
          for (int i = 0; i < bufferSize; i++) {
            dataList.add(temp.poll());
          }
          bufferCounts.get(source).put(target, bufferCounts.get(source).get(target) - bufferSize);
          //send message code here
//          dataFlowOperation.sendPartial(source, messages.get(source).get(target).poll(),
//              flagsMap.get(source).get(target).poll(), target);
        }
      }
    }

    //This will be used for now until we have the ability to change types working the buffering code

    for (Integer source : messages.keySet()) {
      for (Integer target : messages.get(source).keySet()) {
        if (messages.get(source).get(target).size() > 0) {
          dataFlowOperation.sendPartial(source, messages.get(source).get(target).poll(),
              flagsMap.get(source).get(target).poll(), target);
        }
      }
    }
  }
}
