define(["admin/app", "lib/d3", "sg/request", "sg/util", "admin/columnPicker",
        "admin/uploadFile", "sg/downloadFile",  "sg/sideAxis"],
  function(app, _d3, request, _util, _columnPicker, _uploadFile, downloadFile,
           sideAxis) {

    // when.js promise with server config, like ports
    app.service('serverConfigWhen', function() {
      return request.jsonWhen("/serverConfig")
    });

    app.controller('DataDashController', ['$scope', '$q', '$mdDialog',
        function($scope, $q, $mdDialog) {

      /** autocomplete a search term for an entity or column */
      this.entityQuery = function(term) {
        var response = request.jsonWhen("/v1/findEntity?q=" + encodeURIComponent(term));
        return response;
      };

      /** list all the columns available for an entity */
      this.columnsQuery = function(entity) {
        var response = request.jsonWhen("/v1/columns/" + encodeURIComponent(entity));
        return response;
      };

      /** download a column or dataset as a .tsv file */
      this.downloadTsv = function(columnPath) {
        var name = lastPathComponent(columnPath);
        var fileName = name + ".tsv";
        downloadFile("/fetch/" + columnPath, fileName, "text/tab-separated-values");
      };

      /** plot a column by adding it to the chart */
      this.plotData = function(columnPath) {
        // for now, we just add columns to the first group.
        $scope.firstChart.addSeries(columnPath);
      };

      // stash the api for the chartController
      $scope.$on('firstChart', function(e, chartWithSettingsController) {
        $scope.firstChart = chartWithSettingsController;

       if (chartWithSettingsController)  { // DEBUG only
         setTimeout(function() {
           chartWithSettingsController.addSeries("data/num");
           $scope.$apply();
         }, 100);
       }

      });

      $scope.showUpload = function(ev) {
        $mdDialog.show({
          controller: DialogController,
          templateUrl: 'partials/upload-dialog.html',
          targetEvent: ev,
        });
      };

      function DialogController($scope, $mdDialog) {
        $scope.close = function() {
          $mdDialog.hide();
        };
      }

      $scope.showLeftPanel = true;

    }]);

});
