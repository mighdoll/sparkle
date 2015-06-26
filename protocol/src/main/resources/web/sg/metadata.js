define(["sg/request"], function(request) {

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
