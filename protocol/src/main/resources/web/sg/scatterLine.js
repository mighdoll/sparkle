define(["d3", "sg/symbolMark", "sg/util"], function(_d3, symbolMark, _util) {

/** Plot of scatter of marks on a horizontal line 
 * Bind to an array containing a single Categorized object */
return function() {
  var _plot = {plotter:symbolMark()},
      _layoutHeight;

  var returnFn = function(container) {
    container.each(bind); 
  };

  function bind(categorized) {
    var selection = d3.select(this).selectAll(".mark"),
        update = selection.data(categorized.data),
        enter = update.enter(),
        exit = update.exit(),
        subPlot = categorized.plot || _plot;  

    var thisScale = categorized.series.xScale,
        oldScale = this.__categorized ? this.__categorized.oldScale : thisScale;
    this.__categorized = {oldScale: thisScale};

    var args = shallowCopy({}, categorized);
    args.plot = subPlot.plot;
    subPlot.plotter(enter, args);

    update
      .call(translateX, function(d) { return oldScale(d); });

    var transition = d3.transition(update);

    transition
      .call(translateX, function(d) { return thisScale(d); });

    exit
      .remove();
  }

  returnFn.plot = function(value) {
    if (!arguments.length) return _plot;
    _plot = value;
    return returnFn;
  };

  returnFn.plotter = function(value) {
    if (!arguments.length) return _plot && _plot.plotter;
    _plot = {plotter: value};
    return returnFn;
  };

  returnFn.layoutHeight = function(value) {
    if (!arguments.length) {
      return _layoutHeight || _plot.plotter.layoutHeight();
    }
    _layoutHeight = value;
    return returnFn;
  };

  return returnFn;
};

});
