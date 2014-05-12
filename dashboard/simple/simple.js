require( ["sg/dashboard", "sg/sideAxis"], 
  function(dashboard, sideAxis) {

  var charts = [ { 
    title: "90th Percentile Request Time",
    groups: [ { 
      label: "seconds",
      axis: sideAxis(),
      named: [ { name: "epochs/p90" } ]
    } ]
  }];

  var simpleBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  simpleBoard(update);

});
