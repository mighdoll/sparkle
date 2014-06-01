require( ["lib/d3", "sg/dashboard", "sg/sideAxis", "sg/palette", "sg/linePlot" ], 
    function(_d3, dashboard, sideAxis, palette, linePlot) {

  var greyish = d3.scale.ordinal().range(["#BFB9A2"]), 
      thinLines = linePlot().strokeWidth(0.4).interpolate("step-before");

  var multiLine = { 
    title: "Database Server Response Time",
    transformName: "SummarizeMax",
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

  var mohsBoard = dashboard().size([700, 300]), 
      update = d3.selectAll("body").data([{charts:[multiLine]}]);

  mohsBoard(update);

});
