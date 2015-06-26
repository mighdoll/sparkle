define(["d3", "sg/domCache"], 
  function(_, domCache) {

return function() {
  var _color = "green",
      _barWidth = 50,
      _barMargin = 5;

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Return the width in pixels for one bar, based on the width in the data domain.  
   * (this assumes the scale is linear). */
  function calcBarWidth(plotWidth, count, allowedWidth, margin) {
    var maxNoMargin = Math.ceil(plotWidth / count), 
        maxWithMargin = Math.ceil((plotWidth - (count*margin)) / count),
        barWidth = maxWithMargin;

    if (allowedWidth <= maxWithMargin) {
      barWidth = allowedWidth;
    } else if (maxNoMargin < 1 || maxWithMargin < 1) {
      barWidth = 1;
    } 

    return barWidth;
  }

  /** Draw a bar chart data plot.
   * bind to a DataSeries. */
  function attach(dataSeries) {
    var selection = d3.select(this),
        otherPlotterJunk = selection.selectAll('*').filter(function() {
          return !d3.select(this).classed('bar');
        }),
        update = selection.selectAll(".bar").data(dataSeries.data),
        enter = update.enter(),
        exit = update.exit(),
        color = (dataSeries.plot && dataSeries.plot.color) || _color,
        barMargin = (dataSeries.plot && dataSeries.plot.barMargin) || _barMargin,
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale,
        oldScales = domCache.save(this, "bar-scales", [xScale.copy(), yScale.copy()]),
        oldXScale = oldScales[0],
        oldYScale = oldScales[1],
        plotWidth = xScale.range()[1] - xScale.range()[0],
        plotHeight = yScale.range()[0] - yScale.range()[1],
        maxBarWidth = (dataSeries.plot && dataSeries.plot.maxBarWidth) || _barWidth,
        barWidth = calcBarWidth(plotWidth, dataSeries.data.length, maxBarWidth, barMargin);

    otherPlotterJunk.remove();

    function barHeight(value, scale) {
      var height = plotHeight - scale(value);
      if (value > 0 && height <= 0) {
        height = 1;
      }
      if (height < 0) {
        height = 0;
      }
      return height
    }

    function barY(value, scale) {
      var y = scale(value);
      if (y == plotHeight && value > 0) {
        y = plotHeight - 1;
      }
      return y;
    }

    enter
      .append("rect")
      .classed("bar", true)
      .attr("fill", color);

    // d3 note that enter changes the update selection but not transition selection, so we setup 
    // transition here.
    var transition = d3.transition(update); 

    // ensure that there really is a transition, so that we can safely use attrTween
    if (!transition.ease) {
      transition = transition.transition().duration(0);
    }

    // d3: for small values > 0, need we clamp to 1-e6? 
    transition
      .attr("fill", color)
      .attrTween("x", function(d) { 
        var xStart = barWidth / 2;
        var start = oldXScale(d[0]) - xStart;
        var end = xScale(d[0]) - xStart; 
        return d3.interpolateNumber(start, end);
      })
      .attrTween("y", function(d) { 
        var start = barY(d[1], oldYScale);
        var end = barY(d[1], yScale); 
        return d3.interpolateNumber(start, end);
       })
      .attrTween("height", function(d) {
        var start = barHeight(d[1], oldYScale);
        var end = barHeight(d[1], yScale);
        return d3.interpolateNumber(start, end);
      })
      .attr("width", barWidth);

    exit
      .remove();
  }

  returnFn.barMargin = function(value) {
    if (!arguments.length) return _barMargin;
    _barMargin = value;
    return returnFn;
  };

  returnFn.barWidth = function(value) {
    if (!arguments.length) return _barWidth;
    _barWidth = value;
    return returnFn;
  };

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  returnFn.plotterName = "bar";

  return returnFn;
};


});
