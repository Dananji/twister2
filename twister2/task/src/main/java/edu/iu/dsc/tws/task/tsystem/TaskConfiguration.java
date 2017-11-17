
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
package edu.iu.dsc.tws.task.tsystem;

public class TaskConfiguration {

    private String taskConfigurationFile;
    private double taskRequiredRam;
    private double taskRequiredMemory;
    private double taskRequiredCPU;

    public double getTaskRequiredRam() {
        return taskRequiredRam;
    }

    public void setTaskRequiredRam(double taskRequiredRam) {
        this.taskRequiredRam = taskRequiredRam;
    }

    public double getTaskRequiredMemory() {
        return taskRequiredMemory;
    }

    public void setTaskRequiredMemory(double taskRequiredMemory) {
        this.taskRequiredMemory = taskRequiredMemory;
    }

    public double getTaskRequiredCPU() {
        return taskRequiredCPU;
    }

    public void setTaskRequiredCPU(double taskRequiredCPU) {
        this.taskRequiredCPU = taskRequiredCPU;
    }

    public void setConfiguration(String taskConfigurationFile) {
        this.taskConfigurationFile = taskConfigurationFile;
    }

    public String getTaskConfigurationFile(){
        return taskConfigurationFile;
    }
}
