require( ["lib/d3", "sg/dashboard", "sg/sideAxis", "sg/palette", "sg/linePlot" ], 
    function(_d3, dashboard, sideAxis, palette, linePlot) {

  var greyish = d3.scale.ordinal().range(["#BFB9A2"]), 
      thinLines = linePlot().strokeWidth(0.4).interpolate("step-before");

  var charts = [ 
    { title: "Database Server Response Time",
      groups: [
        { label: "seconds",
          axis: sideAxis(),
          named: [ 
            { name: "src/test/resources/epochs.csv/p90" },
            { name: "src/test/resources/epochs.csv/p99" }
          ]
        }, 
        { label: "request count",
          axis: sideAxis().orient("right"),
          colors: greyish,
          named: [
            { name: "src/test/resources/epochs.csv/count",
              plot: { plotter: thinLines } 
            }
          ]
        }
      ]
    }
  ];

  var mohsBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  mohsBoard(update);

});
