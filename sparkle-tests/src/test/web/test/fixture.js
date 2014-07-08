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

define(["lib/d3", "lib/when/when"], function(_d3, when) {

  function svg(_size) {
    var size = _size || [200, 50];

    var svgSelection = 
        d3.select("body").append("svg")
          .attr("width", size[0])
          .attr("height", size[1])
          .style("border-style", "solid")
          .style("border-color", "blue")
          .style("border-width", 1);

    return svgSelection;
  }

  /** return an array sample data with 100 [Date, Number] elements */
  function sampleData() {
    var result = [];
    var start = new Date("Thu Oct 31 2013 12:00:00 UTC");
    for (var i = 0; i < 100; i++) {
      result.push([new Date(start.getTime() + i * 1000), Math.sin(i / 10)]);
    }
    return result;
  }

  /** return a function that when called, will execute the provided doneFn after a testDelay timeout */
  function finish(done) {
    return function() {
      setTimeout(function() {
        done();
      }, fixture.testDelay);
    }
  }

  /** return a function that returns a when that completes after duration millis */
  function wait(duration) {
    var waitTime = duration !== undefined ? duration : fixture.testDelay;
    return function() {
      var deferred = when.defer();
      setTimeout(function() { deferred.resolve(); }, waitTime);
      return deferred.promise;
    }
  }


  var fixture = {
    testDelay: 0,
    svg: svg,
    sampleData: sampleData,
    wait: wait,
    finish: finish
  };

return fixture;
  
});
