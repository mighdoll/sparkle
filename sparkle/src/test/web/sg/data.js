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

define(["jslib/when/monitor/console", "sg/request"], 
    function(_console, request) {

/** Fetch data points from the server.  Return a When that completes with an array:[Date, Number].
  *
  * params {
  *   ?.domain:[Date, Date] -- time range to fetch
  *   ?.extraBeforeAfter:Boolean -- server should return an extra point before and after the domain
  *   ?.approxMaxPoints:Number -- server should return about this many points
  *   ?.summary:String  -- server summarization function to use
  *   ?.filter:[String] -- only return points with these values
  */
return function(setName, column, params) {
  var uri = "/data/" + column + "/" + setName + constructQueryParams();

  return request.jsonWhen(uri).then(reformat);

  /** convert to [Date,Number] format */
  function reformat(jsonArray) {
    var data = jsonArray.map( function (row) { 
      var time = new Date(row[0]);
      return [time, row[1]]; 
    });
    return data;
  }

  /** Use params parameter block to construct url query parmeters for /data request */
  function constructQueryParams() {
    if (!params) return "";

    var start = params.domain ? "start=" + params.domain[0].getTime() : null,
        end = params.domain ? "end=" + params.domain[1].getTime() : null,
        max = (
            params.approxMaxPoints !== undefined && params.approxMaxPoints !== null
          ) ? "max=" + params.approxMaxPoints : null,
        edge = params.extraBeforeAfter ? "edge=" + params.extraBeforeAfter : null,
        summary = params.summary ? "summary=" + params.summary : null,
        filter = params.filter ? "filter=" + filterList() : null;

    return request.queryParams(start, end, max, edge, summary, filter);
  }

  function filterList() {
    var list = params.filter.reduce(function(total, elem) {return total + "," + elem;});
    return encodeURIComponent(list);
  }
}

});
