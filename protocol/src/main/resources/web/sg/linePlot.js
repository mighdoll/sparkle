define(["lib/d3"], 
  function(_) {

/** Plot a single line into a container.  
  * Bind to a DataSeries object */
function linePlot() {
  var _strokeWidth = 1.5,
      _color = "black",
      removeOtherPlots = true,
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
        color = (dataSeries.plot && dataSeries.plot.color) || _color,
        transition = d3.transition(selected),
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale;

    // save old scales to use for transitions
    var currentScales = { xScale:xScale.copy(), yScale:yScale.copy() }, 
        oldScales = this.__linePlotScales || currentScales;
    this.__linePlotScales = currentScales;

    if (removeOtherPlots) {
      var otherPlotterJunk = selection.selectAll('*').filter(function() {
          return !d3.select(this).classed('line');
      });
      otherPlotterJunk.remove();
    } 

    selected
      .attr("stroke", color)
      .datum(dataSeries.data);
      
    transition
      .attr("stroke-width", strokeWidth);

    var line =
      d3.svg.line()
        .interpolate(interpolate);

    /** return functions that are called at each scale and range functions that */
    var tweener = function(scaleName, newDomain) {
      var oldScale = oldScales[scaleName],
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

    if (transition.ease) { // if we're in a transition
      var xTween = tweener("xScale", dataSeries.displayDomain),
          yTween = tweener("yScale", dataSeries.displayRange);
        
      line
        .x( function(d) { return xTween.scale(d[0]); })
        .y( function(d) { return yTween.scale(d[1]); });

      transition
        .attrTween("d", scaledLine);

    } else {
      line
        .x( function(d) { return xScale(d[0]); })
        .y( function(d) { return yScale(d[1]); });

      transition
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

  returnFn.removeOtherPlots = function(value) {
    if (!arguments.length) return removeOtherPlots;
    removeOtherPlots = value;
    return returnFn;
  };

  returnFn.plotterName = 'line';

  return returnFn;
}

return linePlot;
});
