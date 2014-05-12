require( ["lib/d3", "sg/dashboard", "sg/sideAxis", "sg/palette", "sg/linePlot" ], 
    function(_d3, dashboard, sideAxis, palette, linePlot) {

  var charts = [ 
    { title: "Interactive Plot",
      groups: [
        { label: "labelLeft",
          axis: sideAxis(),
          named: [ 
            { name: "plot/test1" }
          ]
        }, 
      ]
    }
  ];

  var mohsBoard = dashboard().size([800, 400]), 
      update = d3.selectAll("body").data([{charts:charts}]);

  mohsBoard(update);

});
