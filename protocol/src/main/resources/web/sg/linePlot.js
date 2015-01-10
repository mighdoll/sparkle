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

define(["lib/d3"], 
  function(_) {

/** Plot a single line into a container.  
  * Bind to a DataSeries object */
function linePlot() {
  var _strokeWidth = 1.5,
      _color = "black",
      _interpolate = "linear";

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Draw a line for a single data series.  
    *
    * If called within a transition, stretch the new line data from
    * the old range to the new range by repeatedly redrawing the line.
    * (we're in a transition if d3.transition called wihin transition.each
    *  returns a transition rather than a selection object.)
    */
  function attach(dataSeries) {
    var selection = d3.select(this),
        // (we don't do the d3 traditional selectAll here -- svg paths are one
        // node for the whole line, not one node per data point)
        selected = attachByClass("path", selection, "line"),
        entered = selected.entered(),
        strokeWidth = (dataSeries.plot && dataSeries.plot.strokeWidth) || _strokeWidth,
        interpolate = (dataSeries.plot && dataSeries.plot.interpolate) || _interpolate,
        color = dataSeries.color || (dataSeries.plot && dataSeries.plot.color) || _color,
        update = d3.transition(selected),
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale;

    // save old dataSeries in to use for transitions
    var thisSeries = { xScale:xScale.copy(), yScale:yScale.copy() }, 
        oldSeries = this.__series || thisSeries;
    this.__series = thisSeries;
    
    entered
      .attr("stroke", color)
      .attr("stroke-width", strokeWidth);

    selected
      .datum(dataSeries.data);
      
    var line = 
      d3.svg.line()
        .interpolate(interpolate);

    /** return functions that are called at each scale and range functions that */
    var tweener = function(scaleName, newDomain) {
      var oldScale = oldSeries[scaleName],
          newScale = dataSeries[scaleName],
          newRange = dataSeries[scaleName].range(),
          startRange = [oldScale(newDomain[0]), oldScale(newDomain[1])],
          tweenScale = newScale.copy().domain(newDomain),
          rangeInterpolate = d3.interpolate(startRange, newRange);

      return {
        scale: tweenScale,
        rangeInterpolate: rangeInterpolate
      };
    }

    if (update.ease) { // if we're in a transition 
      var xTween = tweener("xScale", dataSeries.displayDomain),
          yTween = tweener("yScale", dataSeries.displayRange);
        
      line
        .x( function(d) { return xTween.scale(d[0]); })
        .y( function(d) { return yTween.scale(d[1]); });

      update 
        .attrTween("d", scaledLine);

    } else {
      line
        .x( function(d) { return xScale(d[0]); })
        .y( function(d) { return yScale(d[1]); });

      update
        .attr("d", line);
    }

    function scaledLine() {
      return function(tween) {
        // adjust the x,y scales at each tween in the animation
        // the scales change their range at each time step, such that the
        // fully drawn line fits in the expanding/contracting highlight area
        xTween.scale.rangeRound(xTween.rangeInterpolate(tween));
        yTween.scale.rangeRound(yTween.rangeInterpolate(tween));
        return line(dataSeries.data);
      };
    }
  }

  returnFn.interpolate = function(value) {
    if (!arguments.length) return _interpolate;
    _interpolate  = value;
    return returnFn;
  };

  returnFn.strokeWidth = function(value) {
    if (!arguments.length) return _strokeWidth;
    _strokeWidth = value;
    return returnFn;
  };

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  return returnFn;
}

return linePlot;
});
