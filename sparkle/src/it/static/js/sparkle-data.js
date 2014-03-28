var Sparkle = {};

(function() {
    "use strict";
    
    /**
     * Get the full columnPaths for named DataSet 
     * @param dataSetName
     */
    function getDataSetColumns(dataSetName) {
        var url = "/v1/columns/" + dataSetName;
        var settings = {
            type: "GET",
            dataType: "json"
        }
        return $.ajax(url, settings);
    }
    
    Sparkle.Data = {
        getDataSetColumns: getDataSetColumns
    };
}());