require(["d3", "sg/serverDescribedGraph"], function (_, serverDescribedChart) {

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
