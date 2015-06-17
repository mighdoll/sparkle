define(["lib/d3", "sg/symbolMark", "sg/util"], function(_d3, symbolMark, _util) {

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
        fillColor = (dataSeries.plot && dataSeries.plot.fillColor) || _fillColor,
        selection = rootSelect.selectAll(".mark." + markType),
        otherPlotterJunk = rootSelect.selectAll('*').filter(function() {
          return !d3.select(this).classed('mark ' + markType);
        }),
        update = selection.data(dataSeries.data),
        enter = update.enter(),
        exit = update.exit();

    otherPlotterJunk.remove();

    // save old dataSeries in to use for transitions
    var currentScales = { xScale:dataSeries.xScale.copy(), yScale:dataSeries.yScale.copy() }, 
        oldScales = this.__scales || currentScales;
    this.__scales = currentScales;

    markPlot.color(color);

    enter
      .call(markPlot);

    update
      .call(translateXY, function(d) { 
        return [ oldScales.xScale(d[0]), oldScales.yScale(d[1]) ]; 
      });

    var transition = d3.transition(update);

    transition
      .call(translateXY, function(d) { 
        return [ currentScales.xScale(d[0]), currentScales.yScale(d[1]) ]; 
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

