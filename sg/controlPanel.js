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

define (["lib/d3", "sg/util", "sg/metadata", "sg/dropDown"], 
        function(_d3, _util, networkMetaDataApi, dropDown) {

/** A dashboard control panel.  More functionality to come! */
return function() {
  var _metaDataApi = networkMetaDataApi;

  function returnFn(container) {
    container.each(bind);
  } 

  function bind(controlPanel) {
    var update = d3.select(this),
        metaDataApi = controlPanel && controlPanel.metaDataApi || _metaDataApi,
        attached = attachByClass("div", update, "control-panel");

    var picksPromise = rootPaths(_metaDataApi);
    picksPromise.then(drawPicker).otherwise(rethrow);

    var pickerDiv = attachByClass("div", attached, "picker");

    function drawPicker(picks) {
      pickerDiv.data([picks])
        .call(dropDown());

      pickerDiv.node().dispatchEvent(new CustomEvent("initialized", {bubbles:true}));
    }
  }

/** return a promise that completes with an array containing the root path component of all 
  * data sets available on the server.  Useful for the typical dashboard case of making a UI 
  * widget to select a group of datasets.
  *
  * e.g. if the server has data sets foo/b.csv, bar/b.csv, bar/a/b.csv; then rootPaths 
  * will complete with: ["foo", "bar"].  */
  function rootPaths (metaDataApi) {    
    return metaDataApi.allDataSets().then(rootDirectories);

    /** given a list of paths, return an array of the root level directories */
    function rootDirectories(files) {
      var rootDirs = files.map(function(file) {
        var slashDex = file.indexOf("/");
        return file.slice(0, slashDex);
      });

      var nonZeroRoots = rootDirs.filter(function(file) {
        return file !== "";
      });

      var uniqueRoots = d3.set(rootDirs);
      return uniqueRoots.values();
    }
  }

  returnFn.metaDataApi = function(value) {
    if (!value) return _metaDataApi;
    _metaDataApi = value;
    return returnFn;
  };

  return returnFn;
};

});
