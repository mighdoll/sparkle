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

define(["lib/when/monitor/console", "sg/request"], function(_console, request) {

/** Fetch list of all data sets from the server.  
  * Returns a When that contains an array of strings e.g. ["foo/b.csv", "bar/c.csv"] */
function allDataSets() {
  var uri = "/info";    
  
  return request.jsonWhen(uri);
}

/** fetch column meta data from the server.
 * Returns a when that completes with an object of the form: 
 * {
 *  .?domain:[Number,Number]   -- time covered by data in this column. in milliseconds since the epoch
 *  .?range:[Number,Number]    -- min, max of any data element in this column.
 *  .?units:String             -- units of the data in this column
 * }
 */
function columnMetaData(setName, column, uniques) {
   var uniquesParam = uniques ? "?uniques=true" : "",
       uri = "/column/" + column + "/" + setName + uniquesParam;    

   return request.jsonWhen(uri);
}

return {
  column:columnMetaData,
  allDataSets:allDataSets
};


});
