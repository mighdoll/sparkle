require( ["sg/dashboard", "sg/sideAxis", "sg/scatter"], 
  function(dashboard, sideAxis, scatter) {

  var charts = [ {
    title: "90th Percentile Request Time",
    groups: [ { 
      label: "seconds",
      plot: { plotter: scatter() },
      axis: sideAxis(),
      named: [ { name: "epochs/p90" } ]
    } ]
  } ];

  var simpleBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  simpleBoard(update);

});
