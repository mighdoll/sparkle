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

require(["jslib/d3", "sg/serverDescribedGraph"], function (_, serverDescribedChart) {

/** Create a simple xy chart.  The server specifies the chart title and which data series to plot.  
 * This is intended to be a basic default plot of time series data, e.g. when launching the sg tool
 * from the command line.  
 */
var chartSize = [800, 350];
var svg = d3.select("body").append("svg")
    .attr("width", chartSize[0])
    .attr("height", chartSize[1])
    .classed("chart", true);

var serverChart = 
  serverDescribedChart()
    .size(chartSize);

svg
  .data(["default"])
  .call(serverChart);

});
