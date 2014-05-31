require( ["lib/when/when", "lib/d3", "sg/dashboard", "sg/sideAxis", 
          "sg/palette", "sg/scatter", "sg/data", "sg/util" ], 
           function(when, _d3, dashboard, sideAxis, palette, scatter, data, util) {

  var mohsBoard = dashboard().size([800, 400]); 

  function fetchParameters() {
    function received(data) {
      var last = data.length - 1;
      var plotParameters = data[last][1]; // take the last parameters we received
      return plotParameters;
    }

    var parameters = urlParameters(location.search);
    var sessionId = parameters.sessionId;
    if (!sessionId) console.error("sessionId not found");
    
    var farFuture = (new Date()).getTime * 2;
    var columnPath = "plot/" + sessionId + "/_plotParameters";
    var paramsWhen = data.streamRequest("raw", {until: farFuture, limit: 1}, [columnPath]);

    return paramsWhen.then(received).otherwise(rethrow);
  }



  function drawChart(plotParameters) {
    var namedColumns = plotParameters.sources.map(function(plotSource) {
      return { name: plotSource.columnPath,
               label: plotSource.label
             };
    });

    var charts = [ 
      { title: plotParameters.title,
        timeSeries: plotParameters.timeSeries,
        xScale: plotParameters.timeSeries ? d3.time.scale.utc() : d3.scale.linear(),
        showXAxis: plotParameters.timeSeries,
        padding:[5, 5],    // padding so that marks can extend past the edge of the plot area
        groups: [
          { label: plotParameters.units,
            plot: { plotter: scatter() },
            axis: sideAxis(),
            named: namedColumns
          }
        ]
      }
    ];

    var update = d3.selectAll("body").data([{charts:charts}]);
    mohsBoard(update);
  }

  fetchParameters().then(drawChart);

});
