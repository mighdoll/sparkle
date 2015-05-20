define(['angularAMD', 'angular-material', 'ng-file-upload', 'angular-tree-control'],
  function (angularAMD) {
    var app = angular.module('webapp', ['ngMaterial', 'ngFileUpload', 'treeControl']);
    return angularAMD.bootstrap(app);
  }
);
