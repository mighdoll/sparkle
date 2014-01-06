define(["test-jslib/jquery.simulate"], 
function(_simulate) {
  /** generate DOM events as if the mouse was clicked 
   *
   * .selection:D3Selection     - mouse events will target the first element in the selection
   * .?position:[Number,Number] - location of the click: (pixel offset from selection's upper left)
   */ 
  return function(selection, position) {  
    var node = selection.node(),
        jqSelection = jQuery(node),
        offset = jqSelection.offset();
    if (!position) position = [1, 1];
    var docPosition = [
      offset.left + position[0],
      offset.top + position[1]
    ];
    jqSelection.simulate("mousedown", {clientX: docPosition[0], clientY: docPosition[1]});
    jqSelection.simulate("mouseup", {clientX: docPosition[0], clientY: docPosition[1]});
    jqSelection.simulate("click", {clientX: docPosition[0], clientY: docPosition[1]});
  }
});
