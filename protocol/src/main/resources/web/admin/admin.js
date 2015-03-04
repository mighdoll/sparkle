require(["lib/d3", "sg/downloadFile",  "sg/dashboard", "sg/sideAxis", "sg/palette",
         "sg/scatter", "sg/data", "sg/util" ],

function (_, downloadFile, dashboard, sideAxis, palette, scatter, data, _util) {
  var board = dashboard().size([900,600]);
  var charts = [  // TODO allow most of this stuff to be edited from the UI
    { title: "untitled",
      timeSeries: true,
      xScale: d3.time.scale.utc(),
      showXAxis: true,
      margin: { top: 20, right: 50, bottom: 50, left: 75 },
      transformName: "reduceMax",
      padding:[5, 5],    // padding so that marks can extend past the edge of the plot area
      groups: [
        { plot: { plotter: scatter() },
          axis: sideAxis(),
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
      var enteredValue = d3.select("#ColumnOrEntityInput").property("value");
      d3.event.preventDefault();
      if (!d3.event.ctrlKey) {
        addToPlot(enteredValue);
      } else {
        download(enteredValue);
      }
    });

    form.on("submit", function() {
      d3.event.preventDefault();
      addToPlot();
    });
  }

  /** download an entity as a .tsv file */
  function download(enteredValue) {
    var fileName = lastComponent(enteredValue) + ".tsv";
    downloadFile("/fetch/" + folderName, fileName, "text/tab-separated-values");
  }

  /** add selected column to the current plot */
  function addToPlot(enteredValue) {
    console.log("plot");

    charts[0].groups[0].named.push({name: enteredValue});
    draw();
  }

  //      <select> <option name="foo">one</option> <option name="two">two </option> </select>


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

});
