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

/**
  d3 utils -- remove some boilerplate when using the d3 component model.

  Note: these APIs are changing often and not yet well polished.  Consider them in-progress experiments 
  in ways to reduce boilerplate while writing sparkle-graph components.

  attachByClass() is for attaching & updating a child DOM node:
    . the manual way in d3 uses selectAll(class).data([0]) and .enter():

        var selection = d3.select(this),
            update = selection.selectAll(".foo").data([0]),      // null data unnecesssary 
            transitionUpdate = d3.transition(update),            // unnecessary distinction w/update
            enter = selected.enter();
      
        enter
          .append("g")                                           // we always append(), DRY this
            .attr("class", "foo")                                // DRY with "foo" above
            .attr("static", 1);
      
        transitionUpdate
           .attr("dynamic", x)
    
    . with attachByClass(), we save a few lines for this common idiom

        var update = d3.transition(d3.select(this)),
            attached = attachByClass("g", transition, "foo");

        attached.entered()
          .attr("static", 1);

        update
          .attr("dynamic", x);

  attachComponent() allows attaching a dom.. TBD
      ..example TBD

  bindComponents() allows attaching different components to a selection of DOM nodes
    and also binds the data.  It's experimantal, prefer attachComponent for now.
*/

function emptySelection() { return d3.selectAll([]); }

/** Select or create a dom element with the given css class.
  *
  * Safe to call on a transition or a selection container.  (in d3, you can't normally
  * append nodes on a transition selection but this routine works around that).
  *
  * returns the update selection with an additional method: .entered()
  *  .entered() returns the selection of elements attached to the container
  *  selection.
  */
function attachByClass(element, container, cssClass) {
  var selection = container.selectAll(element + "." + cssClass),
      entered;

  if (selection.empty()) {
    var notTransition = asSelection(container);
    notTransition.append(element) // note that this copies __data to the appended node.
      .attr("class", cssClass.replace(".", " "));

    selection = container.selectAll(element + "." + cssClass);
    entered = selection;
  } else {
    entered = emptySelection();
  }

  selection.entered = function() {
    return entered;
  };

  return selection;
}

/** return an array of the nodes in a selection 
 * (d3's selection.node() returns just the first node) */
function nodes(selection) {
  return d3.merge(selection);
}

/** convert a transition into a selection */
function asSelection(transition) {
  if (transition.ease === undefined) 
    return transition;

  return d3.selectAll(nodes(transition));  
}

/** select or create a dom element as the unique child of the container selection */
function attachByElement(container, element) {
  var selection = container.selectAll(element),
      entered;
      
  if (selection.empty()) {
    selection = container.append(element);
    entered = selection;
  } else {
    entered = emptySelection();
  }

  selection.entered = function() {
    return entered;
  };

  return selection;
}

/** select a dom element with a given id, creating it if necessary */
function attachById(container, element, id) {
  var selection = container.selectAll("#" + id);

  if (selection.empty()) {
    selection = container.append(element).attr("id", id);
  }

  return selection;
}

/** Attach a set of 'g' nodes and bind components and data to the nodes.
 *
 * If the DOM node doesn't exist and data is provided, create
 * a new 'g' node, bind the data to it, and attach the component.
 *
 * If the DOM node exists, update with the provided data, including
 * removing the DOM node if the data is now empty.
 *    
 * parameters:
 *   selection:String - container into which we'll place or update 'g' elements for components
 *   cssClass:String - css class to find/set for the component's 'g' element
 *   componentInfo:[ComponentInfo] - array of descriptors for the components to install
 *
 * componentInfo contains the following properties:
 *   .component:function - the instantiated d3 component to attach (or reattach) to the DOM
 *   .data:[] - the data to bind to the 'g' element for this component
 *   .position: - optional x,y translation for this component
 */
function bindComponents(selection, componentInfo, cssClass) {
  // composite the data needed by the component and the metadata to
  // setup the 'g' wrapper and attach the plugin
  var squashed = componentInfo.map (function(info) { 
    prefixedPropertyCopy(info, info.data, "__", "component", "position");
    return info.data;
  });

  selection.each(function() {
    var outerContainer = d3.select(this);

    var selected = outerContainer.selectAll("g." + cssClass).data(squashed),
        transition = d3.transition(selected),    
        enter = selected.enter(),
        exit = selected.exit();

    function optionalPosition(d) {
      if (d.__position)
        return "translate(" + d.__position[0] + "," + d.__position[1] + ")"; 
      else 
        return "translate(0,0)";
    }

    enter
      .append("g")
        .attr("class", cssClass.replace(".", " "));
          
   transition 
      .attr("transform", optionalPosition)
      .call(function(componentContainer) {     
        // potentially a different component at each node, so loop manually
        componentContainer.each(function(data) {
          var componentTransition = d3.transition(d3.select(this));
          data.__component(componentTransition);
        });
      });

    exit
      .remove();
  });
}

