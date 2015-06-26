define (["d3"], function(_) {

var palette = {
                // TODO make fourth color a bit darker
  set1: ["#4457CC", "#808399", "#6FCEFF", "#FFD0AF", "#CC7C60"],
  set2: ["#AA5A39", "#AA7A39", "2A4F6E", "#277455"]
};

palette.set1.lighter = ["#7784DA", "#A7A9B9", "#CCEEFF", "#FFD0AF", "#DFAD9B"];
palette.set2.lighter = ["#FFC3AA", "#FFDBAA", "#718DA5", "#74AE96"];

return palette;

});
