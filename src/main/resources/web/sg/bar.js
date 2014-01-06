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

define(["jslib/d3", "sg/domCache"], 
  function(_, domCache) {

return function() {
  var _color = "black",
      _width = 1000,
      _padding = 2;

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Return the width in pixels for one bar, based on the width in the data domain.  
   * (this assumes the scale is linear). */
  function barWidth(domainWidth, scale) {
    var a = scale(0),
        b = scale(domainWidth),
        rawWidth = Math.abs(a - b);
    return (rawWidth < 1) ? 1 : rawWidth;
  }

  /** Draw a bar chart data plot.
   * bind to a DataSeries. */
  function attach(dataSeries) {
    var selection = d3.select(this),
        update = selection.selectAll(".bar").data(dataSeries.data),
        transition = d3.transition(update),
        enter = update.enter(),
        exit = update.exit(),
        domainWidth = (dataSeries.plot && dataSeries.plot.width) || _width,
        color = (dataSeries.plot && dataSeries.plot.color) || dataSeries.color || _color,
        padding = (dataSeries.plot && dataSeries.plot.padding) || _padding,
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale,
        oldScales = domCache.save(this, "scales", [xScale, yScale]),
        oldXScale = oldScales[0],
        oldYScale = oldScales[1],
        plotHeight = yScale.range()[0];
        
    var width = barWidth(domainWidth, xScale);

    enter
      .append("rect")
      .classed("bar", true)
      .attr("fill", color)
      .attr("height", plotHeight); // always a tall bar, clipped at the bottom

    update  
      .attr("x", function(d) { return oldXScale(d[0]);} )
      .attr("y", function(d) { return oldYScale(d[1]);} );

    transition
      .attr("x", function(d) { return xScale(d[0]);} )
      .attr("y", function(d) { return yScale(d[1]);} )
      .attr("width", width);

    exit
      .remove();
  }

  returnFn.padding = function(value) {
    if (!arguments.length) return _padding;
    _padding = value;
    return returnFn;
  };

  returnFn.width = function(value) {
    if (!arguments.length) return _width;
    _width = value;
    return returnFn;
  };

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  return returnFn;
};


});