/** Attach a dom 'g' node with a specified css class, and attach a component
 * to the node.  
 *
 * If the 'g' node doesn't exist, the 'g' node is created and attached to the dom
 * and the the component is created and cached in a DOM property.
 * 
 * returns an object {
 *   component:Component,// component instance
 *   update:Selection,   // 'g' node to contain this component
 *   bind:Function       // call this to bind or rebind the component to the g node
 * }
 */
function attachComponent(container, componentFn, cssClass, position) {
  var update = attachByClass("g", container, cssClass);

  update.entered().each(function() {
    var node = this;
    component = componentFn();
    node.__component = component;
    if (position) {
      d3.select(node).attr("transform", "translate(" + position[0] + "," + position[1] + ")"); 
    }
  });

  var component = update.node().__component;  // avoids creating component configurator except when necessary

  function bind(data) {
    if (data) 
      update.datum(data);

    update.call(component);
  }

  return {
    bind:bind,
    component:component,
    update:update
  };
}

/** Attach a 'g' element with a specified css class to a container and optionally position the 'g' element.  
  * Return a selection containing the the 'g' element.  If the appropriate 'g' element can't be found,
  * it is created and appended to the container.
  */
function attachGroup(container, cssClass, position) {
  var selection = attachByClass("g", container, cssClass);

  if (position) {
    selection.attr("transform", "translate(" + position[0] + "," + position[1] + ")"); 
  }

  return selection;
}



/** copy properties from one object to another, prepending a prefix
 * the property names on the destination */
function prefixedPropertyCopy(src, dest, prefix, _properties) {
  for (var i = 3; i < arguments.length; i++) {
    var arg = arguments[i];
    if (arg in src)
      dest[prefix + arg] = src[arg];
  }
}

/** optionally translate a selection if the data contains a .position parameter  */
function optionalPosition(selection) {
  selection.each(function(d) {
    var elem = d3.select(this);
    if (d.position) {
      elem.attr("transform", function(d) { 
        return "translate(" + d.position[0] + "," + d.position[1] + ")"; 
      });
    }
  });
}

/** Use the bound data 1d array and a supplied function to translate 
 * the selection horizontally.  */
function translateX(selection, fn) {
  selection.attr("transform", function(d) {
    return "translate(" + fn(d) + ",0)";
  });
}

/** Use the bound data 2d array and a supplied function to translate the 
 * selection in both X and Y.  */
function translateXY(selection, fn) {
  selection.attr("transform", function(d) {
    var spot = fn(d);
    return "translate(" + spot[0] + "," + spot[1] + ")";
  });

}

/**  Report error to developer by rethrowing on a separate stack.  
 * Used as a when.otherwise() callback */
function rethrow(reason) {
  console.log(reason.stack);
  setTimeout(function() {   
    // rethrow outside of the when.promise try/catch stack
    // so that our bug triggers the debugger
    throw reason;
  }, 0);
}

/** generate a random string */
function randomAlphaNum(length) {
  var pool = "abcdefghijklmnopqurstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
      poolSize = pool.length;

  var result = [];
  for (var i = 0; i < (length || 4); i++) {
    var pick = Math.floor(Math.random() * poolSize);
    result.push(pool[pick]);
  }

  return result.join("");
}

function isString(obj) {
  return (typeof obj == 'string' || Object.prototype.toString.call(obj) == '[object String]');
}

/** Make a deep copy of an object.  Arrays are copied.  Functions in the source object 
 * are not copied, the copy will reference the same function instance.  
 *
 * Note: does not handle self-referential objects.  */
var deepClone = deepReplace;    // just don't pass a substring

function isArray(obj) {
  return Object.prototype.toString.call(obj) === '[object Array]';
}

function isDate(obj) {
  return Object.prototype.toString.call(obj) === '[object Date]';
}

function isNonEmptyArrayLike(obj) {
  try { 
    return obj.length > 0 && '0' in Object(obj);
  } catch(e) {
    return false;
  }
}

function propertyCopy(src, target, _properties) {
  for (var i = 2; i < arguments.length; i++) {
    var arg = arguments[i];
    if (arg in src)
      target[arg] = src[arg];
  }
}

function shallowCopy(target, src) {
  for (var property in src) {
    target[property] = src[property];
  }
  return target;
}

/** return a copy of an object with null or undefined properties removed */
function copyDefined(src) {
  var copy = {};
  for (var property in src) {
    var value = src[property];
    if (value != null && value != undefined) {
      copy[property] = value;
    } 
  }
  return copy;
} 

// TODO rename to merge
function copyPropertiesExcept(target, src, _notProperties) {
  var notProperties = [];
  for (var i = 2; i < arguments.length; i++) {
    notProperties.push(arguments[i]);
  } 
  for (var property in src) {
    if (notProperties.indexOf(property) < 0) {
      target[property] = src[property];
    }
  }
  return target;
}

function mergePropertiesInto(target, src) {
  return copyPropertiesExcept(target, src);
}

