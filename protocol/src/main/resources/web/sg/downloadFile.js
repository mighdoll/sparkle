/** download a file by inserting a anchor element and clicking on it */
define([], 
    function() {

  return function(href, fileName, mediaType) {
    var link = document.createElement('a'); 
    document.body.appendChild(link);
    if (link.download === undefined) { 
      // IE 11 & Safari 8 don't support .download
      window.location.href = href;
    } else {
      link.href = href;
      link.download = fileName;
      // TODO this doesn't work to set the local OS file type
      // (the downloaded file on macOS chrome 36 at least is always Plain Text)
      if (mediaType) link.type = mediaType; 
      link.click();
      link.remove();
    }
  };
  
});
