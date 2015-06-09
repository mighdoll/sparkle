define(["lib/d3", "sg/linePlot"], 
  function(_, linePlot) {

/** Plot a into a container.  
  * Bind to a DataSeries object */
function areaPlot() {
  var _strokeWidth = 1.5,
      _color = "black",
      _fillColor = "grey",
      _interpolate = "linear";

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Draw an area for a single data series.  
    *
    * If called within a transition, stretch the new line data from
    * the old range to the new range by repeatedly redrawing the line.
    * (we're in a transition if d3.transition called wihin transition.each
    *  returns a transition rather than a selection object.)
    */
  function attach(dataSeries) {
    var selection = d3.select(this),
        otherPlotterJunk = selection.selectAll('*').filter(function() {
          return !d3.select(this).classed('area');
        }),
        // (we don't do the d3 traditional selectAll here -- svg paths are one
        // node for the whole line, not one node per data point)
        selected = attachByClass("path", selection, "area"),
        entered = selected.entered(),
        strokeWidth = (dataSeries.plot && dataSeries.plot.strokeWidth) || _strokeWidth,
        interpolate = (dataSeries.plot && dataSeries.plot.interpolate) || _interpolate,
        color = dataSeries.color || (dataSeries.plot && dataSeries.plot.color) || _color,
        fillColor = dataSeries.fillColor || (dataSeries.plot && dataSeries.plot.fillColor) || _fillColor,
        transition = d3.transition(selected),
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale;

    // save old scales for transitions
    var currentScales = { xScale:xScale.copy(), yScale:yScale.copy() }, 
        oldScales = this.__areaPlotScales || currentScales;
    this.__areaPlotScales = currentScales;

    otherPlotterJunk.remove();

    selected
      .attr("stroke", "none")
      .attr("fill", fillColor)
      .datum(dataSeries.data);
      
    transition
      .attr("stroke-width", strokeWidth);

    var area =
      d3.svg.area()
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

    var minY = yScale(dataSeries.range[0]);   

    if (transition.ease) { // if we're in a transition
      var xTween = tweener("xScale", dataSeries.displayDomain),
          yTween = tweener("yScale", dataSeries.displayRange);
        
      area 
        .x( function(d) { return xTween.scale(d[0]); })
        .y0( function(d) { return yTween.scale(d[1]); })
        .y1(minY);

      transition
        .attrTween("d", scaledArea);

    } else {
      area
        .x( function(d) { return xScale(d[0]); })
        .y0( function(d) { return yScale(d[1]); })
        .y1(minY);

      transition
        .attr("d", area);
    }

    function scaledArea() {
      return function(tween) {
        // adjust the x,y scales at each tween in the animation
        // the scales change their range at each time step, such that the
        // fully drawn line fits in the expanding/contracting highlight area
        xTween.scale.rangeRound(xTween.rangeInterpolate(tween));
        yTween.scale.rangeRound(yTween.rangeInterpolate(tween));
        return area(dataSeries.data);
      };
    }

    // attach a line plot to highlight the top edge of the area plot
    var line = linePlot()
      .removeOtherPlots(false)
      .color(color)
      .strokeWidth(strokeWidth);

    var selectionTransition = d3.transition(selection);  // inherit transition on the 'g'
    selectionTransition.call(line);                      // generate a line in the dom
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

  returnFn.fillColor = function(value) {
    if (!arguments.length) return _fillColor
    _fillColor = value;
    return returnFn;
  };

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  returnFn.plotterName = 'area';

  return returnFn;
}

return areaPlot;
});
