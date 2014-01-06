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

define(["jslib/when/when"], function(when) {

/** Return an object that supports the metadata api 
 * metaData is an object of the form {
 *   compositeName:DataArray
 * }
 * where compositeName is a string of the form: "dataSetName/columnName"
 * and DataArray is an array containing [Date,Number] array elements.
 */
return function(metaData) {
  /** See metadata.js */
  function allDataSets() {
    var sets = Object.keys(metaData).map(function(composite) {
      return composite.split("/")[0];
    });
    return when.resolve(sets);
  }

  /** See metadata.js */
  function columnMetaData(setName, column, uniques) {
    console.assert(!uniques, "uniques NYI");
    var data = metaData[setName + "/" + column],
        domain = minMax(data, 0),
        range = minMax(data, 1);

    return when.resolve({
      domain:domain,
      range:range
    });
  }

  function reduce(array, reducer, index) {
    return array.reduce(function(prev, current) {
      return reducer(prev, current[index]);
    }, array[0][index]);
  }

  function minMax(array, index) {
    return [
      reduce(array, Math.min, index),
      reduce(array, Math.max, index)
    ];
  }

  return {
    column:columnMetaData,
    allDataSets:allDataSets
  };

};


});

