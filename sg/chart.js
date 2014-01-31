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

define(["jslib/d3", "jslib/when/monitor/console", "jslib/when/when", 
        "sg/data3", "sg/util", "sg/zoom", "sg/resizable", "sg/linePlot", "sg/richAxis", 
        "sg/legend", "sg/timeClip", "sg/domCache"], 
   function(_d3, _console, when, dataApi, _util, zoomBrush, resizable, linePlot, richAxis, 
            legend, timeClip, domCache) {

/** Draw a chart graph containing possibly multiple data series and one or two Y axes 
  *
  * Expects to bound to ChartData object. 
  *
  * Note: assumes that it will be bound to an 'svg' DOM node.  (might work on a 'g'..)
  */
function chart() {
  var _title = "",
      _size = [400, 200],     // total size: plot area + padding + margin 
      _margin = { top: 20, right: 50, bottom: 50, left: 50 }, // space around the plot area for axes and titles
      _padding = [0, 0],      // padding inside the plotArea
      _titleMargin = [0, 15], // space around the title text
      _showLegend = true,
      _transitionTime = 500,
      _plotter = linePlot(),
      _dataApi = dataApi,  
      emptyGroups = [{label:"", series:[]}];

  /** Add chart components to the dom element selection. */
  var returnFn = function(container) {
    container.each(bind); 
  };

  /** Return an array of Series from an array of SeriesGroup objects */
  function collectSeries(groups) {
    var nested = groups.map(function(group) { return group.series; });
    return d3.merge(nested);
  }


  /** attach line graph dom elements and subcomponents: title, axes, line sets, 
   * etc. inside one dom container. */ 
  function bind(chartData) {
    var node = this,
        title = chartData.title || _title,
        margin = chartData.margin || _margin,
        outerSize = chartData.size || _size,
        defaultPlotter = chartData.plotter || _plotter,
        dataApi = chartData.dataApi || _dataApi,
        transitionTime = chartData.transitionTime || _transitionTime,
        padding = chartData.padding || _padding,
        showLegend = ("showLegend" in chartData) ? chartData.showLegend : _showLegend,
        selection = d3.select(this),
        inheritedTransition = d3.transition(selection),
        paddedPlotSize = [outerSize[0] - margin.left - margin.right,
                          outerSize[1] - margin.top - margin.bottom],
        plotSpot = [margin.left + padding[0],
                    margin.top +  padding[1]], 
        plotSize = [paddedPlotSize[0] - (2 * padding[0]), 
                    paddedPlotSize[1] - (2 * padding[1])],
        titleMargin = chartData.titleMargin || _titleMargin,
        groups = chartData.groups || emptyGroups,
        allSeries = collectSeries(groups),
        chartId = domCache.saveIfEmpty(node, "chartId", function() {
            return randomAlphaNum(8);
          });

    // title
    showTitle(title, selection, plotSize[0], margin, titleMargin);

    // errors
    if (displayErrors(groups, allSeries, selection, outerSize)) return;

    // data driven css styling of the chart
    if (chartData.styles) { selection.classed(chartData.styles, true); }

    var domain = chartData.displayDomain || initializeDomain(chartData, allSeries);

    selection.on("resize", resize);

    var redraw = function() { bind.call(node, chartData); };

    fetchData(dataApi, allSeries, domain, plotSize[0]).then(dataReady).otherwise(rethrow); 

    function dataReady() {
      var transition = useTransition(inheritedTransition);
      // data plot drawing area
      var plotSelection = attachByClass("g", selection, "plotArea")
        .attr("width", plotSize[0])
        .attr("transform", "translate(" + plotSpot[0] + "," + plotSpot[1] +")");
      var plotClipId = "chart-plot-clip-" + chartId;
      var labelLayer = attachByClass("g", selection, "label-layer")
        .attr("width", plotSize[0])
        .attr("transform", "translate(" + plotSpot[0] + "," + plotSpot[1] +")");

      // x axis
      var xAxisSpot = [plotSpot[0], 
                       plotSpot[1] + plotSize[1]],
          xScaleAxis = timeAxis(transition, domain, xAxisSpot, plotSize[0]),
          xScale = xScaleAxis.scale();

      attachSideAxes(groups, transition, plotSize, paddedPlotSize, margin); 
      selection.on("toggleMaxLock", transitionRedraw);    
      selection.on("zoomBrush", brushed);

      // setup clip so we can draw lines that extend a bit past the edge 
      // or animate clip if we're in a transition
      timeClip(plotSelection, plotClipId, paddedPlotSize, [-padding[0], -padding[1]],
               domain, xScale);

      // copy some handy information into the series object
      allSeries.forEach(function (series) {    
        series.xScale = xScale;
        series.plotSize = deepClone(plotSize);
        series.labelLayer = labelLayer;
        series.displayDomain = domain;
      });

      // draw data plot
      var plotTransition = transition.selectAll(".plotArea");
      attachSeriesPlots(plotTransition, groups, defaultPlotter);
      attachGroupPlots(plotSelection, plotTransition, groups, defaultPlotter);

      // legends    
      if (showLegend)
        attachLegends(labelLayer, allSeries);

      // resizer
      bindResizer(transition);
      
      // zooming support
      bindZoomBrush(plotTransition, xScale, plotSize[1]);

      // notify that we've completed drawing (e.g. for testing)
      drawCompleteNotify(transition, node);
    }

    /** set size from a resize event, then redraw. */
    function resize() {
      size = chartData.size = d3.event.detail.size;
      redraw();
    }

    /** the user has zoomed via the brush control */
    function brushed() {
      if (d3.event.detail.zoomReset) {
        chartData.displayDomain = chartData.maxDomain;  
      } else if (d3.event.detail.extent) {
        chartData.displayDomain = d3.event.detail.extent.slice(0);  // consider: make a setDomain interface? 
      } else {  
        return;
      }
      var transition = transitionRedraw();

      var chartZoom = {
        displayDomain: chartData.displayDomain, 
        chartTransition: transition
      };
      var eventInfo = {detail: chartZoom, bubbles:true};
      node.dispatchEvent(new CustomEvent("chartZoom", eventInfo));
    }

    /** trigger our own transition and redraw. */
    function transitionRedraw() {
      var transition = selection.transition()
        .duration(transitionTime);

      transition.each(function() {
        redraw();
      });

      return transition;
    }
  }

  /** Emit a custom "chart" event when the transition completes and drawing is done.
   * Report the event immediately if we're not called on a transition 
   * Note that we dont't take into account any subsequnet 
   * */
  function drawCompleteNotify(transition, node) {
    if (transition.ease) {
      transition.each("end", report);
    } else {
      report();
    }

    function report() {
      var eventInfo = {detail: {drawComplete:true}, bubbles:true};
      node.dispatchEvent(new CustomEvent("chart", eventInfo));
    }
  }

  /** bind the side axes to chart */
  function attachSideAxes(groups, transition, plotSize, paddedPlotSize, chartMargin) {
    var axisGroups = groups.filter(function(group) {if (group.axis) return true; });
    axisGroups.forEach(function(group) {
      var groupAxis = attachByClass("g", transition, "group-axis");

      var axisGroup = {
        label: group.label,
        colors: group.colors,
        orient: group.orient,
        series: group.series,
        zeroLock: group.zeroLock,
        chartMargin: deepClone(chartMargin),
        plotSize: plotSize.slice(0),
        paddedPlotSize: paddedPlotSize.slice(0)
      };

      var selection = d3.select(groupAxis.node());
      selection.data([axisGroup]);

      groupAxis
        .call(group.axis);
    });
  }


  /** If the caller didn't provide some overall time domain, set it now */
  function initializeDomain(chartData, series) {
    domain = timeDomain(series);
    chartData.displayDomain = chartData.displayDomain || domain;
    chartData.maxDomain = chartData.maxDomain || domain;
    return domain;
  }

  function bindResizer(svg) {
    var resizer = resizable();

    resizer(svg);
  }

  function bindZoomBrush(plot, xScale, plotHeight) {
    var attached = attachComponent(plot, zoomBrush, "brush");
    var zoomer = attached.component;

    zoomer
      .xScale(xScale)
      .height(plotHeight);

    attached.bind();
  }

  /** display any loading errors, return the number of errors found */
  function displayErrors(groups, allSeries, selection, outerSize) {
    var errors = loadingErrors(groups, allSeries);
    displayLoadingErrors(selection, outerSize, errors);
    return errors.length
  }

  /** return an array of loading errors, or an empty array if no errors are found */
  function loadingErrors(groups, allSeries) {
    var seriesErrors = 
      allSeries.map(function(series) {
        return series.error;
      });

    var groupErrors = 
      groups.map(function(group) {
        return group.error;
      });

    var allErrors = seriesErrors.concat(groupErrors);
    
    // filter out the undefined
    return allErrors.filter(function(error) {
      return error;
    });
  }

  /** display any error text from loading missing files */
  function displayLoadingErrors(selection, size, errors) {
    if (errors.length) 
      selection.selectAll("*").remove();

    var update = selection.selectAll(".error").data(errors),
        enter = update.enter(),
        exit = update.exit(),
        middle = [size[0] / 2, size[1] * 3/4],
        height = 20;

    enter
      .append("text")
      .classed("error", true);

    update
      .attr("transform", function(d,i) {
          var yPosition = middle[1] - (height * i); 
          return "translate(" + middle[0] + "," + yPosition + ")";
      })
      .text(function(d) {return d;});

    exit
      .remove(); 
  }

  /** connect the legend components to data and the DOM */
  function attachLegends(transition, allSeries) {
    var legendMargin = [30, 20],
        selection = d3.select(transition.node());

    var legendData = 
      allSeries.map(function(series) {
        return {
          color:series.color,
          label: series.label || series.name
        };
      });

    var legendSelection = attachGroup(selection, "legend", legendMargin);
    legendSelection.data([legendData]).call(legend());
  }

  /** bind the data plotting components to the data and the DOM */
  function attachSeriesPlots(container, groups, defaultPlotter) {
    var groupedInfos = groups.map(function(group) {
      var infos = group.series.map(function(series) {
        var plotter = plotterForSeries(series, group, defaultPlotter);
        if (plotter.groupPlotter) return undefined; // prefer the group level plotter if specified
        return {data: series, component:plotter}; 
      });
      var filtered = infos.filter(function(info) {return info !== undefined;});
      return filtered;
    });

    var componentInfo = d3.merge(groupedInfos);

    bindComponents(container, componentInfo, "series");
  }


  /** return the plotter for a given Series, using the most specific plotter provided 
   * (i.e. if no plotter is provided on the series, use the plotter on the group, or the chart.)  */
  function plotterForSeries(series, group, defaultPlotter) {
    var plotter = defaultPlotter;
    if (series.plot) {
      plotter = series.plot.plotter;
    } else if (group.plot) {
      plotter = group.plot.plotter;
    }
    return plotter;
  }

  /** Return the transtition to use for the drawing the chart, depending on the 
   * transition the chart inherits from it's caller (via transition.each()).  (The 
   * transition may have expired before we get to use it, so we recreate a short transition
   * if need be just so the user sees something)   
   *    selection: use a selection, not a transition
   *    transition (valid): use provided transition
   *    transition (expired): create a new short transition
   */
  function useTransition(inheritedTransition) {
    var valid = validTransition(inheritedTransition);

    if (inheritedTransition.ease && !valid.ease) {
      console.log("chart: creating transition.  inherited transition expired on node:", 
         inheritedTransition.node());
      return valid.transition().duration(150); 
    } 

    return valid;
  }

  /** bind the data plotting components to the data and the DOM */
  function attachGroupPlots(selection, parentTransition, groups, defaultPlotter) {
    var groupers = groups.filter(function(group) {
      if (group.plot && group.plot.plotter && group.plot.plotter.groupPlotter) {
        return group;
      }
    });

    var update = selection.selectAll(".grouped").data(groupers),
        enter = update.enter(),
        exit = update.exit();

    enter
      .append("g")
      .classed("grouped", true);
    
    var transition = toTransition(update, parentTransition);
    transition.each(function(group) { 
      var selection = d3.select(this);
      group.plot.plotter(selection);
    });


    exit
      .remove();
  }

  /** Update the .data field of each dataSeries by requesting data from the
    * server for the current time domain.
    * calls a provided function when complete 
    *
    * parameters:
    *   dataSeries - data descriptors array, a .data field will be added to each 
    *   domain - time range of data requested
    *   approxMax - number of data points requested from the server (server may return more)
    *   completeFn - called when all data has been received
    */
  function fetchData(dataApi, dataSeries, domain, approxMaxPoints) {
    var allFetched = 
      dataSeries.map( function(series) {
        var summary, 
            filter,
            categories = series.categories;
        if (categories) {
          summary = "uniques";    // LATER let plotter decide whether this is needed
          if (categories.length > 0) {
            filter = categories.map(function(category) { 
                return decodeValue(series, category);
            });
          }
        }
        
        var dataParams = {
            domain: domain,
            extraBeforeAfter: true,  // LATER edge is only needed for linePlot..
            approxMaxPoints: approxMaxPoints,
            summary: series.summary || summary,
            filter: filter
          };
        var fetched = dataApi(series.set, series.name, dataParams);
        fetched.then(function(data) {
          series.data = data;
        });
        return fetched;
      });

    return when.all(allFetched);
  }

  /** Get a map that inverts the valueCodes table, caching in the series object. */
  function invertedCodes(series) {
    if (!series._invertedCodes) {
      var inverted = d3.map();
      codeMap(series).forEach(function(code, value) { 
        inverted.set(value, code); 
      });
      series._invertedCodes = inverted;
    }
    return series._invertedCodes;
  }

  /** Get a the valueCodes as a map, caching in the series object. */
  function codeMap(series) {
    if (series._codeMap) return series._codeMap;
    if (!series.valueCodes) return undefined;

    var codes = d3.map(series.valueCodes);
    series._codeMap = codes;

    return codes;
  }

  /** Return the code for a possibly-encoded value.  If no valueCodes table is provided,
   *  returns the original unchanged.  */
  function decodeValue(series, value) {
    if (!series.valueCodes) return value;
    var inverted = invertedCodes(series);
    return inverted.get(value);
  }
  
  /** add title text elements to the svg */
  function showTitle(title, selection, plotWidth, chartMargin, titleMargin) {
    var center = chartMargin.left + plotWidth/2,
        textHeight = 10,
        titleTop = titleMargin[1] + textHeight,
        attachedTitle = attachByClass("text", selection, "title");    

    attachedTitle.entered()
      .attr("class", "title")
      .attr("text-anchor", "middle");

    attachedTitle
      .attr("transform", "translate(" + center + "," + titleTop +")")
      .text(title);
  }


  /** add time axis */
  function timeAxis(selection, domain, position, width) {
    var axis = richAxis()
      .displayLength(width)
      .label(function() {return timeLabel(axis);})
      .scale(d3.time.scale.utc())
      .orient("bottom");

    axis.scale().domain(domain);

    bindComponents(selection, [{component:axis, position:position, data: {}}], "bottom.axis");

    return axis;
  }


  /** return the min and max time from an array of DataSeries */
  function timeDomain(series) {
    if (series.length == 0) {
      return [new Date(0), new Date(0)];
    }

    var min = series.reduce(function(prevValue, item) {
      return Math.min(item.domain[0], prevValue);
    }, series[0].domain[0]);

    var max = series.reduce(function(prevValue, item) {
      return Math.max(item.domain[1], prevValue);
    }, series[0].domain[1]);

    return [new Date(min), new Date(max)];
  }

  var dateFormat = d3.time.format.utc("%H:%M %Y-%m-%d");  // LATER format configurable, and display optional

  /** return the label for the time axis (current minimum displayed time) */
  function timeLabel(displayAxis) {
    var currentMin = displayAxis.domain()[0];
    return dateFormat(new Date(currentMin));
  }

  //
  //  accessor functions (most of these are overridable in the bound chartData)
  //

  returnFn.margin = function(value) {
    if (!arguments.length) return _margin;
    _margin = value;
    return returnFn;
  };

  returnFn.size = function(value) {
    if (!arguments.length) return _size;
    _size = value;
    return returnFn;
  };

  returnFn.title = function(value) {    
    if (!arguments.length) return _title;
    _title = value;
    return returnFn;
  };

  returnFn.showLegend = function(value) {
    if (!arguments.length) return _showLegend;
    _showLegend = value;
    return returnFn;
  };

  returnFn.titleMargin = function(value) {
    if (!arguments.length) return _titleMargin;
    _titleMargin = value;
    return returnFn;
  };

  returnFn.transitionTime = function(value) {
    if (!arguments.length) return _transitionTime;
    _transitionTime = value;
    return returnFn;
  };

  returnFn.plotter = function(value) {
    if (!arguments.length) return _plotter;
    _plotter = value;
    return returnFn;
  };

  returnFn.dataApi = function(value) {
    if (!arguments.length) return _dataApi;
    _dataApi = value;
    return returnFn;
  };

  return returnFn;
}

return chart;
});
