define (["lib/d3", "sg/util"], function(_d3, _util) {

/** Make an svg box resizable by dragging on a resize widget.  
  *
  * While resizing, a drag rectangle is drawn in separate svg object "resize-layer".  When
  * the resize is finished, a "resize" DOM event is dispatched.  The module that owns 
  * the svg box should redraw itself at the new size.
  */
function resizable() {

  var returnFn = function(container) {
    container.each(bind);
  };

  function bind() {
    var containerNode = this,
        selection = d3.select(this),
        sizeWidget = attachSizeWidget(selection, selection),  
        resizeLayer = attachByClass("svg", d3.select("body"), "resize-layer");

    resizeLayer.entered()
      .style("visibility", "hidden");

    sizeWidget.on("mousedown", function() { 
      startDrag(containerNode, this, resizeLayer); 
    });
  }

  /** Return an array containing the height and width of an svg element, calculated
   * by the svg bounding box. */
  function bboxSize(node) {
    var bbox = node.getBBox();
    return [bbox.width, bbox.height];
  }

  /** Return an array containing the height and width attributes of a single
   * element selection.  */
  function widthHeight(model) {
    if (model.datum() && model.datum().size) {
      return model.datum().size;
    } else {
      return [model.attr("width") * 1, 
              model.attr("height") * 1];
    }
  }

  /** place the selected widget to align with the lower right corner of an svg element */
  function placeLowerRight(widget, model) {   
    widget.each(function() {  // use .each so we do nothing on empty selection 
      var selection = d3.select(this),
          modelSize = widthHeight(model),
          modelNode = model.node(),   
          modelCTM = modelNode.getCTM(),   // getCTM returns null in firefox when called on an svg element
          modelSpot = modelCTM ? [modelCTM.e, modelCTM.f] : [0,0],
          widgetSize = bboxSize(this),
          spot = [modelSpot[0] + modelSize[0] - widgetSize[0], 
                  modelSpot[1] + modelSize[1] - widgetSize[1]]; 
  
      selection.attr("transform", translate(spot));
    });
  }

  /** place the size widget to overlap the lower right corner of an 'svg' or 'rect' node. */
  function attachSizeWidget(container, model) {
    var sizeWidget = attachByClass("rect", container, "resize-widget");

    sizeWidget.entered()
      .attr("width", 20)
      .attr("height", 20);

    placeLowerRight(sizeWidget, model);

    return sizeWidget;
  }

  function translate(spot) {
    return "translate(" + spot[0] + "," + spot[1] + ")"; 
  }

  /** Redraw the sizing box overlay to the current width
    *
    * this - resize widget */
  function resizeDrag(dragWidget, resizeBox, resizeLayer) {
    resizeBox
      .attr("width", d3.event.x)
      .attr("height", d3.event.y);

    var boxRect = resizeBox.node().getBoundingClientRect();

    resizeLayer
      .attr("width", boxRect.left + window.pageXOffset + d3.event.x + 4)  // +4 for box line width
      .attr("height", boxRect.top + window.pageYOffset + d3.event.y + 4); 

    dragWidget
      .call(placeLowerRight, resizeBox);
  }

  /** Draw a resize box in an overlay layer, and start the drag behavior
   * on the box in the overlay layer.
   *
   * modelNode - svg container in chart
   * widgetNode - widget node in chart
   * resizeLayer - overlay layer 
   */
  function startDrag(modelNode, widgetNode, resizeLayer) {
    d3.select(widgetNode).remove();   

    resizeLayer.style("visibility", "visible");
    var box = attachResizeBox(modelNode, resizeLayer),
        dragWidget = attachSizeWidget(resizeLayer, box);

    var drag = 
      d3.behavior.drag()
        .origin(function() {return dragOrigin(box);} )
        .on("dragend", function() { endDrag(modelNode, resizeLayer);} )
        .on("drag", function() { resizeDrag(dragWidget, box, resizeLayer); } );

    dragWidget
      .call(placeLowerRight, box)
      .call(drag);

    // make as if we started the drag on the new widget in the resizeLayer overlay
    synthesizeEvent("mousedown", dragWidget.node());   
  }

  /** hide the resize layer, then resize the model layer and put the resize box back in place in the model */
  function endDrag(modelNode, resizeLayer) {
    var resizeBox = resizeLayer.select(".resize-box"),
        newSize = [resizeBox.attr("width") * 1, resizeBox.attr("height") * 1],
        container = d3.select(modelNode);
    
    resizeBox.remove();
    resizeLayer.style("visibility", "hidden");

    container.attr("width", newSize[0]);
    container.attr("height", newSize[1]);

    var sizeWidget = attachSizeWidget(container, container);
    sizeWidget.on("mousedown", function() { startDrag(modelNode, this, resizeLayer); });

    var resizeEvent = new CustomEvent("resize", {bubbles:true, detail:{size:newSize}});
    container.node().dispatchEvent(resizeEvent);
  }

  /** place a box in the resize layer at the same screen position as the model node */
  function attachResizeBox(modelNode, resizeLayer) {
    var modelRect = modelNode.getBoundingClientRect(),
        box = attachByClass("rect", resizeLayer, "resize-box");

    box
      .attr("width", modelRect.width)
      .attr("height", modelRect.height);

    resizeLayer
      .attr("width", modelRect.right + window.pageXOffset + 4)  // +4 for box line width
      .attr("height", modelRect.bottom + window.pageYOffset + 4); 

    // set box transform
    var boxNode = box.node(),
        boxTransforms = boxNode.transform.baseVal,
        transform = boxNode.ownerSVGElement.createSVGTransform();
    transform.setTranslate(modelRect.left + window.pageXOffset, modelRect.top + window.pageYOffset); 
    boxTransforms.clear();
    boxTransforms.appendItem(transform);

    return box;
  }

  function synthesizeEvent(eventName, node) {
    var event = new Event(eventName);
    event.x = d3.event.x;
    event.y = d3.event.y;
    event.pageX = d3.event.pageX;
    event.pageY = d3.event.pageY;
    event.clientX = d3.event.clientX;
    event.clientY = d3.event.clientY;
    node.dispatchEvent(event);
  }

  function dragOrigin(resizeBox) {
    var result = {
      x: resizeBox.attr("width") * 1,
      y: resizeBox.attr("height") * 1
    };

    return result;
  }

  return returnFn;
}

return resizable;

});
