define(['admin/app', 'sg/linePlot', 'sg/scatter', 'sg/symbolMark'],
  function (app, linePlot, scatterPlot, symbolMark) {
    app.directive('chartPanel', [function() {
      var self = this;

      function setupWatches($scope) {
        watchAndDo($scope, 'chartModified', function() {
          refreshSeriesList($scope);
        });
        watchAndDo($scope, 'selectedSeries', function() {
          refreshSelectedSeries($scope.selectedSeries, $scope.chartData);
        });
        watchAndDo($scope, 'selectedSeries.plotType', changePlotType);
        watchAndDo($scope, 'selectedSeries.plot.symbol', changeSymbol);
        watchAndDo($scope, 'selectedSeries.plot.symbolSize', changeSymbol);

        watchAndRedraw($scope, 'chartData.title');
        watchAndRedraw($scope, 'chartData.showXAxis');
        watchAndRedraw($scope, 'chartData.timeSeries');
        watchAndRedraw($scope, 'chartData.lockYAxis');
        watchAndRedraw($scope, 'selectedSeries.plot.strokeWidth');
        watchAndRedraw($scope, 'selectedSeries.plot.interpolate');
        watchAndRedraw($scope, 'selectedSeries.transformName');
      }

      /** watch for a change and call a function, unless the watched expression value is undefined */
      function watchAndDo($scope, expression, fn) {
        $scope.$watch(expression, function(newValue) {
          if (newValue != undefined) {
            fn(newValue, $scope);
          }
        });
      }

      var redrawPostDigest = false; // redraw only once per digest cycle
      /** redraw the chart at the end of the digest cycle */
      function queueRedraw($scope) {
        if (!redrawPostDigest) {
          redrawPostDigest = true;
          $scope.$$postDigest(function() {
            $scope.chartData.api.transitionRedraw();
            redrawPostDigest = false;
          });
        }
      }

      /** watch for a change and redraw the chart, unless the watched expression value is undefined */
      function watchAndRedraw($scope, expression) {
        watchAndDo($scope, expression, function() {
          queueRedraw($scope);
        });
      }

      function changeSymbol(symbolName, $scope) {
        var selectedSeries = $scope.selectedSeries,
            squareSize = Math.pow(selectedSeries.plot.symbolSize || 4, 2),
            marks = symbolMark()
                      .markType(selectedSeries.plot.symbol)
                      .size(squareSize)
                      .color(selectedSeries.color);

        selectedSeries.plot.markPlot = marks;

        queueRedraw($scope);
      }


      /** change the plot type (e.g. line or scatter) of the selected series */
      function changePlotType(plotType, $scope) {
        if (plotType == 'line') {
          $scope.selectedSeries.plot.plotter = linePlot();
        } else if (plotType == 'scatter') {
          $scope.selectedSeries.plot.plotter = scatterPlot();
        }
        queueRedraw($scope);
      }


      /** refresh the controllers model of the list of data series from the underlying chart data */
      function refreshSeriesList($scope) {
        $scope.allSeries = collectNamedSeries($scope.chartData);
        if (!$scope.selectedSeries.columnPath && $scope.allSeries[0]) {
          $scope.selectedSeries = $scope.allSeries[0];
        }
      }

      /** collect all the named series in the chart into an array */
      function collectNamedSeries(chartData) {
        var allSeries = [];
        chartData.groups.forEach(function(group) {
          if (group.named != undefined) {
            allSeries = allSeries.concat(group.named);
          }
        });
        return allSeries;
      }

      function refreshSelectedSeries(selectedSeries, chartData) {
        // DRY with chartDirective, which also sets default values as series are added to the chart
//        if (!selectedSeries.plot) selectedSeries.plot = {};
//        var plot = selectedSeries.plot;
//        if (!plot.plotter) plot.plotter = linePlot();
//        if (!plot.strokeWidth) plot.strokeWidth = 1.5;
//        if (!plot.interpolate) plot.interpolate = 'linear';
      }

      /** remove the series the chartData and redraw */
      function removeSeries(removeNamed) {
        var $scope = this;
        $scope.chartData.groups.forEach(function(group) {
          group.named = group.named.filter(function(named, i) {
            if (named == removeNamed) {
              group.series.splice(i, 1);
              return false;
            } else {
              return true;
            }
          });
        });
        $scope.selectedSeries = {};
        refreshSeriesList($scope);
        queueRedraw($scope);
      }

      return {
        restrict: 'E',
        templateUrl: 'partials/chart-control-panel.html',

        scope: {
          chartData: '=',
          chartController: '=',
          chartModified: '='
        },

        controller: function($scope) {

          $scope.showPanel = false;
          $scope.plotTypes = ["line", "scatter"];
          $scope.lineInterpolate = ["linear", "monotone", "basis",  "step"];
          $scope.strokeWidths = [.5, 1, 1.5, 2, 3, 5];
          $scope.selectedSeries = {};
          $scope.symbols = ["circle", "cross", "diamond", "square", "triangle-up", "triangle-down"];
          $scope.symbolSizes = [4, 6, 8, 10, 15];
          $scope.remove = removeSeries;

          $scope.aggregations = [
            { name: "min", aggregation: "reduceMin" },
            { name: "max", aggregation: "reduceMax" },
            { name: "mean", aggregation: "reduceMean" },
            { name: "sum", aggregation: "reduceSum" },
            { name: "count", aggregation: "reduceCount" },
            { name: "raw", aggregation: "raw" }
          ];

          setupWatches($scope);
        }
     };

  }]);
});