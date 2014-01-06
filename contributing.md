### Working on sparkle-graph

1. sg.js and sparkle-graph server are apache licensed, contributions must be apache or BSD/MIT licensed.  
1. sg.js users should not be required to use jquery, dojo, angular, backbone etc.  Don't introduce other 
  dependencies beyond d3.js, when.js, and require.js in the core.  (We'd consider mature javascript libraries 
  for special situations: e.g. for an isolated component, e.g. using jquery/select2 for a picker component).
1. Make components independently testable, and add unit test coverage. 
1. Use requirejs to modularize.
1. Use a consistent style with the rest of the code base (at least the good parts of the rest of the code base..):
  1. Descriptive variable names. 
  1. Short functions.  
  1. Brief comments on every function.  
  1. 110 character lines max, 2 space indenting, no tabs.
1. Review the documentation for: the [sparkle-graph component-model](component-mode.md) documentation, the [d3 api](https://github.com/mbostock/d3/wiki/API-Reference), [require.js](http://requirejs.org/docs/api.html), and [when.js](https://github.com/cujojs/when/blob/master/docs/api.md#api).
