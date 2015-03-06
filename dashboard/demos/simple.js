require( ["sg/dashboard", "sg/sideAxis"], 
  function(dashboard, sideAxis) {

  var basicChart = {
    title: "90th Percentile Request Time",
    transformName: "ReduceMax",
    groups: [ { 
      label: "seconds",
      axis: sideAxis(),
      named: [ { name: "epochs/p90" } ]
    } ]
  };

  var simpleBoard = dashboard().size([700, 300]), 
      update = d3.selectAll("body").data([{charts:[basicChart]}]);

  simpleBoard(update);

});
