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

define(["jslib/d3", "sg/domCache"], function(_d3, domCache) {

/** Add an svg clipping rectangle to a selection.   If called within a transition, animate the 
  * clip rectangle to the it's new size. */
return function(selection, id, size, spot, domain, xScale) {
  var transition = d3.transition(selection),
      svg        = svgElement(selection),
      defs       = attachByElement(svg, "defs"),
      clipPath   = attachById(defs, "clipPath", id),
      oldXScale  = domCache.save(selection.node(), "xScale", xScale),
      startRange = [oldXScale(domain[0]), oldXScale(domain[1])];

  var clipRect = attachByElement(clipPath, "rect");
  selection.attr("clip-path", "url(#" + id + ")");

  // start at the current size (=size of the brush highlight) 
  clipRect
    .attr("x", startRange[0])
    .attr("width", startRange[1] - startRange[0]);

  // animate clip rect back to full size
  // (note this will zoom the clip slightly ahead of the brush because we're animating
  //  to the paddedPlotSize while the brush animates to plotSize)
  transition.each(function() {  // get duration,ease from enclosing transition
    d3.transition(clipRect)
      .attr("x", spot[0])
      .attr("y", spot[1])
      .attr("width", size[0])
      .attr("height", size[1]);
  });

};

/** Return the containing svg of the given selection, or the selection itself
  * if the selection is an svg node */
function svgElement(selection) {
  var node = selection.node();
  if (node.tagName == "svg" || node.tagName == "SVG") {
    return selection;
  } else {
    return d3.select(node.ownerSVGElement);
  }
}

});


