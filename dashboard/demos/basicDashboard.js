require( ["lib/d3", "sg/dashboard", "sg/sideAxis", "sg/palette", "sg/linePlot" ], 
    function(_d3, dashboard, sideAxis, palette, linePlot) {

  var greyish = d3.scale.ordinal().range(["#BFB9A2"]), 
      thinLines = linePlot().strokeWidth(0.4).interpolate("step-before");

  var basicChart = { 
    title: "90th Percentile Request Time",
    transformName: "ReduceMax",
    groups: [ { 
      label: "seconds",
      axis: sideAxis(),
      named: [ { name: "epochs/p90" } ]
    } ]
  };

  var multiLine = { 
    title: "Database Server Response Time",
    transformName: "ReduceMax",
    groups: [
      { label: "seconds",
        axis: sideAxis(),
        named: [ 
          { name: "epochs/p90" },
          { name: "epochs/p99" }
        ]
      }, 
      { label: "request count",
        axis: sideAxis().orient("right"),
        colors: greyish,
        named: [
          { name: "epochs/count",
            plot: { plotter: thinLines } 
          }
        ]
      }
    ]
  };

  var mohsBoard = dashboard().size([700, 220]).zoomTogether(true), 
      update = d3.selectAll("body").data([{charts:[basicChart, multiLine]}]);

  mohsBoard(update);

});
