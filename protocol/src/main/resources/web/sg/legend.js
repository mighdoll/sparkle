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
/** Display a visual 'key' - named color swatches.
 *
 * Bind to LegendData 
 */
return function() {
  var _orient = "left";     

  function returnFn(container) {
    container.each(bind);
  }

  function bind(legendData) {
    var selection = d3.select(this), 
        update = selection.selectAll(".entry").data(legendData),
        enter = update.enter(),
        exit = update.exit(),
        orient = legendData.orient || _orient;

    // positioning parameters. default for left side configuration
    var anchor = "beginning",
        textX = 23,
        boxX = 0;
    if (orient === "right") {
      anchor = "end";
      boxX = -10;
      textX = boxX - 5;
    }

    var gEntry = enter
      .append("g")
        .classed("entry " + orient, true)
        .attr("transform", function(d, i) { 
              return "translate(0," + (i * 20) + ")"; 
            });

    gEntry
      .append("rect")
        .attr("width", 18)
        .attr("height", 18)
        .attr("x", boxX)
        .classed("box", true);

    gEntry
      .append("text")
        .attr("x", textX)
        .attr("y", 9)
        .attr("dy", ".35em")
        .style("text-anchor", anchor);

    update.select(".box").style("fill", function(d) { return d.color; });
    update.select("text").text(function (d) { return d.label; }); 

    exit.remove();
  }

  // accessor functions

  returnFn.orient = function(value) {
    if (!arguments.length) return orient;
    orient = value;
    return returnFn;
  };

  return returnFn;
};

});
