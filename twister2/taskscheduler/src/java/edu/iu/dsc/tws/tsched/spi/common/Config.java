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

package edu.iu.dsc.tws.tsched.spi.common;

public class Config {

  public static double Container_Max_RAM_Value = 10.00;
  public static double Container_Max_CPU_Value = 3.00;
  public static double Container_Max_Disk_Value = 10.00;
  public static int Task_Parallel = 2;

  //public static String Scheduling_Mode = "RoundRobin";
  public static String Scheduling_Mode = "FirstFit";

  public Double getDoubleValue(Key key) {
    Object value = get(key);
    Double dvalue = 0.0;
    if(value instanceof Double) {
      dvalue = (Double) value;
    }
    return dvalue;
  }

  public Object get(Key key) {
    return get(key.value());
  }

  private Object get(String value) {
    return value;
  }
}