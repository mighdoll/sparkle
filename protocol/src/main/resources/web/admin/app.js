define(['angularAMD', 'angular-material'], function (angularAMD) {
  var app = angular.module("webapp", ['ngMaterial']);
  return angularAMD.bootstrap(app);
});
