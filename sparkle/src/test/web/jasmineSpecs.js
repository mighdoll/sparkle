require( ["spec/zoomSpec", "spec/chartSpec", "spec/dashboardSpec"], 
function() {
  var env  = jasmine.getEnv();
  env.execute();
});
