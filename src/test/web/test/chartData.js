/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

define(["sg/sideAxis"], function(sideAxis) {

  /** return a smaple ChartData descriptor object for this raw data array. */
  return function(dataArray) {
    var data = { 
      title: "sample data",
      groups: [
        { 
          axis: sideAxis(),
          series: [
            { set: "sample",
              name: "first",
              domain: findDomain(dataArray),
              range: findRange(dataArray)
            }            
          ],
          label: "label-first-set"
        }
      ]
    };
    return data;
  };

  function findDomain(dataArray) {
    if (dataArray.length < 1) 
      return [new Date(0), new Date(0)];

    return minMax(dataArray, 0);
  }

  function findRange(dataArray) {
    if (dataArray.length < 1) 
      return [0, 0];

    return minMax(dataArray, 1);
  }

  /** return the min an max values of an array column  */
  function minMax(dataArray, column) {
    var min = dataArray.reduce(function(a,b) {
      return (a < b[column]) ? a : b[column];
    }, dataArray[column]);

    var max = dataArray.reduce(function(a,b) {
      return (a > b[column]) ? a : b[column];
    }, dataArray[column]);

    return [min, max];
  }
});
