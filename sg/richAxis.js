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

define(["jslib/d3", "sg/util", "sg/maxLock"], function(_d3, _util, maxLock) {

/** A display d3.axis with a few extensions:
  *  label - a code or css styled text label
  *  lockMax - display a button for 'locking' the maximum value of the axis 
  *    (unlock to let axis size grow dynamically)
  *  sizing - sizes axis range based on a length and orientation
  *
  *  bind to a richAxisData:
  *    {maxLockData: {locked:true, maxValue:"3.3"}}
  */
return function() {
  var axis = d3.svg.axis(),   // wrapped d3.axis
      displayLength = 200,
      labelColor = "black",
      lockMaxEnabled = true,  // lockMax feature enabled
      maxLockHeight = 40,
      maxLockOutdentY = 20,
      label = "";

  /** Creator function the displayScale component.  
    * Adds axis to the selected dom element in the 'this' parameter.  */
  function returnFn(selection) {
    selection.each(bind);
  }
    
  function bind(richAxisData) {
    var g = d3.select(this),
        range,
        dy,
        labelTransform = "rotate(-90)",
        anchorStyle = "end",
        orient = axis.orient(),
        displayLockControl = lockMaxEnabled && (orient == "left" || orient == "right");

    var maxLocked = displayLockControl && richAxisData.maxLockData && richAxisData.maxLockData.locked;

    if (orient == "left") {
      dy = "1.3em";
      range = verticalRange();
    } else if (orient == "right") {
      dy = "-.7em";
      range = verticalRange();
    } else if (orient == "bottom") { 
      labelTransform = "";
      range = [0, displayLength];
      dy = "4em";
      anchorStyle = "beginning";
    }

    function verticalRange() {
      if (maxLocked) { 
        return [displayLength, maxLockHeight - maxLockOutdentY];    
      } else {
        return [displayLength, 0];
      }
    }

    axis.scale().rangeRound(range);

    var labelUpdate = g.selectAll(".label").data([0]),
        labelEnter = labelUpdate.enter(),
        labelExit = labelUpdate.exit();

    labelEnter
      .append("text")
        .attr("class", "label") 
        .attr("transform", labelTransform) 
        .attr("dy", dy)
        .style("text-anchor", anchorStyle)
        .style("fill", labelColor);

    if (displayLockControl) {
      var attached = attachComponent(g, maxLock, "max-lock", [0, -maxLockOutdentY]);
      attached.component
        .height(maxLockHeight)
        .orient(orient);

      attached.bind(richAxisData.maxLockData);
    }
      
    labelUpdate
      .text(label);

    labelExit.remove();

    g
      .call(axis);
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

  returnFn.lockMaxEnabled = function(value) {
    if (!arguments.length) return lockMaxEnabled;
    lockMaxEnabled = value;
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
