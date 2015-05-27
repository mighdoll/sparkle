define(['admin/app', 'sg/chart', 'sg/sideAxis',
        "admin/chartDirective", "admin/chartControlPanelDirective"],
  function (app, chart, sideAxis, _chartDirective, _controlPanelDirective) {
    app.directive('chartWithSettings', ['serverConfigWhen', function(serverConfigWhen) {

      function initializeControllerScope($scope) {
        $scope.padding = { top: 20, right: 50, bottom: 50, left: 75 };

        // NG - chartData is here so it can be shared (and trigger watches) on chart and control panel
        $scope.chartData = {
          title: "chart",
          timeSeries: true,
          showXAxis: true,
          margin: $scope.padding,
          transformName: "reduceMax",
          size: [600, 500],
          padding:[5, 5],    // padding so that marks can extend past the edge of the plot area
          groups: [
            { axis: sideAxis(),
              named: []
            }
          ],
          serverConfigWhen: serverConfigWhen
        };

        // stash the api for the chartController
        $scope.$on('chartController', function(e, chartController) {
          $scope.chartController = chartController;
        });
      }


      return {
        restrict: 'E',
        templateUrl: 'partials/chart-with-settings.html',
        scope: {
          apiEvent: '='
        },
        controller: function($scope, $element, $attrs) {
          initializeControllerScope($scope);
          this.addSeries = function(columnPath) {
            $scope.chartController.addSeries(columnPath);
            $scope.chartModified = new Date();
          };

          $scope.$emit($attrs.apiEvent, this); // publish our controller api
        }

      };

  }]);
});