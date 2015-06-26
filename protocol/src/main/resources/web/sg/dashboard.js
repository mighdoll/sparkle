define (["when", "d3", 
         "sg/util", "sg/richAxis", "sg/data",
         "sg/zoom", "sg/chart", "sg/domCache"], 
        function(when, _d3, util, _richAxis, networkDataApi,
                 _zoom, chart, domCache) {

/** Bind to an array containing a Dashboard object.
 *
 * The dashboard component uses the provided NamedSeries objects to fetch metadata from the server 
 * (e.g. data range and domain).  Using the server data, the dashboard constructs Series objects 
 * for each NamedSeries.  
 */
function dashboard() {
  var _size = [600, 250],
      _zoomTogether = false,
      _transitionTime = 300,
      _dataApi = networkDataApi,
      _chart = chart();   

  var returnFn = function(container) {
    container.each(bind);
  };

  function bind(dashData) {
    var dashNode = this,
        selection = d3.select(this),
        charts = dashData.charts,
        update = selection.selectAll(".chart").data(charts),
        enter = update.enter(),
        exit = update.exit(),
        zoomTogether = dashData.zoomTogether || _zoomTogether,
        transitionTime = dashData.transitionTime || _transitionTime,
        dataApi = dashData.dataApi || _dataApi;

    /** setup containers for charts */
    enter.append("svg")
      .attr("class", "chart")
      .call(setSizeWithDefault, _size);

    exit.remove();

    selection.on("chartZoom", function() { 
      onZoom(dashNode, update, charts, zoomTogether, transitionTime, dataApi); 
    });
    selection.on("keydown", function() { 
      keyboardCommand(dashNode, update, charts, transitionTime, dataApi); 
    });

    saveHistory(charts);
    selection.on("resize", function() { saveHistory(charts); });
    window.onpopstate = function(event) { historyChange(event, update, charts, transitionTime, dataApi) };

    redraw(update, transitionTime, dataApi);
  }

  /** Called when the user navigates the browser back button (or foward button) */
  function historyChange(event, update, charts, transitionTime, dataApi) {
    if (!event.state || !event.state.dashState) { return; }
    var dashState = event.state.dashState;

    for (var i = 0; i < charts.length; i++) {
      charts[i].displayDomain = dashState.displayDomains[i];
      charts[i].size = dashState.sizes[i];
    }

    update.transition().duration(transitionTime)
      .call(setSize)
      .call(redraw, transitionTime, dataApi);
  }

  /** Save the dashboard state, in case the user presses the back button */
  function saveHistory(charts) {
    var domains = charts.map(function(chartData) { return deepClone(chartData.displayDomain); } ), 
        sizes = charts.map(function(chartData) { return deepClone(chartData.size); } ),
        filteredState = copyPropertiesExcept({}, window.history.state, "dashState"),
        state = deepClone(filteredState);
    
    state.dashState = {
      displayDomains: domains,
      sizes: sizes
    };
    window.history.pushState(state);
  }

    
  /** If the 'm' - match zoom, key is pressed, match the domain on all charts to
   * the domain of the last zoomed one. */
  function keyboardCommand(dashNode, update, charts, transitionTime, dataApi) { 
    if (String.fromCharCode(d3.event.keyCode) == "M") {
      var lastZoom = domCache.get(dashNode, "lastZoom");
      if (lastZoom) {
        var transition = d3.transition().duration(transitionTime);
        zoomOthers(update, charts, lastZoom.displayDomain, transition, transitionTime, dataApi); 
        saveHistory(charts);  
      }
    }
  }

  /** When one chart zooms, zoom the other charts immediately if we're
   * in zoomTogether mode. and stash a record of the new domain for
   * future zoom-matching (via the 'M' keyboard command). */
  function onZoom(dashNode, update, charts, zoomTogether, transitionTime, dataApi) {  
    var lastZoom = d3.event.detail;
    domCache.save(dashNode, "lastZoom", lastZoom);
    if (zoomTogether) {
      zoomOthers(update, charts, lastZoom.displayDomain, lastZoom.chartTransition, transitionTime, dataApi);
    }
    saveHistory(charts);  
  }

  /** zoom all the other graphs to the same domain */
  function zoomOthers(update, charts, displayDomain, matchTransition, transitionTime, dataApi) {  
    charts.forEach(function(chartData) {
      chartData.displayDomain = displayDomain;
    });
    matchTransition.each(function() {  // wrap in parent transition
      redrawExcept(update, matchTransition.node(), transitionTime, dataApi); 
    });
  };


  /** redraw all charts except one (one chart is already zoomed, we're matching the others) */
  function redrawExcept(update, exceptNode, transitionTime, dataApi) {  
    var excepted = update.filter(function() {
      return this !== exceptNode;
    });
    redraw(excepted, transitionTime, dataApi);
  }

  /** bind chart components */
  function redraw(update, transitionTime, dataApi) {
    update.each(function(chartData) {
      var chartSelection = d3.select(this);
      var chartMaker = chartData.chart || _chart;
      chartMaker
        .dataApi(dataApi)
        .transitionTime(transitionTime)
        .size(_size);   // (a .size property in the data can override this default)

      chartSelection
        .call(chartMaker);
    });
  }

  // accessors

  returnFn.size = function(value) {
    if (!value) return _size;
    _size = value;
    return returnFn;
  };

  returnFn.chart = function(value) {
    if (!value) return _chart;
    _chart = value;
    return returnFn;
  };

  returnFn.transitionTime = function(value) {
    if (!value) return _transitionTime;
    _transitionTime = value;
    return returnFn;
  };

  returnFn.zoomTogether = function(value) {
    if (!value) return _zoomTogether;
    _zoomTogether = value;
    return returnFn;
  };

  returnFn.dataApi = function(value) {
    if (!value) return _dataApi;
    _dataApi = value;
    return returnFn;
  };


  return returnFn;
}

/** Set the height and width of the selection using the bound data's .size property. */
function setSize(selection) {
  selection
    .attr("width", function(d) {return d.size[0]; })
    .attr("height", function(d) {return d.size[1]; });
}

/** Set the height and width of the selection using the bound data's .size property.
  * If the .size property isn't set in the bound data, use the provided defaultSize parameter instead */
function setSizeWithDefault(selection, defaultSize) {
  selection.each(function(d) {
    d.size = d.size || defaultSize;
  });

  setSize(selection);
}


return dashboard;

});
