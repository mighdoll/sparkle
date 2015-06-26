define (["d3", "sg/domCache"], function(_, domCache) {
/** Setup drag zooming.   
  *
  * Wraps a d3.brush with a few modifications:
  *   . limit selection to the x axis, 
  *   . add support for double clicking (to zoom out), 
  *   . report zoom events as DOM CustomEvents that bubble through the DOM 
  *     (the brush reports only d3 events, which don't bubble)
  *   . perform an animated zoom of the brush extent box to full width (if called on a transition)
  *
  * Caller must configure width, height, and xScale.
  * bind to a single DOM node.   
  */
return function() {
  var height, // LATER, set width, height, xScale to reasonable defaults..
      xScale,
      dblClickDuration = 500,
      brush = d3.svg.brush(); 
        // LATER one brush per instance, cached in the DOM  - to enable binding to a multiple 
        // element selection w/o sharing the same brush and its single 'extent'. 

  var returnFn = function(selection) {
    selection.each(bind);
  };

  function bind(data) {
    var node = this,
        range = xScale.range(),
        width = Math.abs(range[0] - range[1]),
        selection = d3.select(this),
        transition = d3.transition(selection),
        _lastClick = 0;   // for detecting double clicks

    function lastClick(value) {
      if (!arguments.length) return _lastClick;
      _lastClick = value;
    }

    brush.x(xScale);
    selection.call(brush);

    var brushBox = selection.selectAll("rect.extent"),
        origOpacity = domCache.saveIfEmpty(node, "origOpacity", function() { brushOpacity(brushBox); });

    console.assert(selection.ease === undefined);

    // brush height is always full height
    selection.selectAll("rect.background")
      .attr("height", height);  // no y scale, so brush component doesn't set this 

    brushBox
      .attr("height", height);

    var extent = brush.extent(),
        extentSize = Math.abs(extent[1] - extent[0]);
    if (transition.ease && extentSize > 0) { // zoom out if we're in a transition there's an existing extent 
      var transitionBox = transition.select("rect.extent");
      
      // zoom from current extent to full width
      // (less one pixel on each side so we don't overlap the axis and cause a flicker)
      transitionBox
        .attr("width", width - 2)   
        .attr("x", 1);

      // and then fade out brush 
      transitionBox 
        .transition()
          .ease("linear")
          .duration(250)
          .style("fill-opacity", 0)
          .each("end", function() { brushAnimationFinish(node, selection, brush, brushBox, origOpacity); });
    }

    brush.on("brushend", function() { brushEnd(node, lastClick); });
  }

  /** Brush gesture complete, report an appropriate "zoomBrush" DOM event.  
   *  Our caller should rebind us to transition to trigger the zoom out effect if */
  function brushEnd(node, lastClick) {
    if (brush.empty()) {
      var now = new Date(); 
      if (now - lastClick() < dblClickDuration) {
        lastClick(0);
        var eventInfo = {detail: {zoomReset: true}, bubbles:true};
        node.dispatchEvent(new CustomEvent("zoomBrush", eventInfo));
      } else {
        lastClick(now);
      }
    } else {
      var eventInfo = {detail: {extent: brush.extent()}, bubbles:true};
      node.dispatchEvent(new CustomEvent("zoomBrush", eventInfo));
    }
  }

  /** change brush display back to the unused state */
  function brushAnimationFinish(node, selection, brush, brushBox, opacity) {
    brushBox.style("fill-opacity", opacity);
    brush.clear();
    selection.call(brush);
    var eventInfo = {detail: {transitionEnd: true}, bubbles:true};
    node.dispatchEvent(new CustomEvent("zoomBrush", eventInfo));
  }

  /** return the opacity of the brush drag selection */
  function brushOpacity(brushBox) {
    console.assert(brushBox.ease === undefined);
    var opacity = parseFloat(brushBox.style("fill-opacity"));
    return opacity;
  }


  returnFn.height = function(value) {
    if (!arguments.length) return height;
    height = value;
    return returnFn;
  };
      
  returnFn.xScale = function(value) {
    if (!arguments.length) return xScale;
    xScale = value;
    return returnFn;
  };

  d3.rebind(returnFn, brush, "extent");

  return returnFn;
};

});
