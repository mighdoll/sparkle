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

define(["sg/dashboard", "sg/sideAxis", "sg/localData",
        "test/simulateDrag", "test/simulateClick", "test/chartUtil", "test/fixture", 
        "test/mockMetaData", "lib/when/when"], 
  function(dashboard, sideAxis, localData, simulateDrag, simulateClick, chartUtil, fixture, 
    mockMetaData, when) {

describe("dashboard", function() {
  var dashTransitionTime = 50;
  /* for debugging: 
  fixture.testDelay = 1500;   
  dashTransitionTime = 500;
  jasmine.DEFAULT_TIMEOUT_INTERVAL = 500000;
  */
  var bugs = { 
    title: "Bug counts",
    groups: [
      { axis: sideAxis(),
        named: [
          { name: "ants.csv/count" }
        ]
      }
    ]
  };
  var dashDiv;

  beforeEach(function() {
    dashDiv = d3.selectAll("body").append("div").classed("dashboard", true);
  });

  afterEach(function() {
    dashDiv.remove();
  });

  it("can draw a single chart", function(done) {
    drawDashboard([bugs]).then(fixture.finish(done));
  });
  
  xit("can draw two charts", function(done) {
    drawDashboard([bugs, bugs]).then(fixture.finish(done));
  });

  xit("two charts zoom together", function(done) {
    drawDashboard([bugs, bugs], setZoomTogether)
      .then(zoomOne)
      .then(verify)
      .then(fixture.finish(done));

    function setZoomTogether(dash) { dash.zoomTogether(true); }

    function verify() {
      var secondChartNode = d3.selectAll(".chart")[0][1],
          secondChart = d3.select(secondChartNode);
      chartUtil.verifyZoom(secondChart)();
      window.history.back();
    }
  });

  xit("history back returns to original state", function(done) {
    var promisedDash = drawDashboard([bugs]),
        chartSelection = d3.select(".chart");

    promisedDash
      .then(zoomOne)
      .then(navigateBack(chartSelection))
      .then(chartUtil.verifyNoZoom(chartSelection))
      .then(fixture.finish(done));

  });

  /** zoom the first chart on the page.  Return a when that completes when the zoom is complete. */
  function zoomOne() {
    var firstChart = d3.select(".chart");
    return chartUtil.makeZoom(firstChart, fixture.testDelay)();
  }

  /** navigate the browser back.  Return a when that completes when the chart in the provided
   * container has redrawn */
  function navigateBack(container) {
    return function() {
      var promise = chartUtil.awaitRedraw(container);
      window.history.back();
      return promise;
    };
  }

  /** draw the dashboard, return a when that completes when the requisite number of charts have been drawn */
  function drawDashboard(charts, dashModify) {
    var deferred = when.defer(),
        found = 0;
        
    d3.select("body").on("chart.dashboardSpec", function() {
      if (d3.event.detail.drawComplete) {
        if (++found >= charts.length) {
          deferred.resolve(dash);
        }
      }
    });
    var sampleData = fixture.sampleData(),
        metaDataApi = mockMetaData({"ants.csv/count":sampleData});

    var dash = 
      dashboard()
        .size([400,200])  // we match the size in chartSpec so that chartUtil.verifyZoom works.
        .transitionTime(dashTransitionTime)
        .dataApi(localData(sampleData));
    
    dashModify && dashModify(dash);

    var dashData = [{charts:charts}],
        update = dashDiv.data(dashData);

    update.call(dash);
      
    return deferred.promise;
  }

});
});

