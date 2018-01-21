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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageReceiver;
import edu.iu.dsc.tws.comms.api.MessageType;
import edu.iu.dsc.tws.comms.core.TaskPlan;

public class MPIDataFlowKReduce implements DataFlowOperation {
  private static final Logger LOG = Logger.getLogger(MPIDataFlowKReduce.class.getName());
  // the source tasks
  protected Set<Integer> sources;

  // the destination task
  private Set<Integer> destinations;

  // one reduce for each destination
  private Map<Integer, MPIDataFlowReduce> reduceMap;

  // the partial receiver
  private MessageReceiver partialReceiver;

  // the final receiver
  private MessageReceiver finalReceiver;

  private TWSMPIChannel channel;

  private Set<Integer> edges;

  private int executor;

  public MPIDataFlowKReduce(TWSMPIChannel chnl,
                            Set<Integer> sources, Set<Integer> destination,
                            MessageReceiver finalRecv,
                            MessageReceiver partialRecv, Set<Integer> es) {
    this.channel = chnl;
    this.sources = sources;
    this.destinations = destination;
    this.partialReceiver = partialRecv;
    this.finalReceiver = finalRecv;
    this.edges = es;
    this.reduceMap = new HashMap<>();
  }

  @Override
  public boolean send(int source, Object message, int flags) {
    throw new RuntimeException("Not implemented");
  }

  @Override
  public boolean send(int source, Object message, int flags, int dest) {
    MPIDataFlowReduce reduce = reduceMap.get(dest);
    if (reduce == null) {
      throw new RuntimeException("Un-expected destination: " + dest);
    }
    boolean send = reduce.send(source, message, dest);
//  LOG.info(String.format("%d sending message on reduce: %d %d %b", executor, path, source, send));
    return send;
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags, int dest) {
    MPIDataFlowReduce reduce = reduceMap.get(dest);
    if (reduce == null) {
      throw new RuntimeException("Un-expected destination: " + dest);
    }
    boolean send = reduce.sendPartial(source, message, dest);
//  LOG.info(String.format("%d sending message on reduce: %d %d %b", executor, path, source, send));
    return send;
  }

  @Override
  public void progress() {
    for (MPIDataFlowReduce reduce : reduceMap.values()) {
      reduce.progress();
    }
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
    return null;
  }

  @Override
  public TaskPlan getTaskPlan() {
    return null;
  }

  @Override
  public void init(Config config, MessageType type, TaskPlan instancePlan, int edge) {
    executor = instancePlan.getThisExecutor();
    Map<Integer, List<Integer>> partialReceives = new HashMap<>();
    Map<Integer, List<Integer>> finalReceives = new HashMap<>();
    List<Integer> edgeList = new ArrayList<>(edges);
    Collections.sort(edgeList);
    int count = 0;
    for (int dest : destinations) {
      ReducePartialReceiver partialRcvr = new ReducePartialReceiver(dest);
      ReduceFinalReceiver finalRcvr = new ReduceFinalReceiver(dest);
      MPIDataFlowReduce reduce = new MPIDataFlowReduce(channel, sources, dest,
          finalRcvr, partialRcvr, count, dest);
      reduce.init(config, type, instancePlan, edgeList.get(count));
      reduceMap.put(dest, reduce);
      count++;

      for (Map.Entry<Integer, List<Integer>> e : reduce.receiveExpectedTaskIds().entrySet()) {
        partialReceives.put(e.getKey(), e.getValue());
        finalReceives.put(e.getKey(), e.getValue());
      }
    }

    finalReceiver.init(config, this, finalReceives);
    partialReceiver.init(config, this, partialReceives);
  }

  @Override
  public boolean sendPartial(int source, Object message, int flags) {
    // now what we need to do
    throw new RuntimeException("Not implemented");
  }

  private class ReducePartialReceiver implements MessageReceiver {
    private int destination;

    ReducePartialReceiver(int dst) {
      this.destination = dst;
    }

    @Override
    public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    }

    @Override
    public boolean onMessage(int source, int path, int target, int flags, Object object) {
//      LOG.info(String.format("%d received message %d %d %d", executor, path, target, source));
      return partialReceiver.onMessage(source, destination, target, flags, object);
    }

    public void progress() {
    }
  }

  private class ReduceFinalReceiver implements MessageReceiver {
    private int destination;

    ReduceFinalReceiver(int dest) {
      this.destination = dest;
    }

    @Override
    public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    }

    @Override
    public boolean onMessage(int source, int path, int target, int flags, Object object) {
//      LOG.info(String.format("%d received message %d %d %d", executor, path, target, source));
      return finalReceiver.onMessage(source, destination, target, flags, object);
    }

    @Override
    public void progress() {
    }
  }
}
