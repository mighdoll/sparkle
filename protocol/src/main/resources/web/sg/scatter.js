define(["lib/d3", "sg/symbolMark", "sg/util", "sg/domCache"], 
       function(_d3, symbolMark, _util, domCache) {

/** Plot a scatterin of symbols.  
  * Bind to a DataSeries object */
function scatter () {
  var _markPlot = symbolMark(),
      _color = "black",
      _fillColor = "none";

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Draw a scatter plot for a single data series.  
    *
    * The symbolMark plotter is typically used to create each element.
    *
    * On transition, we translate the existing elements to their new location.
    */
  function attach(dataSeries) {
    var rootSelect = d3.select(this),
        markPlot = (dataSeries.plot && dataSeries.plot.markPlot) ?
                      dataSeries.plot.markPlot : _markPlot,
        markType = markPlot.markType(),
        color = (dataSeries.plot && dataSeries.plot.color) || _color,
        fillColor = (dataSeries.plot && dataSeries.plot.lightColor) || _fillColor,
        selection = rootSelect.selectAll(".mark." + markType),
        otherPlotterJunk = rootSelect.selectAll('*').filter(function() {
          return !d3.select(this).classed('mark ' + markType);
        }),
        update = selection.data(dataSeries.data),
        currentScales = {x:dataSeries.xScale, y:dataSeries.yScale},
        oldScales = domCache.save(this, "scatter-scales", 
              { x: currentScales.x.copy(), y: currentScales.y.copy() }),
        enter = update.enter(),
        exit = update.exit();

    otherPlotterJunk.remove();

    markPlot
      .color(color)
      .fillColor(fillColor);

    enter
      .call(markPlot);

    update
      .call(translateXY, function(d) { 
        return [ oldScales.x(d[0]), oldScales.y(d[1]) ]; 
      });

    var transition = d3.transition(update);

    transition
      .call(translateXY, function(d) { 
        return [ currentScales.x(d[0]), currentScales.y(d[1]) ]; 
      });

    exit
      .remove();
  }

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  returnFn.fillColor = function(value) {
    if (!arguments.length) return _fillColor;
    _fillColor = value;
    return returnFn;
  };

  returnFn.plot = function(value) {
    if (!arguments.length) return _plot;
    _plot = value;
    return returnFn;
  };

  returnFn.plotterName = 'scatter';

  return returnFn;
}

return scatter;
});

