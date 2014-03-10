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

define(["jslib/d3", "sg/maxLock"], function(_, maxLock) {
/** Display a lockable maximum value indicator for a Y axis.
 *
 * When the lock/unlock button is clicked, a custom event 'toggleMaxLock' is fired.
 * 
 * Bind to a MaxLockData object
 */
return function() {
  var orient = "left",
      indicatorThickness = 2,
      width = 50,
      outDentY = 20,
      height = 30;

  function returnFn(selection) {
    selection.each(bind);
  }

  function bind(maxLockData) {
    var node = this,
        g = d3.select(node),
        background = g.selectAll(".background").data([0]),
        indicator = g.selectAll(".indicator").data([0]),
        maxValueData = maxLockData.locked ? [0] : [], // don't show max value in dynamic mode
        value = g.selectAll(".value").data(maxValueData),
        lockData = maxLockData.locked ? [0] : [],
        lock = g.selectAll(".lock").data(lockData),
        indicatorY,
        backgroundHeight;
    
    if (maxLockData.locked) {
      indicatorY = height - indicatorThickness; 
      backgroundHeight = height;
    } else {
      indicatorY = outDentY - indicatorThickness;
      backgroundHeight = outDentY;
    }

    var lockSize = [20, 25],
        lockBaseSize = [lockSize[0], lockSize[1] * 0.5],
        haspSize = [lockBaseSize[0] * 0.6, lockBaseSize[1] * 0.75],
        haspThick = 2,
        lockMargin = [5, 15],
        textMarginX = 5,
        haspPosition = [(lockBaseSize[0] - haspSize[0]) /2 , -haspSize[1] ],  // relative to lock base
        lockPosition,
        backgroundX,
        textRight;

    if (orient == "left") {
      lockPosition = [-width + lockMargin[0], lockMargin[1]];  
      backgroundX = -width;
      textRight = -textMarginX;
    } else if (orient == "right") {
      lockPosition = [width - lockMargin[0] - lockSize[0], lockMargin[1]];  
      backgroundX = 0;
      textRight = width - textMarginX;
    }

    indicator.enter().append("rect")
      .attr("x", backgroundX)
      .attr("width", height)
      .attr("height", 2)
      .attr("class", "indicator");

    value.enter().append("text")
      .attr("class", "value") 
      .attr("text-anchor", "end")
      .attr("dy", height / 3)
      .attr("dx", textRight)
      .attr("opacity", 0);

    var lockGroupEnter = lock.enter().append("g")
      .attr("class", "lock")
      .attr("opacity", 0);
    
    lockGroupEnter.append("rect")
      .attr("class", "base")
      .attr("x", lockPosition[0])
      .attr("y", lockPosition[1] + haspSize[1])
      .attr("width", lockBaseSize[0])
      .attr("height", lockBaseSize[1])
      .attr("rx", 2)
      .attr("ry", 2);

    lockGroupEnter.append("rect")
      .attr("class", "hasp")
      .attr("x", lockPosition[0] + haspPosition[0])
      .attr("y", lockPosition[1] + haspSize[1] + haspPosition[1])
      .attr("width", haspSize[0])
      .attr("height", haspSize[1] * 2)    // we show double the size 
      .attr("rx", 4)
      .attr("ry", 4)
      .style("fill", "none")
      .style("stroke-width", haspThick);

    d3.transition(lock).transition().duration(400).ease("cubic-in")
        .style("opacity", 100);

    d3.transition(value.exit())
      .style("opacity", 0)
      .remove();

    d3.transition(lock.exit())
      .style("opacity", 0)
      .remove();

    background.enter().append("rect")
      .attr("x", backgroundX)
      .attr("y", 0)
      .attr("width", width)
      .attr("height", height)
      .attr("class", "background")
      .on("click", toggleMaxLock);

    function toggleMaxLock() {
      maxLockData.locked = !maxLockData.locked;
      var eventInfo = {detail: maxLockData, bubbles:true};
      node.dispatchEvent(new CustomEvent("toggleMaxLock", eventInfo));
    }
    
      // update location of max line
    d3.transition(indicator)
      .attr("y", indicatorY);

    d3.transition(background)
      .attr("height", backgroundHeight);

      // update max value text
    value 
      .text(maxLockData.maxValue || maxLockData.lockRange[1]);

    d3.transition(value)
      .attr("opacity", 100);
  }

  returnFn.height = function(value) {
    if (!arguments.length) return height;
    height = value;
    return returnFn;
  };

  returnFn.width = function(value) {
    if (!arguments.length) return width;
    width = value;
    return returnFn;
  };

  returnFn.orient = function(value) {
    if (!arguments.length) return orient;
    orient = value;
    return returnFn;
  };

  returnFn.indicatorThickness = function(value) {
    if (!arguments.length) return indicatorThickness;
    indicatorThicknes = value;
    return returnFn;
  };

  return returnFn;
};

});
