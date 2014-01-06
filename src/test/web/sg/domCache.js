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

define(["jslib/d3"], 
  function(_) {
  
  /** Cache some private data in a javascript object e.g. a DOM node.  
   * Returns the previously cached data.  If no previous data was cached, returns newData.  */
  function save(node, name, newData) {
    var data = get(node, name);
    if (data === undefined) {
      data = newData;
    }

    put(node, name, newData); 

    return data;
  }

  /** store the data */
  function saveIfEmpty(node, name, newDataFn) {
    var data = get(node, name);
    if (data === undefined) {
      data = newDataFn();
      put(node, name, data);
    }
    return data;
  }


  /** get the cached private data, return undefined if none available */
  function get(node, name) {
    return node[privateProperty(name)];
  }

  /** store the data */
  function put(node, name, newData) {
    node[privateProperty(name)] = newData;
  }

  function privateProperty(name) {
    return "__" + name; 
  }


  return {
    save:save,
    saveIfEmpty:saveIfEmpty,
    get:get
  };

});
