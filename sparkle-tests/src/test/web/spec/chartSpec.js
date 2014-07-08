/* Copyright 2013  Nest Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.  */

define(["sg/chart", "sg/localData", 
        "test/simulateDrag", "test/simulateClick", "test/chartUtil",
        "test/fixture", "test/chartData", "lib/when/when"], 
  function(chart, localData, simulateDrag, simulateClick, chartUtil, fixture, chartDataMaker, when) {

describe("chart", function() {
  var svg,
      g,
      testChart, 
      sampleData = fixture.sampleData(),
      dataApi = localData(sampleData),
      chartData,
      testDelay = 0,
      chartTransitionTime = 50,
      slowApiDelay = chartTransitionTime + 50;

  beforeEach(function() {
    svg = fixture.svg([400, 200]);
    g = svg.append("g");
    chartData = chartDataMaker(sampleData);

    testChart = chart()
      .transitionTime(chartTransitionTime)
      .dataApi(dataApi);
  });

  afterEach(function() {
    svg.remove();
  });

  /* uncomment as needed for debugging
  it("debugging", function(done) {
    g.data([chartData]);
    testChart(g);
  });

  jasmine.DEFAULT_TIMEOUT_INTERVAL = 500000;
  chartTransitionTime = 500;
  fixture.testDelay = 350;
  slowApiDelay = chartTransitionTime + 50;
  */


  it("shouldn't crash with an empty ChartData", function(done) {
    chartData = {};
    drawChart().then(fixture.finish(done));
  });

  
  it("should display a line chart", function(done) {
    drawChart().then(fixture.finish(done));
  });

  it("should reset zoom", function(done) {
    var zoom = chartUtil.makeZoom(svg, testDelay);
    drawChart(testChart)
      .then(zoom)
      .then(resetZoom)
      .then(chartUtil.verifyNoZoom(g))
      .then(fixture.finish(done));

  });

  it("should zoom", function(done) {
    testZoom(testChart, done);
  });

  it("zoom a slow dataFetch", function(done) {
    testChart
      .dataApi(slowDataApi);

    testZoom(testChart, done);
  });

  /** a data api that returns after a delay */
  function slowDataApi() {
    var deferred = when.defer();
    var saveThis = this,
        saveArgs = Array.prototype.slice.call(arguments, 0);
    setTimeout(function() {
      deferred.resolve(dataApi.apply(saveThis, saveArgs));
    }, slowApiDelay);

    return deferred.promise;
  }
  slowDataApi.toObject = dataApi.toObject;
  slowDataApi.millisToDates = dataApi.millisToDates;


  /** draw the chart, return a when that completes when the chart has drawn */
  function drawChart(chartComponent) {
    var chartComponent = chartComponent || testChart,
        promise = chartUtil.awaitRedraw(g);
    
    g.data([chartData]);
    chartComponent(g);

    return promise;
  }

  /** reset the zoom by double clicking.  return a when that completes when
   * the chart has drawn */
  function resetZoom() {
    var deferred = when.defer();

    svg.on("chart.chartSpec.resetZoom", function() {
      svg.on("chart.chartSpec.resetZoom", null);
      setTimeout(function() {
        deferred.resolve();
      }, 100);
    });

    var brushBackground = svg.select(".brush rect.background");
    setTimeout(function() {
      simulateClick(brushBackground);
      simulateClick(brushBackground);
    }, fixture.testDelay);

    return deferred.promise;
  }

  /** draw and zoom the chart. verify that the result is zoomed and the brush is again hidden */
  function testZoom(chartComponent, done) {
    var zoom = chartUtil.makeZoom(svg, testDelay);

    drawChart(chartComponent)
      .then(zoom)
      .then(chartUtil.verifyZoom(g))
      .then(fixture.finish(done));
  }


});
});
