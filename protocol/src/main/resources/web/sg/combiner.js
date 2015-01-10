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

define(["lib/d3", "sg/domCache", "sg/barGroup", "sg/streamWrapper"], 
  function(_, domCache, barGroup, streamWrap) {

var combiner = function() {
  var _plotter = barGroup();

  var returnFn = function(container) {
    container.each(attach);
  };

  /** Combine the data streams 
   * bind to a SeriesGroup. */
  function attach(seriesGroup) {
    var selection = d3.select(this),
        seriesDatas = seriesGroup.series.map(function(series) { return series.data;} ),
        combined = combineData(seriesDatas),
        plotter = seriesGroup.plotter || _plotter;

    seriesGroup.combined = combined;
    var update = selection.selectAll(".combined").data([seriesGroup]),
        transition = d3.transition(update),
        enter = update.enter(),
        exit = update.exit();
        
    enter
      .append("g")
      .classed("combined", true);

    transition
      .call(plotter);

    exit
      .remove();
  }

  /** return an array of the form [Date,[Number,Number,,,Number]] 
   * representing the data to be drawn at each time.  
   * Pass in an array of [Date,Double] arrays.  */
  function combineData(dataSeriesArray) {
    /** convert the data series array into seekable streams */
    var streams = dataSeriesArray.map(function(array) {
      return streamWrap(array);
    });

    var results = [];
    // iterate through the streams, collecting an array of values at each time
    for (var time = nextTime(); time !== undefined; time = nextTime()) {
      var numbers = collectAt(time);
      results.push([time, numbers]);
    }
    return results;

    /** return the next smallest time in any of the streams
     * (the streams are sorted, so we just pick the array with the smallest
     * next time) */
    function nextTime() {
      var nextTimes = streams.map(function(stream) {
        return stream.peekKey();
      });

      return nextTimes.reduce(function(a,b) {
        if (a === undefined) return b;
        if (b === undefined) return a;
        return (a <= b) ? a : b;
      }, nextTimes[0]);
    }

    /** Return an array of numbers matching the given time, with the value 'undefined' 
     * in the array for streams that don't match the given time. */
    function collectAt(time) {
      return streams.map(function(stream) {
        return stream.popIfKey(time);
      });
    }
  }


  returnFn.plotter = function(value) {
    if (!arguments.length) return _plotter;
    _plotter = value;
    return returnFn;
  };

  /** tell the chart that we plot groups, not just one series */
  returnFn.groupPlotter = true;

  return returnFn;
};

return combiner;

});
