define(["d3"], function(_) {
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
