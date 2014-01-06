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

define(["sg/zoom", "test/fixture", "test/simulateDrag"], 
  function(zoom, fixture, simulateDrag) {

describe("zoom", function() {
  var zoomer, svg, group;

  var size = [200, 50],
      zoomTransitionTime = 10;

  beforeEach(function() {
    var xScale = d3.scale.linear()
        .domain([0, 100])
        .range([0, size[0]]);

    zoomer = zoom()
      .height(size[1])
      .xScale(xScale);

    svg = fixture.svg();

    group = svg.append("g");
    group.append("rect")
     .attr("width", size[0])
     .attr("height", size[1])
     .attr("fill", "yellow");
  });

  afterEach(function() {
    svg.remove();
  });

  /* uncomment for debugging 
  it("debugging", function(done) {
    zoomer(group);
  });
  */

  it("should generate a zoomBrush event when dragged", function(done) {
    group.on("zoomBrush", function() {
      var brushEvent = d3.event.detail;
      expect(brushEvent.extent[0]).toEqual(50);
      expect(brushEvent.extent[1]).toEqual(75);
      done();
    });
        
    zoomer(group);
    simulateDrag(group, [100,25], [50,0]);
  });

  it("should do a zoom animation when called on a transition", function(done) {
    var transition = group.transition().duration(zoomTransitionTime);

    // we'll be done when the zoomBrush transition fires
    group.on("zoomBrush", function() {
      if (d3.event.detail.transitionEnd) {
        expect(zoomer.extent()).toEqual([0,0]);
        done();
      }
    });

    // pretend we have a brush that has a dragged out a region
    zoomer(group);
    zoomer.extent([50,75]);   
    zoomer(group);

    zoomer(transition);
  });


});
});
