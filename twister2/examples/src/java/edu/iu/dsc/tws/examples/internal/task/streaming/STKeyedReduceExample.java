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
package edu.iu.dsc.tws.examples.internal.task.streaming;

import java.util.List;
import java.util.logging.Logger;

import edu.iu.dsc.tws.api.task.TaskGraphBuilder;
import edu.iu.dsc.tws.comms.api.Op;
import edu.iu.dsc.tws.data.api.DataType;
import edu.iu.dsc.tws.examples.internal.task.BenchTaskWorker;
import edu.iu.dsc.tws.examples.internal.task.TaskExamples;
import edu.iu.dsc.tws.task.api.IFunction;
import edu.iu.dsc.tws.task.streaming.BaseStreamSink;
import edu.iu.dsc.tws.task.streaming.BaseStreamSource;

public class STKeyedReduceExample extends BenchTaskWorker {
  private static final Logger LOG = Logger.getLogger(STKeyedReduceExample.class.getName());

  @Override
  public TaskGraphBuilder buildTaskGraph() {
    List<Integer> taskStages = jobParameters.getTaskStages();
    int psource = taskStages.get(0);
    int psink = taskStages.get(1);
    Op operation = Op.SUM;
    DataType keyType = DataType.OBJECT;
    DataType dataType = DataType.INTEGER;
    String edge = "edge";
    TaskExamples taskExamples = new TaskExamples();
    BaseStreamSource g = taskExamples.getStreamSourceClass("keyed-reduce", edge);
    BaseStreamSink r = taskExamples.getStreamSinkClass("keyed-reduce");
    taskGraphBuilder.addSource(SOURCE, g, psource);
    computeConnection = taskGraphBuilder.addSink(SINK, r, psink);
    computeConnection.keyedReduce(SOURCE, edge, new IFunction() {
      @Override
      public Object onMessage(Object object1, Object object2) {
        return object1;
      }
    }, keyType, dataType);
    return taskGraphBuilder;
  }
}
