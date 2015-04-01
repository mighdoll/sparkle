define(['angularAMD', 'angular-material', 'angular-upload', 'angular-tree-control'],
  function (angularAMD) {
    var app = angular.module('webapp', ['ngMaterial', 'angularFileUpload', 'treeControl']);
    return angularAMD.bootstrap(app);
  }
);
