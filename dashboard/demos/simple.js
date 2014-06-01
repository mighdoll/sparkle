require( ["sg/dashboard", "sg/sideAxis"], 
  function(dashboard, sideAxis) {

  var basicChart = {
    title: "90th Percentile Request Time",
    transformName: "SummarizeMax",
    groups: [ { 
      label: "seconds",
      axis: sideAxis(),
      named: [ { name: "epochs/p90" } ]
    } ]
  };

  var simpleBoard = dashboard().size([800, 300]), 
      update = d3.selectAll("body").data([{charts:[basicChart]}]);

  simpleBoard(update);

});
