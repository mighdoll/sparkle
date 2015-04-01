define(["lib/d3"], function(_) {

/** Return a function which plots a single d3Symbol.  */
return function() {
  var _layoutHeight,
      _color = "blue",
      _markType = "circle",
      _symbol = d3.svg.symbol().size(36).type(_markType);


/** Parameters to the symbol plotting function:
  *   enter:Selection --  d3 enter() selection 
  */
  var returnFn = function(enter) {
    enter
      .append("path")
      .classed("mark", true)
      .style("stroke", _color)
      .style("fill", _color)
      .attr("d", _symbol);
  };

  d3.rebind(returnFn, _symbol, "size");

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

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  returnFn.markType = function(value) {
    if (!arguments.length) return _markType;
    _markType = value;
    _symbol.type(_markType);
    return returnFn;
  }

  return returnFn;
};

});

