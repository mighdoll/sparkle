define(['admin/app', 'sg/linePlot', 'sg/areaPlot', 'sg/scatter', 'sg/barPlot', 'sg/symbolMark',
        'sg/palette2'],
  function (app, linePlot, areaPlot, scatterPlot, barPlot, symbolMark, palettes) { 
    app.directive('chartPanel', [function() {
      var self = this;

      function setupWatches($scope) {
        watchAndDo($scope, 'chartModified', function() {
          refreshSeriesList($scope);
        });
        watchAndDo($scope, 'selectedSeries', function() {
          refreshSelectedSeries($scope.selectedSeries, $scope.chartData);
        });
        watchAndDo($scope, 'selectedSeries.plot.symbol', changeSymbol);
        watchAndDo($scope, 'selectedSeries.plot.symbolSize', changeSymbol);
        watchAndDo($scope, 'palette', changePalette);

        watchAndRedraw($scope, 'chartData.title');
        watchAndRedraw($scope, 'chartData.showXAxis');
        watchAndRedraw($scope, 'chartData.timeSeries');
        watchAndRedraw($scope, 'chartData.lockYAxis');
        watchAndRedraw($scope, 'selectedSeries.plot.strokeWidth');
        watchAndRedraw($scope, 'selectedSeries.plot.interpolate');
        watchAndRedraw($scope, 'selectedSeries.transformName');
        watchAndRedraw($scope, 'selectedSeries.grouping');
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
                      .size(squareSize);

        selectedSeries.plot.markPlot = marks;

        queueRedraw($scope);
      }

      function changePalette(palette, $scope) {
        console.log("color palette changed", palette);
      }


      /** change the plot type (e.g. line or scatter) of the selected series */
      function changePlotType(plotType, $scope) {
        if (plotType == 'line') {
          $scope.selectedSeries.plot.plotter = linePlot();
        } else if (plotType == 'area') {
          $scope.selectedSeries.plot.plotter = areaPlot();
        } else if (plotType == 'bar') {
          $scope.selectedSeries.plot.plotter = barPlot().barWidth(50);
        } else if (plotType == 'scatter') {
          $scope.selectedSeries.plot.plotter = scatterPlot();
        }

        setPadding($scope.chartData);
        queueRedraw($scope);
      }

      /** set padding for the chart, adding extra side padding if we have a bar chart */
      function setPadding(chartData) {
        var noBars = 
          chartData.groups.every(function(group) {
            return group.series.every(function(series) {
              return !series.plot.plotter || series.plot.plotter.plotterName != 'bar';
            });
          });

        if (noBars) {
          chartData.padding = [5, 5];  // leaves some room for marks
        } else {
          chartData.padding = [25, 5]; // room for 50px wide bar
        }
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

      /** TODO what's the right way 'around with NG? */
      function paletteHtml(palette) {
        var result = "<span class='colors'>";
        for (var j = 0; j < palette.length; j++) {
          var color = palette[j];
          result += "<span class='swatch' background-color='" + color+ "'></span>";
        }
        result += "</span>"; 
        return result;
      }

      /** getter/setter for selectedSeries.plotType */
      function plotTypeAccess(newPlotterName) {
        var $scope = this;
        if (!arguments.length) {
          if ($scope.selectedSeries && $scope.selectedSeries.plot) {
            return $scope.selectedSeries.plot.plotter.plotterName;
          }
          return undefined;
        } else {
          changePlotType(newPlotterName, $scope);
        }
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
          $scope.showPanel = true; // start open for DEBUG convenience
          $scope.plotTypes = ["line", "scatter", "area", "bar"];
          $scope.lineInterpolate = ["linear", "monotone", "basis",  "step"];
          $scope.strokeWidths = [.5, 1, 1.5, 2, 3, 5];
          $scope.selectedSeries = {};
          $scope.symbols = ["circle", "cross", "diamond", "square", "triangle-up", "triangle-down"];
          $scope.symbolSizes = [4, 6, 8, 10, 15];
          $scope.remove = removeSeries;
          $scope.groupings = [
            { name: "automatic", group:"" },  // TODO NYI
            { name: "month", group:"1 month"}, 
            { name: "week", group:"1 week"}, 
            { name: "day", group:"1 day"}, 
            { name: "hour", group:"1 hour"}, 
            { name: "minute", group:"1 minute"}, 
            { name: "second", group:"1 second"} 
          ];
          $scope.aggregations = [
            { name: "min", aggregation: "reduceMin" },
            { name: "max", aggregation: "reduceMax" },
            { name: "mean", aggregation: "reduceMean" },
            { name: "sum", aggregation: "reduceSum" },
            { name: "count", aggregation: "reduceCount" },
            { name: "raw", aggregation: "raw" }
          ];
          $scope.palettes = palettes;
          $scope.palette = palettes[0];
          $scope.paletteHtml = paletteHtml;
          $scope.plotTypeAccess = plotTypeAccess;

          setupWatches($scope);
        }
     };

  }]);
});
