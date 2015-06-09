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

define(["lib/d3", "sg/util", "sg/richAxis", "sg/palette"], 
       function(_d3, _util, richAxis, palette) {

/** Attach a lockable axis to one side of the chart.
 *
 * Bind to an AxisGroup */
return function() {
  var _colors = palette.purpleBlueGreen3(),
      _fillColors = palette.purpleBlueGreen3Pastel(),
      _zeroLock = false,
      _lockYAxis = false,
      _orient = "left";

  var returnFn = function(container) {
    container.each(bind); 
  };

  function bind(axisGroup, index) {
    var selection = d3.select(this),
        transition = d3.transition(selection),
        zeroLock = axisGroup.zeroLock || _zeroLock,
        lockYAxis = axisGroup.lockYAxis || _lockYAxis,
        colors = axisGroup.colors || _colors,
        fillColors = axisGroup.fillColors || _fillColors,
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
      .labelColor(colors(0))
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

    // assign a color, and share the scale and range
    axisGroup.series.forEach(function(series, index) {
      series.color = colors(index);
      series.fillColor = fillColors(index);
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

  returnFn.colors = function(value) {
    if (!arguments.length) return _colors;
    _colors = value;
    return returnFn;
  };

  returnFn.fillColors = function(value) {
    if (!arguments.length) return _fillColors;
    _fillColors = value;
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

