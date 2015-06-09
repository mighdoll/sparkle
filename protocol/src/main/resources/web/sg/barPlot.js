define(["lib/d3", "sg/domCache"], 
  function(_, domCache) {

return function() {
  var _color = "black",
      _width = 1000,
      _padding = 5;

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Return the width in pixels for one bar, based on the width in the data domain.  
   * (this assumes the scale is linear). */
  function barWidth(domainWidth, scale) {
    var a = scale(0),
        b = scale(domainWidth),
        rawWidth = Math.abs(a - b);
    return (rawWidth < 1) ? 1 : rawWidth;
  }

  /** Draw a bar chart data plot.
   * bind to a DataSeries. */
  function attach(dataSeries) {
    var selection = d3.select(this),
        update = selection.selectAll(".bar").data(dataSeries.data),
        transition = d3.transition(update),
        enter = update.enter(),
        exit = update.exit(),
        domainWidth = (dataSeries.plot && dataSeries.plot.width) || _width,
        color = (dataSeries.plot && dataSeries.plot.color) || dataSeries.color || _color,
        padding = (dataSeries.plot && dataSeries.plot.padding) || _padding,
        xScale = dataSeries.xScale,
        yScale = dataSeries.yScale,
        oldScales = domCache.save(this, "scales", [xScale, yScale]),
        oldXScale = oldScales[0],
        oldYScale = oldScales[1],
        plotHeight = yScale.range()[0];
        
    var width = barWidth(domainWidth, xScale);

    enter
      .append("rect")
      .classed("bar", true)
      .attr("fill", color)
      .attr("height", plotHeight); // always a tall bar, clipped at the bottom

    update  
      .attr("x", function(d) { return oldXScale(d[0]);} )
      .attr("y", function(d) { return oldYScale(d[1]);} );

    transition
      .attr("x", function(d) { return xScale(d[0]);} )
      .attr("y", function(d) { return yScale(d[1]);} )
      .attr("width", width);

    exit
      .remove();
  }

  returnFn.padding = function(value) {
    if (!arguments.length) return _padding;
    _padding = value;
    return returnFn;
  };

  returnFn.width = function(value) {
    if (!arguments.length) return _width;
    _width = value;
    return returnFn;
  };

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  return returnFn;
};


});
