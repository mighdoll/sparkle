define(['admin/app', 'sg/chart', 'sg/sideAxis', 'sg/linePlot', 'sg/scatter', 'sg/symbolMark'],
  function (app, chart, sideAxis, linePlot, scatterPlot, symbolMark) {
    app.directive('chart', ['$q', function($q) {

      var chartMaker = chart();
      function drawChart(chartData, $element) {
        var width = chartData.size ? chartData.size[0] : chartMaker.size()[0];
        var height = chartData.size ? chartData.size[1] : chartMaker.size()[1];
        var chartUpdate = d3.select($element[0]).selectAll("svg.chart").data([chartData]);
        chartUpdate.enter().append("svg")
          .attr("width", width)
          .attr("height", height)
          .classed("chart", true);
        chartMaker(chartUpdate);
      }

      function addSeries(chartData, $element, columnPath) {
        chartData.groups[0].named.push(
          { columnPath: columnPath,
            transformName: "reduceMean",
            plot: {
              plotter: linePlot(),
              strokeWidth: 1.5,
              interpolate: 'linear'
//              plotter: scatterPlot(),
//              markPlot:
//                symbolMark()
//                  .markType("square")
//                  .size(8)
//                  .color("green")
            }
          }
        );
        drawChart(chartData, $element);
      }

      return {
        restrict: 'E',
        controller: function($scope, $element, $attrs) {
          this.draw = function() {
            drawChart($scope.chartData, $element);
          };

          this.addSeries = function(columnPath) {
            addSeries($scope.chartData, $element, columnPath);
          };

          $scope.$emit('chartController', this); // publish our controller api
        },
        scope: {
          chartData: '=',
          controllerName: '@'
        },
        link: function($scope, $element, $attr) {
          drawChart($scope.chartData, $element);
        }

      };

  }]);
});