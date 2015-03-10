define(['admin/app', 'lib/d3', 'sg/chart', 'sg/linePlot', 'sg/scatter', 'sg/palette',
        'sg/symbolMark'],
  function (app, _d3, chart, linePlot, scatter, palette, symbolMark) {
  var chartMaker = chart();
  app.controller('ChartControlPanel', ['$scope', function($scope) {
    $scope.showPanel = false;
    $scope.chartTypes = ["line", "scatter"];
    $scope.lineInterpolate = ["linear", "monotone", "basis",  "step"];
    $scope.aggregations = ["max", "min", "mean", "count", "raw"];
    $scope.symbols = ["circle", "cross", "diamond", "square", "triangle-up", "triangle-down"];
    $scope.strokeWidths = [.5, 1, 1.5, 2, 2.5, 3, 4, 5];
    $scope.symbolSizes = [4, 6, 8, 10, 15];
    $scope.palettes = ["blue purple", "orangey"];
    $scope.chart = {
      title: "chart",
      showXAxis: true,
      timeSeries: true,
      chartType: "line",
      lineInterpolate: "linear",
      aggregation: "min",
      strokeWidth: 1.5,
      palette: "blue purple",
      symbol: "circle",
      symbolSize: 6
    };

    function changePlotter() {
      var value = $scope.chart.chartType;
      if (value == "line") {
        changeLinePlotter();
      } else if (value == "scatter") {
        changeScatterPlotter();
      } else {
        console.log("unexpected chart type:", value);
      }
    }

    function changeAggregation() {
      var value = $scope.chart.aggregation;
      var aggregation = "raw";
      switch (value) {
        case "max":
        case "min":
        case "mean":
        case "count":
          aggregation = "reduce"+value;
          break;
        case "raw":
          break;
      }

      chartData().groups[0].series.forEach( function(series) {
        series.transformName = aggregation;
      });
      chartData().transformName = aggregation;
    }

    function changeLinePlotter() {
      chartData().plotter =
        linePlot()
          .interpolate($scope.chart.lineInterpolate)
          .strokeWidth($scope.chart.strokeWidth);
    }

    function changeScatterPlotter() {
      var symbol = d3.svg.symbol();

      var value = $scope.chart.symbol
      switch (value) {
        case "square":
        case "circle":
        case "cross":
        case "diamond":
        case "triangle-up":
        case "triangle-down":
          symbol.type(value);
          break;
      }
      var squareSize = Math.pow($scope.chart.symbolSize, 2);
      symbol.size(squareSize);

      var marks = symbolMark().symbol(symbol);

      chartData().plotter = scatter().plot({plotter: marks});
    }



    function changePalette() {
      var colors = palette.purpleBlueGreen3();
      switch ($scope.chart.palette) {
        case "blue purple":
          break;
        case "orangey":
          colors = palette.orange4();
          break;
      }
      chartData().groups[0].colors = colors;
    }

    /** watch for a change to the control panel chart scope and apply
        it to the d3 chart */
    function watchChartAndRedraw(field, erase, changeFn) {
      function trigger() {
        skipFirst(field, change);
      }

      function defaultChange(value) {
        chartData()[field] = value;
      }

      function change() {
        var value = $scope.chart[field];
        var fn = changeFn || defaultChange;
        fn(value);
        redrawChart(erase);
      }

      $scope.$watch('chart.'+field, trigger);
    }

    watchChartAndRedraw('symbol', true, changeScatterPlotter);
    watchChartAndRedraw('symbolSize', true, changeScatterPlotter);
    watchChartAndRedraw('palette', true, changePalette);
    watchChartAndRedraw('strokeWidth', true, changeLinePlotter);
    watchChartAndRedraw('aggregation', true, changeAggregation);
    watchChartAndRedraw('lineInterpolate', true, changeLinePlotter);
    watchChartAndRedraw('chartType', true, changePlotter);
    watchChartAndRedraw('title', false);
    watchChartAndRedraw('timeSeries', true);
    watchChartAndRedraw('showXAxis', true);
  }]);


  /** call a function except the first time it is called */
  var firstCalled = {};
  function skipFirst(name, fn) {
    if (firstCalled[name]) {
      fn();
    } else {
      firstCalled[name] = true;
    }
  }


  /** return the first chart on screen.  LATER inject the chart controller NG style */
  function chartData() {
    return d3.select('#charts').datum().charts[0];
  }

  // we don't correctly support live update of all the options,
  // so for now we erase and redraw everything
  function redrawChart(erase) {
    var data = chartData();

    if (erase) {
      d3.selectAll('#charts .chart').remove();
    }

    var chartSelect = d3.select('#charts').selectAll('.chart').data([data]);

    chartSelect.enter()
      .append("svg")
      .attr("class", "chart")
      .attr("width", function(d) {return d.size[0]; })
      .attr("height", function(d) {return d.size[1]; });

    chartSelect
      .call(chartMaker);

  }


});