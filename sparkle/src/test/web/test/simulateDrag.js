define([], 
function() {
  /** simulate the mousedown,mousemove,mouseup and click events of a drag operation.
   * selection: [Selection] - single node d3 selection 
   * ?start: [Number,Number] - start drag from this x,y (relative to the selection upper left)
   *                           (if start is not specified, start is the center of the node)
   * move: [Number,Number]  - total x,y offset to drag
   * ?steps: Number          - number of intermediate steps (defaults to 3)
   */ 
  return function(selection, start, move, steps) {  
    var jqNode = jQuery(selection.node());
    jqNode.simulate("drag", {start:start, move:move, steps:steps});
  }
});


