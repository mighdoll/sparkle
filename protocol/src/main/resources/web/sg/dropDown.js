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

requirejs.config({
  paths:{
    'select2': 'http://ivaynberg.github.com/select2/select2-3.2/select2',
    'jquery': 'lib/jquery'
  },
  shim: {
    'select2': ['jquery']
  }
});

//define(["jquery", "select2", "sg/util"],  // TODO we saw some problems loading select2, temporarily disabled
define(["lib/jquery", "sg/util"], 
       function (_jquery, _util, _select2) {

/** create a select2 based dropdown menu */
return function() {
  var _intro = "";

  var returnFn = function(container) {
    container.each(bind);
  };

  /** Create a Select2 backed selector.  
    * Bound data should be an array of strings.   */
  function bind(data) {
    var selection = d3.select(this),
        pickerUpdate = selection.selectAll("select.drop-down").data([0]),
        pickerEnter = pickerUpdate.enter();

    var pickerEntered = 
      pickerEnter
        .append("span")   
        .append("select")
          .classed("drop-down", true);

    var picksUpdate = selection.select("select").selectAll("option").data(data),
        picksEnter = picksUpdate.enter(),
        picksExit = picksUpdate.exit();

    picksEnter
      .append("option");

    picksUpdate
      .text(function(d) { return d; });

    picksExit
      .remove();

    pickerEntered 
      .call( function() {
          // called when the select element is created, after the option elements are added
         $(this.node())
//           .select2()
           .change(forwardChanged);   // need to bind w/jquery, it's a jquery event, not a DOM event 
      });

    /** Re-emit the changed event as a DOM event, since select2 swallows the DOM event
      * and only emits a jquery event.  The event is emitted from the parent span 
      * (created above) not the selection to avoid looping on the change event. */
    function forwardChanged(m) {
      var event = document.createEvent("HTMLEvents");
      event.initEvent("change", true, true);
      this.parentNode.dispatchEvent(event);
    }

  }


  return returnFn;
};

});
