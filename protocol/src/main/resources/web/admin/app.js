define(['angularAMD', 'angular-material', 'angular-upload'], function (angularAMD) {
  var app = angular.module("webapp", ['ngMaterial', 'angularFileUpload']);
  return angularAMD.bootstrap(app);
});
