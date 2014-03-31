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

define(["jslib/when/monitor/console", "jslib/when/when", "jslib/d3"], 
    function(_console, when, _d3) {


/** wrapper around d3.json that returns a when.js promise */
function jsonWhen(request) {
  var deferred = when.defer(),
      promise = deferred.promise;

  d3.json(request, received);

  function received(err, json) {
    if (err && err.status != 200) {
      deferred.reject(err);
    } else {
      deferred.resolve(json);
    }
  }

  return promise;
}


/** Make a POST request and returns a when.js promise */
function jsonPost(url, json) {
  var deferred = when.defer(),
      promise = deferred.promise;

  var xhr = d3.xhr(url, "application/json");
  xhr.header("Content-Type", "application/json");
  xhr.post(json, received); 

  function received(err, response) {
    if (err && err.status != 200) {
      deferred.reject(err);
    } else {
      deferred.resolve(response.response);
    }
  }

  return promise;
}

/** concatenate some "name=value" strings and return a list of query parameters.  Skip undefined strings. */
function queryParams() {
  if (arguments.length === 0) return "";

  var args = Array.prototype.slice.call(arguments);
  var realArgs = args.filter( function(arg) {if (arg !== undefined && arg !== null) return true; });
  if (realArgs.length == 1) {
    return "?" + realArgs[0];
  } else {
    return "?" + realArgs.reduce( function(msg, arg) { return msg + "&" + arg; });
  }
}

return {
  jsonWhen:jsonWhen,
  jsonPost:jsonPost,
  queryParams:queryParams
};


});
