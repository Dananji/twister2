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
package edu.iu.dsc.tws.comms.mpi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.Pair;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageHeader;
import edu.iu.dsc.tws.comms.api.MessageReceiver;
import edu.iu.dsc.tws.comms.api.MessageType;
import edu.iu.dsc.tws.comms.core.TaskPlan;
import edu.iu.dsc.tws.comms.mpi.io.MPIMessageDeSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MPIMessageSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MessageDeSerializer;
import edu.iu.dsc.tws.comms.mpi.io.MessageSerializer;
import edu.iu.dsc.tws.comms.routing.InvertedBinaryTreeRouter;
import edu.iu.dsc.tws.comms.utils.KryoSerializer;

public class MPIDataFlowReduce implements DataFlowOperation, MPIMessageReceiver {
  private static final Logger LOG = Logger.getLogger(MPIDataFlowBroadcast.class.getName());

  // the source tasks
  protected Set<Integer> sources;

  // the destination task
  protected int destination;

  private InvertedBinaryTreeRouter router;

  private MessageReceiver finalReceiver;

  private MessageReceiver partialReceiver;

  private int index = 0;

  private int pathToUse = MPIContext.DEFAULT_PATH;

  private MPIDataFlowOperation delegete;
  private Config config;
  private TaskPlan instancePlan;
  private int executor;
  private MessageType type;

  public MPIDataFlowReduce(TWSMPIChannel channel, Set<Integer> sources, int destination,
                           MessageReceiver finalRcvr,
                           MessageReceiver partialRcvr, int indx, int p) {
    this.index = indx;
    this.sources = sources;
    this.destination = destination;
    this.finalReceiver = finalRcvr;
    this.partialReceiver = partialRcvr;
    this.pathToUse = p;
    this.delegete = new MPIDataFlowOperation(channel);
  }

  public MPIDataFlowReduce(TWSMPIChannel channel, Set<Integer> sources, int destination,
                           MessageReceiver finalRcvr, MessageReceiver partialRcvr) {
    this(channel, sources, destination, finalRcvr, partialRcvr, 0, 0);
  }

  private boolean isLast(int source, int path, int taskIdentifier) {
    return router.isLastReceiver();
  }

  /**
   * We can receive messages from internal tasks or an external task, we allways receive messages
   * to the main task of the executor and we go from there
   *
   * @param currentMessage
   * @param object
   */
  public boolean receiveMessage(MPIMessage currentMessage, Object object) {
    MessageHeader header = currentMessage.getHeader();

    // we always receive to the main task
    int messageDestId = currentMessage.getHeader().getDestinationIdentifier();
    // check weather this message is for a sub task
    if (!isLast(header.getSourceId(), header.getFlags(), messageDestId)
        && partialReceiver != null) {
      return partialReceiver.onMessage(header.getSourceId(), MPIContext.DEFAULT_PATH,
          router.mainTaskOfExecutor(instancePlan.getThisExecutor(),
              MPIContext.DEFAULT_PATH), header.getFlags(), object);
    } else {
      return finalReceiver.onMessage(header.getSourceId(), MPIContext.DEFAULT_PATH,
          router.mainTaskOfExecutor(instancePlan.getThisExecutor(),
              MPIContext.DEFAULT_PATH), header.getFlags(), object);
    }
  }

  private RoutingParameters partialSendRoutingParameters(int source, int path) {
    RoutingParameters routingParameters = new RoutingParameters();
    // get the expected routes
    Map<Integer, Set<Integer>> internalRoutes = router.getInternalSendTasks(source);
    if (internalRoutes == null) {
      throw new RuntimeException("Un-expected message from source: " + source);
    }

    Set<Integer> sourceInternalRouting = internalRoutes.get(source);
    if (sourceInternalRouting != null) {
      routingParameters.addInternalRoutes(sourceInternalRouting);
    }

    // get the expected routes
    Map<Integer, Set<Integer>> externalRoutes =
        router.getExternalSendTasksForPartial(source);
    if (externalRoutes == null) {
      throw new RuntimeException("Un-expected message from source: " + source);
    }

    Set<Integer> sourceRouting = externalRoutes.get(source);
    if (sourceRouting != null) {
      routingParameters.addExternalRoutes(sourceRouting);
    }

    routingParameters.setDestinationId(router.destinationIdentifier(source, path));
    return routingParameters;
  }

  private RoutingParameters sendRoutingParameters(int source, int path) {
    RoutingParameters routingParameters = new RoutingParameters();

    // get the expected routes
    Map<Integer, Set<Integer>> internalRouting = router.getInternalSendTasks(source);
    if (internalRouting == null) {
      throw new RuntimeException("Un-expected message from source: " + source);
    }

    // we are going to add source if we are the main executor
    if (router.mainTaskOfExecutor(instancePlan.getThisExecutor(),
        MPIContext.DEFAULT_PATH) == source) {
      routingParameters.addInteranlRoute(source);
    }

    // we should not have the route for main task to outside at this point
    Set<Integer> sourceInternalRouting = internalRouting.get(source);
    if (sourceInternalRouting != null) {
      routingParameters.addInternalRoutes(sourceInternalRouting);
    }

    routingParameters.setDestinationId(router.destinationIdentifier(source, path));
    return routingParameters;
  }

