define(["d3"], 
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
