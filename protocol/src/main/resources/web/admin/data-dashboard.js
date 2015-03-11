require(["lib/d3", "sg/downloadFile",  "sg/dashboard", "sg/sideAxis", "sg/palette",
         "sg/scatter", "sg/data", "sg/util", "admin/chartControlPanel", "admin/uploadFile"],

function (_d3, downloadFile, dashboard, sideAxis, palette, scatter, data, _util, _panel, _upload) {

  var board = dashboard().size([600,400]);
  var charts = [  // TODO allow most of this stuff to be edited from the UI
    { title: "chart",
      timeSeries: true,
      showXAxis: true,
      margin: { top: 20, right: 50, bottom: 50, left: 75 },
      transformName: "reduceMax",
      padding:[5, 5],    // padding so that marks can extend past the edge of the plot area
      groups: [
        { axis: sideAxis(),
          named: []
        }
      ]
    }
  ];

  /** bind script actions to UI elements */
  function bindToUI() {
    var form = d3.select("#ColumnOrEntityForm");
    var button = d3.select("#ColumnOrEntityForm button");
    button.on("click", function() {
      d3.event.preventDefault();
      var enteredValue = d3.select("#ColumnOrEntityInput").property("value");
      if (!d3.event.ctrlKey) {
        addToPlot(enteredValue);
      } else {
        download(enteredValue);
      }
    });

    form.on("submit", function() {
      d3.event.preventDefault();
      var enteredValue = d3.select("#ColumnOrEntityInput").property("value");
      addToPlot(enteredValue);
    });
  }

  /** download an entity as a .tsv file */
  function download(enteredValue) {
    var fileName = lastComponent(enteredValue) + ".tsv";
    downloadFile("/fetch/" + folderName, fileName, "text/tab-separated-values");
  }

  /** add selected column to the current plot */
  function addToPlot(enteredValue) {
    // for now, we just add columns to the first group
    charts[0].groups[0].named.push({name: enteredValue});
    draw();
  }

  /** return the last part of a columnPath. (for choosing the name of the file to download) */
  function lastComponent(path) {
    var last = path.lastIndexOf("/"); 
    if (last >= 0) {
      return path.substring(last + 1);
    } else {
      return path;
    }
  }

  function draw() {
    var update = d3.select("#charts").data([{charts:charts}]);
    board(update);
  }

  bindToUI();
  draw();

});
