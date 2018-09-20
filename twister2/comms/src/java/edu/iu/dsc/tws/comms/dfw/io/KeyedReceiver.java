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
package edu.iu.dsc.tws.comms.dfw.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageFlags;
import edu.iu.dsc.tws.comms.api.MessageReceiver;

/**
 * This class is the abstract class that all keyed receivers extend. This provides all the basic
 * data structures and performs initialisation on those data structures.
 */
public abstract class KeyedReceiver implements MessageReceiver {
  private static final Logger LOG = Logger.getLogger(KeyedReceiver.class.getName());
  /**
   * Id of the current executor
   */
  protected int executor;

  /**
   * The buffer limit for single key. Messages are not buffered for a key after this limit is
   * reached
   */
  protected int limitPerKey;

  /**
   * The number of keys buffered in this receiver before keys are flushed
   */
  protected int keyLimit;

  /**
   * Once the receiver reaches the buffer limit or some other condition requires the current
   * buffered messages to be flushed, needsFlush needs to be set to true. The progress method
   * will look at this and flush the current buffer if needsFlush is true.
   */
  protected boolean needsFlush;

  /**
   * The dataflow operation that is related to the class instance. Ex - Reduce, Gather, etc.
   */
  protected DataFlowOperation dataFlowOperation;

  /**
   * Map that keeps track of which sources have sent an finished signal. The finish signal may
   * either be a END message or a LAST message in the message flags.
   * Structure - <target, <source, true/false>>
   */
  protected Map<Integer, Map<Integer, Boolean>> finishedSources = new ConcurrentHashMap<>();


  /**
   * Map that keeps all the incoming messages to this receiver. A separate map is kept for each
   * target and witin each target values are mapped according to their key value
   * Structure - <target, <key, List<value>>>
   */
  protected Map<Integer, Map<Object, Queue<Object>>> messages = new HashMap<>();

  @Override
  public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    this.dataFlowOperation = op;
    this.executor = dataFlowOperation.getTaskPlan().getThisExecutor();
    this.limitPerKey = 100; //TODO: use config to init this
    this.keyLimit = 10; //TODO: use config to init this

    for (Map.Entry<Integer, List<Integer>> expectedIdPerTarget : expectedIds.entrySet()) {
      Map<Integer, Boolean> finishedPerTarget = new HashMap<>();

      for (int sources : expectedIdPerTarget.getValue()) {
        finishedPerTarget.put(sources, false);
      }

      finishedSources.put(expectedIdPerTarget.getKey(), finishedPerTarget);
      messages.put(expectedIdPerTarget.getKey(), new HashMap<>());
    }
  }

  @Override
  public boolean onMessage(int source, int path, int target, int flags, Object object) {
    // add the object to the map
    boolean canAdd = true;

    if (messages.get(target) == null) {
      throw new RuntimeException(String.format("Executor %d, Partial receive error. Receiver did"
          + "not expect messages for this target %d", executor, target));
    } else if (!(object instanceof KeyedContent) && !(object instanceof List)) {
      throw new RuntimeException(String.format("Executor %d, Partial receive error. Received"
          + " object which is not of type KeyedContent or List for target %d", executor, target));
    }


    Map<Integer, Boolean> finishedMessages = finishedSources.get(target);

    if ((flags & MessageFlags.END) == MessageFlags.END) {
      finishedMessages.put(source, true);
      return true;
    }

    canAdd = offerMessage(target, object);

    if (canAdd) {
      if ((flags & MessageFlags.LAST) == MessageFlags.LAST) {
        finishedMessages.put(source, true);
      }
    }

    return canAdd;
  }

  /**
   * saves the given message (or messages if the object is a list) into the messages data structure
   *
   * @param target target for which the messages are to be added
   * @param object the message/messages to be added
   * @return true if the message was added or false otherwise
   */
  @SuppressWarnings("rawtypes")
  private boolean offerMessage(int target, Object object) {
    Map<Object, Queue<Object>> messagesPerTarget = messages.get(target);
    if (messagesPerTarget.size() > keyLimit) {
      needsFlush = true;
      LOG.fine(String.format("Executor %d Partial cannot add any further keys needs flush ",
          executor));
      return false;
    }

    if (object instanceof List) {
      List dataList = (List) object;
      Map<Object, List<Object>> tempList = new HashMap<>();
      for (Object dataEntry : dataList) {
        KeyedContent keyedContent = (KeyedContent) dataEntry;
        //If any of the keys are full the method returns false because partial objects cannot be
        //added to the messages data structure
        Object key = keyedContent.getKey();
        if (messagesPerTarget.containsKey(key)
            && messagesPerTarget.get(key).size() >= limitPerKey) {
          return false;
        }
        if (tempList.containsKey(key)) {
          tempList.get(key).add(keyedContent.getValue());
        } else {
          tempList.put(key, new ArrayList<>());
          tempList.get(key).add(keyedContent.getValue());

        }

      }

      for (Object key : tempList.keySet()) {
        if (messagesPerTarget.containsKey(key)) {
          messagesPerTarget.get(key).add(tempList.get(key));
        } else {
          ArrayBlockingQueue<Object> messagesPerKey = new ArrayBlockingQueue<>(limitPerKey);
          messagesPerKey.add(tempList.get(key));
          messagesPerTarget.put(key, messagesPerKey);
        }
      }

    } else {
      KeyedContent keyedContent = (KeyedContent) object;
      if (messagesPerTarget.containsKey(keyedContent.getKey())) {
        if (messagesPerTarget.get(keyedContent.getKey()).size() < limitPerKey) {
          return messagesPerTarget.get(keyedContent.getKey()).add(keyedContent.getValue());
        } else {
          needsFlush = true;
          LOG.fine(String.format("Executor %d Partial cannot add any further values for key "
              + "needs flush ", executor));
          return false;
        }
      } else {
        ArrayBlockingQueue<Object> messagesPerKey = new ArrayBlockingQueue<>(limitPerKey);
        messagesPerKey.add(keyedContent.getValue());
        messagesPerTarget.put(keyedContent.getKey(), messagesPerKey);
      }
    }
    return true;
  }

  /**
   * Once called this method will update the finishedSources data structure so that the given
   * source is marked as finished for each target that is present.
   *
   * @param source the task id of the source that has finished
   */
  @Override
  public void onFinish(int source) {
    for (Integer target : finishedSources.keySet()) {
      Map<Integer, Boolean> finishedMessages = finishedSources.get(target);
      finishedMessages.put(source, true);
    }
  }
}