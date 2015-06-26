define(["d3", "sg/util", "sg/richAxis"], 
       function(_d3, _util, richAxis) {

/** Attach a lockable axis to one side of the chart.
 *
 * Bind to an AxisGroup */
return function() {
  var _zeroLock = false,
      _lockYAxis = false,
      _color = "grey",
      _orient = "left";

  var returnFn = function(container) {
    container.each(bind); 
  };

  function bind(axisGroup, index) {
    var selection = d3.select(this),
        transition = d3.transition(selection),
        zeroLock = axisGroup.zeroLock || _zeroLock,
        lockYAxis = axisGroup.lockYAxis || _lockYAxis,
        orient = axisGroup.orient || _orient;
  
    // bind the richAxis at the right position
    var position;
    var paddingY = (axisGroup.paddedPlotSize[1] - axisGroup.plotSize[1]) / 2;
    if (orient == "left") {
      position = [axisGroup.chartMargin.left, 
                  axisGroup.chartMargin.top + paddingY];
    } else {
      position = [axisGroup.chartMargin.left + axisGroup.paddedPlotSize[0], 
                  axisGroup.chartMargin.top + paddingY];
    }
    var axisSelection = attachGroup(selection, orient + ".axis", position),
        axisNode = axisSelection.node(),
        axisTransition = toTransition(axisSelection, transition);

    var axisData = axisNode.__sideAxis || {};
    axisNode.__sideAxis = axisData;

    var axis = richAxis()     
      .displayLength(axisGroup.plotSize[1])
      .labelColor(_color)
      .orient(orient)
      .label(axisGroup.label);

    var range;
    if (lockYAxis) {
      if (!axisData.locked) {
        axisData.locked = currentRange(axisGroup.series);
      }
      range = axisData.locked;
    } else {
      axisData.locked = undefined;
      range = currentRange(axisGroup.series); 
      if (zeroLock) {
        range[0] = 0;
      }
    }
    axis.domain(range); 

    axisSelection.data([axisData]);
    axisTransition.call(axis);

    // and share the y scale and range with all series in the group
    axisGroup.series.forEach(function(series, index) {
      series.yScale = axis.scale();
      series.displayRange = range;
    });
  }

  /** return the maximum range of all series in the current loaded dataSeries.data */
  function currentRange(seria) {
    var min = seria.reduce(function(localMin, seriesB) {
      return Math.min(localMin, minData(seriesB));
    }, minData(seria[0]));

    var max = seria.reduce(function(localMax, seriesB) {
      return Math.max(localMax, maxData(seriesB));
    }, maxData(seria[0]));

    return [min, max];
  }

  /** return the smallest number in a series.data range */
  function minData(series) {
    if (!series || !series.data || !series.data.length) return 0;

    return series.data.reduce(function(prev, current) {
      return Math.min(prev, current[1]);
    }, series.data[0][1]);
  }

  /** return the largest number in a series.data range */
  function maxData(series) {
    if (!series || !series.data || !series.data.length) return 0;

    return series.data.reduce(function(prev, current) {
      return Math.max(prev, current[1]);
    }, series.data[0][1]);
  }

  returnFn.color = function(value) {
    if (!arguments.length) return _color;
    _color = value;
    return returnFn;
  };

  returnFn.zeroLock = function(value) {
    if (!arguments.length) return _zeroLock;
    _zeroLock = value;
    return returnFn;
  };

  returnFn.lockYAxis = function(value) {
    if (!arguments.length) return _lockYAxis;
    _lockYAxis = value;
    return returnFn;
  };

  returnFn.orient = function(value) {
    if (!arguments.length) return _orient;
    _orient = value;
    return returnFn;
  };


  return returnFn;
};

});

