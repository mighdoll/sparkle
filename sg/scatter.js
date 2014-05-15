define(["lib/d3", "sg/symbolMark", "sg/util"], function(_d3, symbolMark, _util) {

/** Plot a scatterin of symbols.  
  * Bind to a DataSeries object */
function scatter () {
  var _plot = { plotter:symbolMark() },
      _color = "black";

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
    var selection = d3.select(this).selectAll(".mark"),
        update = selection.data(dataSeries.data),
        enter = update.enter(),
        exit = update.exit(),
        subPlot = dataSeries.plot || _plot;  

    // save old dataSeries in to use for transitions
    var currentScales = { xScale:dataSeries.xScale.copy(), yScale:dataSeries.yScale.copy() }, 
        oldScales = this.__scales || currentScales;
    this.__scales = currentScales;
    
    var args = shallowCopy({}, dataSeries);
    args.plot = subPlot.plot;
    subPlot.plotter(enter, args);

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

  returnFn.plot = function(value) {
    if (!arguments.length) return _plot;
    _plot = value;
    return returnFn;
  };


  return returnFn;
}

return scatter;
});

