require( ["sg/dashboard", "sg/sideAxis"], 
  function(dashboard, sideAxis) {

  var charts = [ { 
    title: "90th Percentile Request Time",
    groups: [ { 
      label: "seconds",
      axis: sideAxis(),
      named: [ { name: "epochs.csv/p90" } ]
    } ]
  }];

  var mohsBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  mohsBoard(update);

});
