---
layout: default
title: Contributing to sg.js
---

### Contributions to sg.js and the sparkle dashboard are welcomed

1. The existing code is Apache 2 licensed, contributions must be Apache 2 or BSD/MIT licensed. 
1. Make components independently testable, and add unit test coverage. 
1. Use requirejs to modularize.
1. Use a consistent style with the rest of the code base:

    * Descriptive variable names. 
    * Short functions.  
    * Brief comments on every function.  
    * 110 characters per line, 2 space indenting, no tabs.
1. Resist introducing more library dependencies into the core charting library.
  The dashboard uses angular and angular components.
1. Review the documentation for support libraries for the core sg.js charting library.

    * [sparkle-graph component-model](component-model.html) 
    * [d3 api](https://github.com/mbostock/d3/wiki/API-Reference)
    * [require.js](http://requirejs.org/docs/api.html)
    * [when.js](https://github.com/cujojs/when/blob/master/docs/api.md#api)
    * [jasmine](http://jasmine.github.io/)

1. Review the documentation for additional libraries used in the dashboard.

    * [angular](https://angularjs.org/)
    * [angular material](https://material.angularjs.org)
