define(['admin/app', 'sg/chart', 'sg/sideAxis', 'sg/linePlot', 'sg/areaPlot', 'sg/barPlot', 
        'sg/scatter', 'sg/palette2'],
  function (app, chart, sideAxis, linePlot, areaPlot, barPlot, scatterPlot, palettes) {
    app.directive('chart', ['$q', function($q) {

      var chartMaker = chart(),
          currentPalette = palettes.set2;

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

      function addSeries($scope, $element, columnPath) {
        var namedGroup = $scope.chartData.groups[0].named,
            index = namedGroup.length,
            color = currentPalette[index % currentPalette.length],
            lightColor = currentPalette.lighter[index % currentPalette.lighter.length];

        $scope.chartData.padding = [25,0];
        namedGroup.push(
          { columnPath: columnPath,
            transformName: "reduceMax",
            grouping: "",
            plot: {
              plotter: linePlot(),
              color: color,
              lightColor: lightColor,
              strokeWidth: 2,
              interpolate: 'basis'
            }
          }
        );
        queueRedraw($scope, $element);
      }

      function setPalette($scope, $element, palette) {
        currentPalette = palette;
        queueRedraw($scope, $element);
      }

      var redrawPostDigest = false; // redraw only once per digest cycle

      /** redraw the chart at the end of the digest cycle */
      function queueRedraw($scope, $element) {
        if (!redrawPostDigest) {
          redrawPostDigest = true;
          $scope.$$postDigest(function() {
            drawChart($scope.chartData, $element);
            redrawPostDigest = false;
          });
        }
      }

      return {
        restrict: 'E',
        controller: function($scope, $element, $attrs) {
          this.draw = function() {
            drawChart($scope.chartData, $element);
          };

          this.addSeries = function(columnPath) {
            addSeries($scope, $element, columnPath);
          };

          this.setPalette = function(palette) {
            setPalette($scope, $element, palette);
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
