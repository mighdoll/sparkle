require( ["sg/dashboard", "sg/sideAxis", "sg/bar", "sg/palette"], 
  function(dashboard, sideAxis, bar, palette) {

  var bars = bar();

  var charts = [ { 
    title: "ssl connections and sessions offered",
    styles: "show-bottom-axis-line",
    padding:[20, 0],                
    groups: [ { 
      label: "packets",
      axis: sideAxis(),
      zeroLock: true,
      plot: {
        plotter: bars,
        color: palette.category10(),
      },
      named: [ 
        { name: "client-a-no-session.csv/time",
          label: "client-a-no-session",
          summary: "count"
        }, 
        { name: "client-a-with-session.csv/time",
          label: "client-a-with-session",
          summary: "count"
        } 
      ],
    } ]
  }, 

     /* 
      {
    title: "ssl connections and sessions offered",
    styles: "show-bottom-axis-line",
    padding:[20, 0],                
    groups: [ { 
      label: "packets",
      axis: sideAxis(),
      zeroLock: true,
      plot: {
        plotter: groupBars,
        color: palette.category20(),
        named: [ 
          { name: "client-b-no-session.csv/time",
            label: "client-b-no-session",
            summary: "count"
          }, 
          { name: "client-b-with-session.csv/time",
            label: "client-b-with-session",
            summary: "count"
          } 
        ]
      },
    } ]
  } 
  */
  ];

  var board = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  board(update);

});
