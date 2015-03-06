define(['admin/app', 'lib/d3', 'sg/chart'],
  function (app, _d3, chart) {
  var chartMaker = chart();
  app.controller('ChartControlPanel', ['$scope', function($scope) {
    $scope.chart = {
      title: "untitled",
      showXAxis: true,
      timeSeries: true,
      chartType: "line"
    };
    $scope.showPanel = false;
    $scope.chartTypes = ["scatter", "line"];

    var firstTimeSeries = true;
    $scope.$watch('chart.timeSeries', function() {
      if (!firstTimeSeries) {
        chartData().timeSeries = $scope.chart.timeSeries;
        redrawChart();
      }
      firstTimeSeries = false;
    });

    var firstTimeTitle = true;
    $scope.$watch('chart.title', function() {
      if (!firstTimeTitle) {
        chartData().title = $scope.chart.title;
        redrawChart();
      }
      firstTimeTitle = false;
    });

    var firstShowXAxis = true;
    $scope.$watch('chart.showXAxis', function() {
      if (!firstShowXAxis) {
        chartData().showXAxis = $scope.chart.showXAxis;
        redrawChart();
      }
      firstShowXAxis = false;
    });

  }]);


  /** return the first chart on screen.  LATER inject the chart controller NG style */
  function chartData() {
    return d3.select('#charts').datum().charts[0];
  }

  // we don't correctly support live update of all the options,
  // so for now we erase and redraw everything
  function redrawChart() {
    var data = chartData();

    d3.selectAll('#charts .chart').remove();

    var chartSelect = d3.select('#charts').selectAll('.chart').data([data]);

    chartSelect.enter()
      .append("svg")
      .attr("class", "chart")
      .attr("width", function(d) {return d.size[0]; })
      .attr("height", function(d) {return d.size[1]; });

    chartMaker(chartSelect);
  }


});