/** copy the properties of multiple objects into a single merged object */
function combineProperties(_obj1, _obj2, etc) {
  var result = {};
  for (var i = 0; i < arguments.length; i++) {
    mergePropertiesInto(result, arguments[i]);
  }
  return result;
}


/** Make a deepClone of an object, optionally calling string.replace on each string in the new object.
 *
 * Note: does not handle self-referential objects.  */
function deepReplace(obj, substring, replacement) {
  return copyAndReplace(obj);

  function copyAndReplace(obj) {
    if (isString(obj) && substring) {
      return obj.replace(substring, replacement);
    }

    if (obj === null || !(obj instanceof Object) || (obj instanceof Function)) { 
      return obj; 
    }

    if (isArray(obj) || isNonEmptyArrayLike(obj)) {
      return arrayClone(obj);
    } else if (isDate(obj)) {
      return new Date(obj);
    } else {
      return objectClone(obj);
    }
  }

  function arrayClone(array) {
    var copy = [],
        length = array.length;

    for (var i = 0; i < length; i++) {
      copy[i] = copyAndReplace(array[i]);
    }
    return copy;
  }

  function objectClone(obj) {
    var copy = {};
    for (var prop in obj) {
      copy[prop] = copyAndReplace(obj[prop]);
    }
    return copy;
  }
}

/** Convert a css style size string to numeric pixels.
 *  Assumes the style is e.g. '10px'. */
function styleToPixels(style) {
  if (!style) return 0;
  var pixStr = style.replace("px", "");

  return pixStr ? pixStr * 1 : 0 ;
}


/** Return an object containing the width in pixels of the border around an DOM node */
function cssPadding(node) {
  var styles = window.getComputedStyle(node),
      result = {
        top: styleToPixels(styles.getPropertyValue("padding-top")),
        left: styleToPixels(styles.getPropertyValue("padding-left")),
        bottom: styleToPixels(styles.getPropertyValue("padding-bottom")),
        right: styleToPixels(styles.getPropertyValue("padding-right"))
      };
  return result;
}

/** Return an object containing the size of the padding around a DOM node */
function cssBorderWidth(node) {
  var styles = window.getComputedStyle(node),
      result = {
        top: styleToPixels(styles.getPropertyValue("border-top-width")),
        left: styleToPixels(styles.getPropertyValue("border-left-width")),
        bottom: styleToPixels(styles.getPropertyValue("border-bottom-width")),
        right: styleToPixels(styles.getPropertyValue("border-right-width"))
      };
  return result;
}


function readOnlyApi(api, _name, _value, _name2, _value2) {
  /* jshint loopfunc:true */
  for (var i = 1; i < arguments.length;) {
    var name = arguments[i++],
        value = arguments[i++];
    api[name] = (function() {
      var capturedValue = value;
      return function() {
        return capturedValue;
      };
    })();
  }
}


/** Given a transitionOrselection, return a transition if it's still valid (not expired).
 * otherwise return the underlying selection */ 
function validTransition(transitionOrSelection) { 
  if (!transitionOrSelection.ease) {
    return transitionOrSelection; // already a selection
  }

  var transitionLock = transitionOrSelection.node().__transition__;
  if (transitionLock && transitionLock[transitionOrSelection.id]) {  
    return transitionOrSelection; // valid transition
  } else {
    return asSelection(transitionOrSelection);
  }
}

/** convert a selection to a transition */  
function toTransition(selection, modelTransition) {
  var transition;

  // consider just copying the transition internal state instead: if this was an 
  // transition containing mulitple elements, this loop would run multiple times unnecessarily
  modelTransition.each(function() {   
    transition = d3.transition(selection);
  });

  return transition;
}

/** convert [[String,Value],[String,Value]] to {name1: value1, name2: value2}.  */
function arrayToObject(array) {
  var obj = {}; 
  array.forEach(function(pair) {
    obj[pair[0]] = pair[1];
  });
  return obj;
}

/** return a copy of the array with certain elements removed */
function arrayClean(array, remove) {
  var cleaned = array.filter(function(elem) {
    return elem != remove;
  });
  return cleaned;
}

/** return an hash of the query parameters in a url */
function urlParameters(url) {
  var parameters = url.slice(1).split('&');
  var keyValues = parameters.map(function(parameter) {
    var keyValue = parameter.split('=').map(function(kv) { return decodeURIComponent(kv); });
    if (keyValue[1] === undefined) { keyValue[1] = true; }
    return keyValue;
  });

  return arrayToObject(keyValues);
}

/** parse a url string. return the hostname, port, etc. */
function parseUrl(urlString) {
  var parser = document.createElement('a');
  parser.href = urlString;
  return {
    protocol: parser.protocol,
    hostname: parser.hostname,
    port: parser.port,
    pathname: parser.pathname,
    search: parser.search,
    parameters: urlParameters(parser.search)
  };
}


