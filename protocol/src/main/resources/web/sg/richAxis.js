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

define(["lib/d3", "sg/util"], function(_d3, _util) {

/** A display d3.axis with a few extensions:
  *  label - a code or css styled text label
  *  sizing - sizes axis range based on a length and orientation (TODO remove this and set scale externally)
  */
return function() {
  var axis = d3.svg.axis(),   // wrapped d3.axis
      displayLength = 200,
      labelColor = "black",
      label = "foo";

  /** Creator function the displayScale component.  
    * Adds axis to the selected dom element in the 'this' parameter.  */
  function returnFn(selection) {
    selection.each(bind);
  }
    
  function bind() {
    var g = d3.select(this),
        inheritedTransition = d3.transition(g),
        range,
        dy,
        labelTransform = "rotate(-90)",
        anchorStyle = "end",
        orient = axis.orient();

    if (orient == "left") {
      dy = "1.3em";
      range = [displayLength, 0];
      axis.scale().rangeRound(range);
    } else if (orient == "right") {
      dy = "-.7em";
      range = [displayLength, 0];
      axis.scale().rangeRound(range);
    } else if (orient == "bottom") {
      labelTransform = "";
      range = [0, displayLength];
      dy = "4em";
      anchorStyle = "beginning";
    }

    g
      .call(axis);

    var labelUpdate = g.selectAll(".label").data([0]),
        labelEnter = labelUpdate.enter(),
        labelExit = labelUpdate.exit();

    labelEnter
      .append("text")
        .attr("class", "label")
        .attr("transform", labelTransform)
        .attr("dy", dy)
        .attr("opacity", 0)
        .style("text-anchor", anchorStyle)
        .style("fill", labelColor);

    toTransition(labelUpdate, inheritedTransition)
      .text(label)
      .attr("opacity", 1);

    labelExit.remove();

  }

  republishAxisApi();

  function republishAxisApi() {
    d3.rebind(returnFn, axis, "orient");
    rebindAxisScale();
  }

  function rebindAxisScale() {
    d3.rebind(returnFn, axis.scale(), "domain", "range");
  }


  //
  //    accessors 
  //

  returnFn.scale = function(value) {
    if (!arguments.length) return axis.scale();
    axis.scale(value);
    rebindAxisScale();
    return returnFn;
  };

  returnFn.displayLength = function(value) {
    if (!arguments.length) return displayLength;
    displayLength = value;
    return returnFn;
  };

  returnFn.label = function(value) {
    if (!arguments.length) return label;
    label = value;
    return returnFn;
  };

  returnFn.labelColor = function(value) {
    if (!arguments.length) return labelColor;
    labelColor = value;
    return returnFn;
  };

  returnFn.axis = function(value) {
    if (!arguments.length) return axis;
    axis = value;
    return returnFn;
  };

  return returnFn;
}; 

});
