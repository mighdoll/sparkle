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

define(["lib/d3", "test/simulateDrag", "test/simulateClick", 
        "lib/when/when"], 
function(_d3, simulateDrag, simulateClick, when) {

  /** return a function that will: 
   *   . zoom the chart by simulating a drag operation. 
   *   . return a when that completes when the zoom has completed */
  function makeZoom(container, testDelay) {
    return function() {
      var deferred = when.defer();

      container.on("zoomBrush.zoomChart", function() {
        var brushEvent = d3.event.detail;
        if (brushEvent.transitionEnd) {
          setTimeout(function() {
            container.on("chart.zoomChart", null);
            deferred.resolve();
          }, testDelay);
        }
      });

      var brushBackground = container.selectAll(".brush rect.background");   // don't use select!  .select copies the .data
      simulateDrag(brushBackground, [100, 50], [50, 0]);

      return deferred.promise;
    };
  };

  /** return a function that verifies that the zoom has completed to the correct domain 
   * (assumes data came from fixture.sampleData, and zoom was by zoomChart) */
  function verifyZoom(chart) {
    return function() {
      var extent = chart.selectAll("g.brush rect.extent"),
          chartData = chart.datum();
      expect(chartData.displayDomain[0]).toEqual(new Date("Thu Oct 31 2013 12:00:33 UTC"));
      expect(chartData.displayDomain[1]).toEqual(new Date("Thu Oct 31 2013 12:00:49.5 UTC"));
      expect(parseFloat(extent.attr("width"))).toEqual(0);
    }
  }

  /** return a future that completes when the chart has finished drawing */
  function awaitRedraw(container) {
    var deferred = when.defer(),
        chartDrawCount = 0;

    container.on("chart.fixture.awaitChartRedraw", function() {
      if (d3.event.detail.drawComplete) {
        if (chartDrawCount == 0) {
          container.on("chart.fixture.awaitChartRedraw", null);
          deferred.resolve();          
        }
        chartDrawCount++;
      }
    });

    return deferred.promise;
  }

  /** return a function that verifies that the chart display is at the original domain
   * (assumes data came from fixture.sampleData) */
  function verifyNoZoom(chart) {
    return function() {
      chartData = chart.datum();
      expect(chartData.displayDomain[0]).toEqual(new Date("Thu Oct 31 2013 12:00:00 UTC"));
      expect(chartData.displayDomain[1]).toEqual(new Date("Thu Oct 31 2013 12:01:39 UTC"));
    };
  }

  return {
    makeZoom:makeZoom,
    verifyZoom:verifyZoom,
    verifyNoZoom:verifyNoZoom,
    awaitRedraw: awaitRedraw
  };

});
