define(["lib/d3"], function(_) {

/** Return a function which plots a single d3Symbol.  */
return function() {
  var _layoutHeight,
      _symbol = d3.svg.symbol().size(36);


/** Parameters to the circle plotting function:
  *   enter:Selection --  d3 enter() selection 
  *   symbolMark:SymbolMark -- a Categorized with optional mark drawing control */
  var returnFn = function(enter, symbolMark) {
    var plot = symbolMark.plot || {},
        symbol = plot.symbol || _symbol,
        color = symbolMark.color;

    enter
      .append("path")
      .classed("mark", true)
      .style("stroke", color)
      .style("fill", color)
      .attr("d", symbol);
  };

  d3.rebind(returnFn, _symbol, "size", "type");

  returnFn.layoutHeight = function(value) {
    if (!arguments.length) return _layoutHeight || Math.sqrt(_symbol.size()()); // ()() because size returns accessorFn
    _layoutHeight = value;
    return returnFn;
  };

  returnFn.symbol = function(value) {
    if (!arguments.length) return _symbol;
    _symbol = value;
    return returnFn;
  };

  return returnFn;
};

});

