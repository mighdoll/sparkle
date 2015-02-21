require( ["lib/when/when", "lib/d3", "sg/dashboard", "sg/sideAxis",
          "sg/palette", "sg/scatter", "sg/data", "sg/util" ],
  /** A dashboard that is controlled in real time by the service. The dashboards listens for
    * updates to a well-known columnPath based on the sessionId. The column contains
    * 'plotParameters' objects describing what to draw. */
  function(when, _d3, dashboard, sideAxis, palette, scatter, data, util) {

  var plotBoard = dashboard().size([700, 300]);

  // TODO separate the dashboard drawing from listening to plotParameters, so that
  // plotParameters can specify other dashboards than this default one.

  function fetchParameters(paramsFn) {
    /** called every time the server sends a new plotParameters object */
    function plotReceived(data) {
      if (data.length != 0) {
        var last = data.length - 1;
        var plotParameters = data[last][1]; // take the last parameters we received
        paramsFn(plotParameters);
      } else {
        // TODO - why are these being sent?
        console.log("plotDashboard.parameters: ignoring empty data array");
      }
    }

    var parameters = urlParameters(location.search);
    var sessionId = parameters.sessionId;
    if (!sessionId) console.error("sessionId not found");
    
    var farFuture = (new Date()).getTime * 2;
    data.columnRequestSocket("raw", {until: farFuture, limit: 1}, 
        "plot/" + sessionId, "_plotParameters", plotReceived);
  }


  /** draw a chart based on the plotParameters descriptor, replacing any current displayed chart. */
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
        showXAxis: plotParameters.showXAxis,
        transformName: plotParameters.zoomTransform,
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
    plotBoard(update);
  }

  fetchParameters(drawChart);

});
