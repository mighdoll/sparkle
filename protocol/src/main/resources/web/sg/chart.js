define(['d3', 'when', 
        'sg/data', 'sg/util', 'sg/zoom', 'sg/resizable', 'sg/linePlot', 'sg/richAxis', 
        'sg/legend', 'sg/timeClip', 'sg/domCache'], 
   function(_d3, when, sgData, _util, zoomBrush, resizable, linePlot, richAxis, 
            legend, timeClip, domCache) {

var seriesId = 1;     // unique id for every series added to the chart

/** Draw a chart graph containing possibly multiple data series and one or two Y axes 
  *
  * Expects to bound to ChartData object. 
  *
  * Note: assumes that it will be bound to an 'svg' DOM node.  (might work on a 'g'..)
  */
function chart() {
  var _title = '',
      _size = [400, 200],     // total size: plot area + padding + margin 
      _margin = { top: 20, right: 50, bottom: 50, left: 50 }, // space around the plot area for axes and titles
      _padding = [0, 0],      // padding inside the plotArea
      _titleMargin = [0, 15], // space around the title text
      _showLegend = true,
      _transitionTime = 500,
      _plotter = linePlot(),
      _dataApi = sgData,  
      _showXAxis = true,
      _lockYAxis = false,
      _transformName = 'Raw',  
      _xScale = null,
      _timeSeries = true,
      emptyGroups = [{label:'', series:[]}];

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
        defaultTransform = chartData.transformName || _transformName,
        title = chartData.title || _title,
        margin = chartData.margin || _margin,
        outerSize = chartData.size || _size,
        defaultPlotter = chartData.plotter || _plotter,
        dataApi = chartData.dataApi || _dataApi,
        transitionTime = chartData.transitionTime || _transitionTime,
        padding = chartData.padding || _padding,
        showLegend = ('showLegend' in chartData) ? chartData.showLegend : _showLegend,
        selection = d3.select(this),
        inheritedTransition = d3.transition(selection),
        lockYAxis = chartData.lockYAxis || _lockYAxis,
        paddedPlotSize = [outerSize[0] - margin.left - margin.right,
                          outerSize[1] - margin.top - margin.bottom],
        plotSpot = [margin.left + padding[0],
                    margin.top +  padding[1]],
        plotSize = [paddedPlotSize[0] - (2 * padding[0]), 
                    paddedPlotSize[1] - (2 * padding[1])],
        titleMargin = chartData.titleMargin || _titleMargin,
        groups = chartData.groups || emptyGroups,
        timeSeries = ('timeSeries' in chartData) ? chartData.timeSeries : _timeSeries,
        showXAxis = ('showXAxis' in chartData) ? chartData.showXAxis: _showXAxis,
        xScale = chartData.xScale || _xScale || timeSeries ? d3.time.scale.utc() : d3.scale.linear(),
        chartId = domCache.saveIfEmpty(node, 'chartId', function() {
            return randomAlphaNum(8);
          }),
        serverConfigWhen = chartData.serverConfigWhen;

//    console.log("redrawing chart"); // TODO we're redrawing overmuch

    // data driven css styling of the chart
    if (chartData.styles) { selection.classed(chartData.styles, true); }

    // title
    showTitle(title, selection, plotSize[0], margin, titleMargin);

    selection.on('resize', resize);

    var redraw = function() { bind.call(node, chartData); };

    // expose an api to the outside world
    chartData.api = { transitionRedraw:transitionRedraw };

    namedSeriesMetaData(groups, dataApi)
      .then(seriesMetaDataLoaded);

    function seriesMetaDataLoaded() {
      var allSeries = collectSeries(groups);

      if (allSeries.length == 0) {
        var exceptTitle = selection.selectAll('*').filter(function() {
          return !d3.select(this).classed('title');
        });
        // no series? fade out everything except the title
        exceptTitle.transition().style('opacity', 0).remove();
        return;
      }

      var errors = displayErrors(groups, allSeries, selection, outerSize);

      function failed(err) {
        console.log("seriesMetaDataLoaded err:", err);
      }

      /** return true if all the series contain no data */
      function allEmpty() {
        return allSeries.every(function(series) {
          return (!series.domain || series.domain.length == 0);
        });
      }

      if (!errors && !allEmpty()) {
        trackDomainExtent(chartData, allSeries);
        var requestUntil = chartData.displayDomain[1] == chartData.domainExtent[1] ?
            undefined        // unspecified end (half bounded range) if selection is max range
            : chartData.displayDomain[1] + 1; // +1 to be inclusive of last element
        var requestDomain = [ chartData.displayDomain[0], requestUntil ];
        allSeries.forEach(function (series) {
          series.transformName = series.transformName ? series.transformName : defaultTransform;
        });
        var fetched = fetchData(serverConfigWhen, dataApi, allSeries, requestDomain, timeSeries, plotSize[0], moreData);
        fetched.then(function(){ dataReady(allSeries); }).otherwise(failed);
      }
    }

    /** Update chart meta data and re-plot based on new data received from the server. */
    function moreData(series, data) {
      if (data.length) {  // TODO try trackDomainExtent here
        var lastKey = data[data.length-1][0];
        var newEnd = Math.max(lastKey, chartData.displayDomain[1]);
        series.data = series.data.concat(data); // TODO overwrite existing keys not just append
        chartData.displayDomain[1] = newEnd;
        transitionRedraw();
      }
    }

    /** Plot the chart now that the data has been received from the server. */
    function dataReady(allSeries) {
      var transition = useTransition(inheritedTransition);
      var labelLayer = attachByClass('g', selection, 'label-layer')
          .attr('width', plotSize[0])
          .attr('transform', 'translate(' + plotSpot[0] + ',' + plotSpot[1] +')');
      // data plot drawing area
      var plotSelection = attachByClass('g', selection, 'plotArea')
        .attr('width', plotSize[0])
        .attr('transform', 'translate(' + plotSpot[0] + ',' + plotSpot[1] +')');
      var plotClipId = 'chart-plot-clip-' + chartId;

      // x axis
      var xAxisSpot = [plotSpot[0],
                       plotSpot[1] + plotSize[1] + padding[1]];

      xAxis(transition, chartData.displayDomain, timeSeries, xAxisSpot,
            plotSize[0], xScale, showXAxis);

      attachSideAxes(groups, transition, plotSize, paddedPlotSize, margin, lockYAxis);
      selection.on('zoomBrush', brushed);

      // setup clip so we can draw lines that extend a bit past the edge 
      // or animate clip if we're in a transition
      timeClip(plotSelection, plotClipId, paddedPlotSize, [-padding[0], -padding[1]],
               chartData.displayDomain, xScale);

      // copy some handy information into the series object
      allSeries.forEach(function (series) {    
        series.xScale = xScale;
        series.plotSize = deepClone(plotSize);
        series.displayDomain = deepClone(chartData.displayDomain);
      });

      // draw data plot
      var plotTransition = transition.selectAll('.plotArea');
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
        chartData.displayDomain = chartData.domainExtent;  
      } else if (d3.event.detail.extent) {
        // convert date back to millis if necessary
        var newDomain = d3.event.detail.extent.map( function(maybeDate) {
          if (isDate(maybeDate)) {
            return maybeDate.getTime();
          } else {
            return maybeDate;
          }
        });
        chartData.displayDomain = newDomain;  // consider: make a setDomain interface? 
      } else {  
        return;
      }
      var transition = transitionRedraw();

      var chartZoom = {
        displayDomain: chartData.displayDomain, 
        chartTransition: transition
      };
      var eventInfo = {detail: chartZoom, bubbles:true};
      node.dispatchEvent(new CustomEvent('chartZoom', eventInfo));
    }

    /** trigger our own transition and redraw. return the transition so
     * that other animations can share our transition timing. */
    function transitionRedraw() {
      var transition = selection.transition()
        .duration(transitionTime);

      transition.each(function() {
        redraw();
      });

      return transition;
    }
  }

  /** Emit a custom 'chart' event when the transition completes and drawing is done.
   * Report the event immediately if we're not called on a transition 
   * Note that we dont't take into account any subsequnet 
   * */
  function drawCompleteNotify(transition, node) {
    if (transition.ease) {
      transition.each('end', report);
    } else {
      report();
    }

    function report() {
      var eventInfo = {detail: {drawComplete:true}, bubbles:true};
      node.dispatchEvent(new CustomEvent('chart', eventInfo));
    }
  }

  /** bind the side axes to chart */
  function attachSideAxes(groups, transition, plotSize, paddedPlotSize, chartMargin, lockYAxis) {
    var axisGroups = groups.filter(function(group) {if (group.axis) return true; });
    axisGroups.forEach(function(group) {
      var groupAxis = attachByClass('g', transition, 'group-axis');

      var axisGroup = {
        label: group.label,
        orient: group.orient,
        series: group.series,
        zeroLock: group.zeroLock,
        lockYAxis: lockYAxis,
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


  /** Adjust the domain extent based on the current series. Update the display domain 
   * to be inside the domain extent. */
  function trackDomainExtent(chartData, allSeries) {
    var extent = keysExtent(allSeries);
    if (extent) {
      chartData.domainExtent = extent;
      var displayed = chartData.displayDomain;
      if (displayed) {
        if (displayed[0] < extent[0] || displayed[0] > extent[1]) {
          displayed[0] = extent[0];
        }
        if (displayed[1] < extent[0] || displayed[1] > extent[1]) {
          displayed[1] = extent[1];
        }
      } else {
        chartData.displayDomain = extent;
      }
    }
  }

  function bindResizer(svg) {
    var resizer = resizable();

    resizer(svg);
  }

  function bindZoomBrush(plot, xScale, plotHeight) {
    var attached = attachComponent(plot, zoomBrush, 'brush');
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
      selection.selectAll('*').remove();

    var update = selection.selectAll('.error').data(errors),
        enter = update.enter(),
        exit = update.exit(),
        middle = [size[0] / 2, size[1] * 3/4],
        height = 20;

    enter
      .append('text')
      .classed('error', true);

    update
      .attr('transform', function(d,i) {
          var yPosition = middle[1] - (height * i); 
          return 'translate(' + middle[0] + ',' + yPosition + ')';
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
          color: series.plot.color,
          label: series.label || series.name
        };
      });

    var legendSelection = attachGroup(selection, 'legend', legendMargin);
    legendSelection.data([legendData]).call(legend());
  }

  /** bind the data plotting components to the data and the DOM */
  function attachSeriesPlots(container, groups, defaultPlotter) {
    plottedSeriesIds = [];
    groups.forEach(function(group) {
      group.series.forEach(function(series) {
        if (!series.uniqueId) {
          series.uniqueId = 'series_' + seriesId++;
        }
        plottedSeriesIds.push(series.uniqueId);
        var plotter = plotterForSeries(series, group, defaultPlotter);
        var enteredSeries = bindComponent(container, plotter, series, series.uniqueId);
        enteredSeries.classed('series', true);
      });
    });

    /** return true if the */
    function isPlottedSeries(node) {
      return plottedSeriesIds.some(function(plotted) {
        return node.classList.contains(plotted);
      });
    }

    var oldSeries =
      container.selectAll('.series').filter(function() {
        return !isPlottedSeries(this);
      });

    // remove immediately, not in transition, so as not to distract from re-zooming Y axis
    asSelection(oldSeries).remove();
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

  /** Return the transition to use for the drawing the chart, depending on the
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
      console.log('chart: creating transition.  inherited transition expired on node:', 
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

    var update = selection.selectAll('.grouped').data(groupers),
        enter = update.enter(),
        exit = update.exit();

    enter
      .append('g')
      .classed('grouped', true);
    
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
    * returns a when that completes with the first set of data
    * calls a provided function when subsequent data is available
    *
    * parameters:
    *   serverConfigWhen - when.js promise with server config, like ports
    *   dataApi - access to the sg/data module
    *   dataSeries - data descriptors array, a .data field will be added to each 
    *   domain - time range of data requested
    *   maxResults - number of data points requested from the server 
    *   timeSeries - true if the incoming data should be converted to Date objects
    *   moreData - called with data updates subsequent to the initial data
    */
  function fetchData(serverConfigWhen, dataApi, dataSeries, domain, timeSeries, maxResults, moreData) {
    var allFetched = 
      dataSeries.map(function(series) {
        var fetched = when.defer();

        var partsControl = { };
        if (!series.grouping || series.grouping == '' || series.grouping == 'automatic') {
          partsControl.intoCountedParts = maxResults;
        } else {
          partsControl.partBySize = series.grouping;
        }
        if (timeSeries) {
          partsControl.timeZoneId = 'UTC';  // TODO allow override of timezone
        }

        var rangeParams = {
            ranges: [
              { start: domain[0],
                until: domain[1]  
              }
            ],
          };

        var transformParams = combineProperties(rangeParams, partsControl);

        //TODO: pass errorFn
        dataApi.columnRequestSocket(serverConfigWhen, series.transformName, transformParams, series.set, series.name, received);
        function received(data) {
          var translatedData;
          if (timeSeries) {
            translatedData = dataApi.millisToDates(data);
          } else {
            translatedData = data;
          }

          // update when for first set of data, call fn for subsequent updates
          // TODO fix race: we shouldn't update stream 1 if stream2 head hasn't arrived
          if (fetched.promise.inspect().state === 'pending') {
            series.data = translatedData;
            fetched.resolve();
          } else {
            moreData(series, translatedData);
          }
        }
        return fetched.promise;
      });

    return when.all(allFetched);
  }

  /** add title text elements to the svg */
  function showTitle(title, selection, plotWidth, chartMargin, titleMargin) {
    var center = chartMargin.left + plotWidth/2,
        textHeight = 10,
        titleTop = titleMargin[1] + textHeight,
        attachedTitle = attachByClass('text', selection, 'title');    

    attachedTitle.entered()
      .attr('class', 'title')
      .attr('text-anchor', 'middle');

    attachedTitle
      .attr('transform', 'translate(' + center + ',' + titleTop +')')
      .text(title);
  }


  /** revise the scaling function for the xAxis based on current zoom and display width */
  function setXScale(xScale, displayDomain, timeSeries, width) {
    var translatedDomain = (timeSeries
      ? displayDomain.map( function(millis) { return new Date(millis); })
      : displayDomain);
    xScale.domain(translatedDomain);
    xScale.rangeRound([0, width]);
  }

  /** add x axis, typically for time. Also, revise the xAxis scaling function */
  function xAxis(transition, displayDomain, timeSeries, position, width, scale, show) {
    setXScale(scale, displayDomain, timeSeries, width);

    var axisSelection = d3.select(transition.node()).selectAll('.bottom.axis').data([{}]);
    if (!show) {
      axisSelection.remove();
    } else {
      var axis = richAxis();

      var labeler = function() {
        if (timeSeries) {
          return function() {return timeLabel(axis);};
        } else {
          return function() {return rawLabel(axis); };
        }
      }

      axis
        .displayLength(width)
        .label(labeler())
        .scale(scale)
        .orient('bottom');

      axis.scale(scale);

      axisSelection.enter()
        .append('g')
        .classed('bottom axis', true);

      var translate = 'translate(' + position[0] + ',' + position[1] + ')';
      var axisTransition = toTransition(axisSelection, transition);

      axisSelection
        .attr('transform', translate);

      axisTransition
        .call(axis);
    }


    return axis;
  }


  /** return the min and max time from an array of DataSeries */
  function keysExtent(allSeries) {
    var seriesWithDomains = allSeries.filter(function(series) {
      return series.domain && series.domain.length == 2;
    });

    if (seriesWithDomains.length == 0) {
      return undefined;
    }

    var firstDomain = seriesWithDomains[0].domain;

    var min = seriesWithDomains.reduce(function(prevValue, item) {
      return Math.min(item.domain[0], prevValue);
    }, firstDomain[0]);

    var max = seriesWithDomains.reduce(function(prevValue, item) {
      return Math.max(item.domain[1], prevValue);
    }, firstDomain[1]);

    return [min, max];
  }

  var dateFormat = d3.time.format.utc('%H:%M %Y-%m-%d');  // LATER format configurable, and display optional

  /** return the label for a time axis (current minimum displayed time) */
  function timeLabel(displayAxis) {
    var currentMin = displayAxis.domain()[0];
    return dateFormat(new Date(currentMin));
  }

  /** return the label for a key axis */
  function rawLabel(displayAxis) {
    return displayAxis.domain()[0];
  }

  /** Fetch the extent of each 'named' series from the server. Update the
    * the chartData with the extent of the series.
    *
    * (this is so things like zooming
    * will know their maximum extent, as well as for convenience so that the first
    * plot can set its displayed range to the extent of the data)
    *
    * Fetching is protected by 'namedFetched' latch - it only happens the first time.
    * Return a when that completes the fetching is done. */
  function namedSeriesMetaData(groups, dataApi) {
    var whens = [];

    groups.forEach(function(group) {
      var groupWhens = [];

      if (group.series == undefined) group.series = [];

      if (group.named) {
        var futureSeries = fetchNamedSeries(dataApi, group.named).then(function(seriesArray) {
          if (seriesArray) {
            group.series = group.series.concat(seriesArray);
          }
        }).otherwise(function(err) {
          console.log("named error:", err);
        });
        groupWhens.push(futureSeries);
      }

      if (group.plot && group.plot.named) {
        var futureSeries = fetchNamedSeries(group.plot.named).then(function(seriesArray) {
          group.plot.series = seriesArray;
          group.series = group.series.concat(seriesArray);
        });
        groupWhens.push(futureSeries);
      }

      whens = whens.concat(groupWhens);
    });

    return when.all(whens);
  }

  /** Fetch series metadata from the server.
    * Returns a promise that completes with an array of the new Series objects, or a special error Series
    * if the call returned with an error.
    * Call on an array of NamedSeries */
  function fetchNamedSeries(dataApi, named) {

    /** return a 'fake' Series with an error property set */
    function errorSeries(err) {
      console.log("returning error Series for:", err);
      return {
        error: err.statusText + ': ' + err.responseText
      };
    }

    var futures =
      named.map(function(namedSeries) {
        if (namedSeries.fetched) {
          return when.resolve();
        } else {
          var promisedSeries = fetchSeriesInfo(dataApi, namedSeries);
          namedSeries.fetched = true;
          return promisedSeries.otherwise(errorSeries);
        }
      });

    function skipUndefined(array) {
      return arrayClean(array, undefined);
    }

    return when.all(futures).then(skipUndefined);
  }

  /** Fetch a namedSeries from the server via the /column REST api.
   *  Returns a promise that completes with a Series object.  */
  function fetchSeriesInfo(dataApi, namedSeries) {
    var setAndColumn = namedSeries.columnPath,
        lastSlash = setAndColumn.lastIndexOf('/'),
        dataSetName = setAndColumn.slice(0, lastSlash),
        column = setAndColumn.slice(lastSlash+1, setAndColumn.length),
        futureKeyValueRanges = dataApi.columnRequestHttp("KeyValueRanges", {},
          dataSetName, column);

    /** plotter that will be used to plot this series */
    function plotter() {
      return namedSeries.plot && namedSeries.plot.plotter || chart().plotter();
    }

    /** now that we have the series metadata from the server, fill in the Series */
    function received(data) {
      var keyValueRanges = arrayToObject(data);
      namedSeries.set = dataSetName;
      namedSeries.name = column;
      // TODO reconsider this approach, the max value of the entire series doesn't take the transform into account
      namedSeries.range = keyValueRanges.valueRange;
      namedSeries.domain = keyValueRanges.keyRange;

      return namedSeries;
    }

    var result = futureKeyValueRanges.then(received).otherwise(requestFailed);

    function requestFailed(err) {
      // TODO display error on screen
      console.log("KeyValueRanges failed", err, result);
      return [];
    }

    return result;
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

  returnFn.timeSeries = function(value) {
    if (!arguments.length) return _timeSeries;
    _timeSeries = value;
    return returnFn;
  };

  returnFn.showXAxis = function(value) {
    if (!arguments.length) return _showXAxis;
    _showXAxis = value;
    return returnFn;
  };

  returnFn.lockYAxis = function(value) {
    if (!arguments.length) return _lockYAxis;
    _lockYAxis = value;
    return returnFn;
  };

  returnFn.transformName = function(value) {
    if (!arguments.length) return _transformName;
    _transformName = value;
    return returnFn;
  };

  returnFn.xScale = function(value) {
    if (!arguments.length) return _xScale;
    _xScale = value;
    return returnFn;
  };

  return returnFn;
}

return chart;
});

/* Margins and padding: 
 
|                 |                   |
|                 |                   |
|                 |                   |
|                 |                   |
    chartMargin         padding
      margin 
^size <->         ^paddedPlotSize <->
^outerSize <->    
                  ^clip()             ^plotArea()

                                      ^plotSize
                                      ^plotSpot

*/
