require(["lib/d3", "sg/downloadFile"], function (_, downloadFile) {

  /** bind script actions to UI elements */
  function bindToUI() {
    var form = d3.select("#DownloadFolderForm");
    form.on("submit", function() {
      d3.event.preventDefault();
      var folderName = d3.select("#DownloadFolderInput").property("value");
      var fileName = lastComponent(folderName) + ".tsv";
      downloadFile("/fetch/" + folderName, fileName, "text/tab-separated-values");
    });

  }

  function lastComponent(path) {
    var last = path.lastIndexOf("/"); 
    if (last >= 0) {
      return path.substring(last + 1);
    } else {
      return path;
    }
  }

  bindToUI();

});
