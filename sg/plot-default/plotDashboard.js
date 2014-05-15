require( ["lib/when/when", "lib/d3", "sg/dashboard", "sg/sideAxis", 
          "sg/palette", "sg/scatter", "sg/data", "sg/util" ], 
           function(when, _d3, dashboard, sideAxis, palette, scatter, data, util) {

  function fetchParameters() {
    var parameters = urlParameters(location.search);
    var sessionId = parameters.sessionId;
    if (!sessionId) console.error("sessionId not found");
    
    var paramsWhen = data("raw", 

    // data(); 
  }

  fetchParameters();

  var charts = [ 
    { title: "Interactive Plot",
      timeSeries: false,
      xScale: d3.scale.linear(),
      showXAxis: false,
      padding:[5, 5],    // padding so that marks can extend past the edge of the plot area
      groups: [
        { label: "labelLeft",
          plot: { plotter: scatter() },
          axis: sideAxis(),
          named: [ 
            { name: "plot/test1" }
          ]
        }, 
      ]
    }
  ];

  var mohsBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  mohsBoard(update);

});
