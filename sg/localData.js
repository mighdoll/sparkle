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

define(["lib/when/when", "sg/data"], function(when, dataApi) {

  /** returns an object that supports the async data() api
   *
   * Call with a local array of data: [Date, Number] */
  return function(dataArray) {
    
    returnFn.millisToDates = dataApi.millisToDates;
    return returnFn;

    /* return data points within the domain from a local RAM array.
     * (see data.js data api)
     */
    function returnFn(setName, column, params) {
      var domain = params.domain,
          first,
          extra = params.edgeExtra;

      // Simulate DomainRange transform if requested
      if (params.transform == "DomainRange") {
        var d = minMax(dataArray, 0);
        var r = minMax(dataArray, 1);
        
        return when.resolve(
            [ [["domain",d],["range",r]] ]
        );
      } 
      
      dataArray.every( function(datum, index) {
        var dataTime = datum[0];
        if (dataTime < domain[0]) {
          return true;
        } else {
          first = index;
          return false;
        }
      });
      var start = first ? (extra ? first - 1 : first) : 0;

      var last = 0;
      for (var i = dataArray.length - 1; i >= start; i--) {
        var dataTime = dataArray[i][0];
        if (dataTime <= domain[1]) {
          last = i;
          break;
        }
      }
      var endDex = (last + 1 < dataArray.length) ? (extra ? last + 1 : last) : last;

      var result = dataArray.slice(start, endDex + 1);
      return when.resolve(result);
    }

    /**
     * Return a 2 value array of the min & max of a column from a 2D array.
     * @param array An array of 2 arrays
     * @param index The 'column' of the array to find the min & max for.
     * @returns {*[]}
     */
    function minMax(array, index) {
      return [
        reduce(array, Math.min, index),
        reduce(array, Math.max, index)
      ];
    }

    function reduce(array, reducer, index) {
      return array.reduce(function(prev, current) {
        return reducer(prev, current[index]);
      }, array[0][index]);
    }

  };
});
