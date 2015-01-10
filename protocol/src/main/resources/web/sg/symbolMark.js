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

  return returnFn;
};

});

