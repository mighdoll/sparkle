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

/** THIS FILE IS OBSOLETE */

define(["jslib/d3", "sg/xyChart", "sg/data"], function(_, xyChart, data) {
/** Draw a time series graph with multiple data series and up to two y axes, with the
  * server specifying which data series, titles, etc.
  * 
  * The server specifies which data series to draw via the /control REST endpoint
  * Zooming is enabled by default.  Data is refreshed from the server with each new zoom.  
  */
return function() {
  var size = [400, 250];

  /** Creator function */
  var returnFn = function(selection) {
     selection.each(attach); 
  };

  /** Fetch data from the server and attach a chart. 
   * bound .data contains the 'graphName' of the server control endpoint */
  function attach(graphName) {
    var selection = d3.select(this),
        chartData,
        mainChart;

    // fetch ChartInfo control dscriptor from the server
    d3.json("/chart/" + graphName, infoReady);
      
    /** translate control block from server into a dataDescriptor for the chart */
    function infoReady(err, chartInfo) {
      var startDomain = chartInfo.domain.map(function(millis) {
        return new Date(millis);
      });
      window.history.replaceState(startDomain);

      var seriesSets = chartInfo.seriesSets;
      
      leftSeriesSets = filterByOrientation(seriesSets, "left");
      rightSeriesSets = filterByOrientation(seriesSets, "right");

      var left = seriesGroup(leftSeriesSets, chartInfo.domain),
          right = seriesGroup(rightSeriesSets, chartInfo.domain);

      chartData = { left:left, right:right}; // publish chartData so popState can get it

      attachChart(chartData, chartInfo.title);
    }

    /** draw the graph (the graph component itself will fetch the data) */
    function attachChart(chartData, title) {
      mainChart = 
        xyChart()
          .size(size)
          .dataApi(data)
          .on("zoom", updateHistory)
          .title(title);

      selection
        .data([chartData]);

      selection
        .call(mainChart);
    }

    function filterByOrientation(seriesSets, orientation) {
      return seriesSets.filter(function(seriesSet) {
        if (seriesSet.orientation === orientation) {
          return true;
        } 
      });
    }

    function updateHistory(domain) {
      window.history.pushState(domain);  
    }

    /** when the browser back/forward button is pressed, redraw at the saved time domain */
    window.onpopstate = function(event) {
      if (event.state) {
        var newDomain = event.state;

        chartData.displayDomain = newDomain;
        selection
          .data([chartData]);

        if (mainChart) {
          selection
            .transition(500)
            .call(mainChart);
        }
      }
    };

    /** create an array of dataSeries descriptors from an array of server set objects */
    function seriesGroup(sets, domain) {
      var unflattened = sets.map (function(seriesSet) {
        return dataSeries(seriesSet, domain);
      });

      return {
        label:sets[0].units,
        series:d3.merge(unflattened)
      };
    }

    /** create a dataSeries descriptor.  i.e. convert from the server series descriptor
      * to a dataSeries structure suitable for data binding to chart data */
    function dataSeries(seriesSet, domain, fn) {
      return seriesSet.series.map ( function(setAndColumn) {
        var split = setAndColumn.split("/");
        var dataSet = split[0];
        var column = split[1];

        return {
          name: column,
          set: dataSet, 
          range: seriesSet.range,
          domain: domain.map(function(millis) {return new Date(millis);})
        };
      });
    }
  }

  returnFn.size = function(value) {
    if (!arguments.length) return size;
    size = value;
    return returnFn;
  };

  return returnFn;
};

});
