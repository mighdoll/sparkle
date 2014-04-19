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

define(["lib/d3", "sg/scatterLine", "sg/palette", "sg/util"], 
       function(_d3, scatterLine, palette, _util) {

/** Divide a Series into categories and then plot each category with 
  * the configured plotter, scatterLine by default.
  * 
  * Bind to a Series object
  */
return function() {
  var _plot = {plotter: scatterLine()},
      _colors = palette.orangeBlue4(),
      _labelMargin = [-30, 5],
      _categoryMargin = [0, 5],
      labelHeight = 15,   // LATER size these by drawing some text off-screen..
      labelDescent = 3;
      
  var returnFn = function(container) {
    container.each(bind); 
  };

  function bind(series) {
    var selection = d3.select(this),
        subplot = series.plot.plot || _plot,
        categories = groupByCategory([series], subplot),
        update = selection.selectAll(".category").data(categories),
        enter = update.enter(),
        exit = update.exit(),
        markHeight = subplot.plotter.layoutHeight(),     
        categoryMargin = series.categoryMargin || _categoryMargin,
        labelMargin = series.labelMargin || _labelMargin,
        colors = series.colors || _colors,
        slotHeight = markHeight + categoryMargin[1] + labelHeight + labelMargin[1],
        plotHeight = series.plotSize[1],
        slotsTop = plotHeight - (slotHeight * categories.length),
        labelLayer = series.labelLayer;

    this.__categorized = this.__categorized || {id:"i"+randomAlphaNum(6)};
    var id = this.__categorized.id;

    assignColor(categories, colors);

    enter
      .append("g")
      .classed("category", true);

    update
      .attr("transform", function(_, index) { 
              return "translate(0," + positionY(index) + ")"; 
           });

    d3.transition(update)
      .call(subplot.plotter); 

    exit
      .remove();

    // draw labels in layer so that they'll not be clipped by the plotArea
    var labelCategories = labelLayer.selectAll(".category."+id).data(categories);

    
    labelCategories.enter()
      .append("g")
      .classed("category " + id, true)
        .append("text")
          .classed("label", true);

    labelCategories.select("text.label")
      .style("fill", function(d) {return d.color;}) 
      .attr("transform", function(d, index) {
         var labelY = positionY(index) - labelDescent - labelMargin[1];
         var labelX = labelMargin[0];
         return "translate(" + labelX + "," + labelY + ")";
      })
      .text(function(d) {return d.name;});

    labelCategories.exit().remove();

    function positionY(index) {
      return slotsTop + (slotHeight * index);
    }
  }

  function assignColor(groups, colors) {
    groups.forEach(function(category, index) {
      category.color = colors(index);
    });
  }

  /** return the value in a scatterLine object, translated by the valueCodes */
  function scatterLineValue(scatterLine) {
    return decodeValue(scatterLine.series, scatterLine.value);
  }

  /** return a value translated by the valueCodes if any */
  function decodeValue(series, value) {
    var codes = series._codeMap;
    if (!codes) {
      return value;
    } else {
      return codes.get(value);
    }
  }

  /** Partition the data arrays into groups by the unique values in the data 
    * Return an array of Category objects, one for each unique value.  */
  function groupByCategory(seria, subplot) {
    // create categories array with metadata (but no data points yet)
    var byCategory = [];
    seria.forEach(function(series) { 
      series.categories.forEach(function(categoryName) {
        if (!findCategory(categoryName)) {
          var plot = series.plot.plot && series.plot.plot.plot; // umm
          byCategory.push(
            { series:series,
              name:categoryName,
              plot:plot, 
              data:[]
            }
          );
        }
      });
    });

    // sort desired data into categories
    seria.forEach(function(series) {
      var categories = series.categories;
      series.data.forEach(function(pair) {
        var time = pair[0],
            value = pair[1],
            decodedValue = decodeValue(series, value),
            category = findCategory(decodedValue);

        if (category) {
          category.data.push(time);
        } 
        
      });
    });

    return byCategory;

    /** return scatterLine from the byCategory array, or undefined if it isn't found */
    function findCategory(categoryName) {
      var found;

      byCategory.some(function(category, index) { 
        if (category.name === categoryName) {
          found = category;
          return true;
        }
        return false;
      });

      return found;
    }
  }

  returnFn.colors = function(value) {
    if (!arguments.length) return _colors;
    _colors = value;
    return returnFn;
  };

  returnFn.categoryMargin = function(value) {
    if (!arguments.length) return _categoryMargin;
    _categoryMargin = value;
    return returnFn;
  };

  returnFn.labelMargin = function(value) {
    if (!arguments.length) return _labelMargin;
    _labelMargin = value;
    return returnFn;
  };

  returnFn.plot = function(value) {
    if (!arguments.length) return _plot;
    _plot = value;
    return returnFn;
  };

  returnFn.plotter = function(value) {
    if (!arguments.length) return _plot && _plot.plotter;
    _plot = {plotter: value};
    return returnFn;
  };

  returnFn.needsCategories = function() { return true; };

  return returnFn;
};

});

