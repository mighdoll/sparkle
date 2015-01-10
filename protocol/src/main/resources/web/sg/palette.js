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

define (["lib/d3"], function(_) {

var palette = {
  category10       : d3.scale.category10,
  category20       : d3.scale.category20,
  category20b      : d3.scale.category20b,
  category20c      : d3.scale.category20c,
  purpleBlueGreen3 : colorsMaker(["#1c9099", "#a6bddb", "#ece2f0"]),
  orangeBlue4      : colorsMaker(["#ffb400", "#4512ae", "#06799f", "#a67500"]),
  orangePurple3    : colorsMaker(["#ffa400", "#5e0dac", "#a66b00"]),
  orangeBrown3     : colorsMaker(["#ffbb40", "#a66b00", "#ffa400"]),
  orange4          : colorsMaker(["#ff6a00", "#ff9700", "#ffb800", "#ffb800"]),
  alarmingRed5     : colorsMaker(
                      ["#F92200", "#b02c41", "#f95400", "#a21600", "#fc7f3f"]),
  mediumMix20      : colorsMaker(
                      ["#bd0000","#fcbd00","#8aaf22","#00aeed","#6e2f9e",
                       "#7d7d7d","#f59547","#bd4d4b","#4e81ba","#99b858",
                       "#7f64a1","#4bacc4","#f59547","#8aa9cf","#dcb12d",
                       "#3f9f9f","#92cf51","#f05e27","#222222"
                      ])
};

function colorsMaker(colors) {
  return function() {
    return d3.scale.ordinal().range(colors);
  };
}

return palette;

});