  protected boolean isLastReceiver() {
    return router.isLastReceiver();
  }

  public boolean receiveSendInternally(int source, int t, int path, int flags, Object message) {
    // check weather this is the last task
    if (router.isLastReceiver()) {
//      LOG.info(String.format("%d Calling directly final receiver %d",
//          instancePlan.getThisExecutor(), source));
      return finalReceiver.onMessage(source, path, t, flags, message);
    } else {
      return partialReceiver.onMessage(source, path, t, flags, message);
    }
  }

  @Override
  public boolean passMessageDownstream(Object object, MPIMessage currentMessage) {
    return true;
  }

  @Override
  public boolean send(int source, Object message, int flags) {
    return delegete.sendMessage(source, message, pathToUse, flags,
        sendRoutingParameters(source, pathToUse));
  }

  @Override
  public boolean send(int source, Object message, int flags, int dest) {
    return delegete.sendMessage(source, message, dest, flags,
        sendRoutingParameters(source, pathToUse));
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags, int dest) {
    return delegete.sendMessagePartial(source, message, dest, flags,
        partialSendRoutingParameters(source, dest));
  }

  @Override
  public void init(Config cfg, MessageType t, TaskPlan taskPlan, int edge) {
    this.instancePlan = taskPlan;
    this.config = cfg;
    this.type = t;

    // we only have one path
    this.router = new InvertedBinaryTreeRouter(cfg, taskPlan,
        destination, sources, index);

    // initialize the receive
    if (this.partialReceiver != null && !isLastReceiver()) {
      partialReceiver.init(cfg, this, receiveExpectedTaskIds());
    }

    if (this.finalReceiver != null && isLastReceiver()) {
      this.finalReceiver.init(cfg, this, receiveExpectedTaskIds());
    }

    LOG.info(String.format("%d all send tasks: %s", executor, router.sendQueueIds()));

    Map<Integer, ArrayBlockingQueue<Pair<Object, MPISendMessage>>> pendingSendMessagesPerSource =
        new HashMap<>();
    Map<Integer, Queue<Pair<Object, MPIMessage>>> pendingReceiveMessagesPerSource = new HashMap<>();
    Map<Integer, Queue<MPIMessage>> pendingReceiveDeSerializations = new HashMap<>();

    Set<Integer> srcs = router.sendQueueIds();
    for (int s : srcs) {
      // later look at how not to allocate pairs for this each time
      ArrayBlockingQueue<Pair<Object, MPISendMessage>> pendingSendMessages =
          new ArrayBlockingQueue<Pair<Object, MPISendMessage>>(
              MPIContext.sendPendingMax(cfg));
      pendingSendMessagesPerSource.put(s, pendingSendMessages);
    }

    int maxReceiveBuffers = MPIContext.receiveBufferCount(cfg);
    int receiveExecutorsSize = receivingExecutors().size();
    if (receiveExecutorsSize == 0) {
      receiveExecutorsSize = 1;
    }
    Set<Integer> execs = router.receivingExecutors();
    for (int e : execs) {
      int capacity = maxReceiveBuffers * 2 * receiveExecutorsSize;
      Queue<Pair<Object, MPIMessage>> pendingReceiveMessages =
          new ArrayBlockingQueue<Pair<Object, MPIMessage>>(
              capacity);
      pendingReceiveMessagesPerSource.put(e, pendingReceiveMessages);
      pendingReceiveDeSerializations.put(e, new ArrayBlockingQueue<MPIMessage>(capacity));
    }

    KryoSerializer kryoSerializer = new KryoSerializer();
    kryoSerializer.init(new HashMap<String, Object>());

    MessageDeSerializer messageDeSerializer = new MPIMessageDeSerializer(kryoSerializer);
    MessageSerializer messageSerializer = new MPIMessageSerializer(kryoSerializer);

    delegete.init(cfg, t, taskPlan, edge,
        router.receivingExecutors(), router.isLastReceiver(), this,
        pendingSendMessagesPerSource, pendingReceiveMessagesPerSource,
        pendingReceiveDeSerializations, messageSerializer, messageDeSerializer);
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags) {
    // now what we need to do
    return delegete.sendMessagePartial(source, message, pathToUse, flags,
        partialSendRoutingParameters(source, pathToUse));
  }

  protected Set<Integer> receivingExecutors() {
    return router.receivingExecutors();
  }

  public Map<Integer, List<Integer>> receiveExpectedTaskIds() {
    Map<Integer, List<Integer>> integerMapMap = router.receiveExpectedTaskIds();
    // add the main task to receive from iteself
    int key = router.mainTaskOfExecutor(instancePlan.getThisExecutor(), MPIContext.DEFAULT_PATH);
    List<Integer> mainReceives = integerMapMap.get(key);
    if (mainReceives == null) {
      mainReceives = new ArrayList<>();
      integerMapMap.put(key, mainReceives);
    }
    if (key != destination) {
      mainReceives.add(key);
    }
    return integerMapMap;
  }

  @Override
  public void progress() {
    delegete.progress();

    finalReceiver.progress();
    partialReceiver.progress();
  }

  @Override
  public void close() {

  }

  @Override
  public void finish() {

  }

  @Override
  public MessageType getType() {
    return type;
  }

  @Override
  public TaskPlan getTaskPlan() {
    return instancePlan;
  }
}
