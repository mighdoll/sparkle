Sparkle
=======

**This is a pre-release version of sparkle.  The documentation is wrong, the API will change.**

Introduction
-------
**Sparkle** is a visualization suite interactive dashboards from time series data.  

Sparkle gives users an easy way to make a zoomable graphs of data from a directory of .csv files. The graphs are interactively zoomable in a d3 based web interface. Graphs can be aggregated into dashboards, and customized with simple declarative javascript.

[coming soon: links to example charts and dashboards]

Components
------
Sparkle contains three core components:

###Sparkle-Time Server 
**sparkle-time** is a mini web server that provides HTTP and websocket apis for data visualization. Sparkle-time collects data from an extensible set of inputs (Apache Kafka and .csv/.tsv data files, hadoop and netcat support coming soon). Sparkle-time stores data in Cassandra.  Sparkle-time also hosts an extensible set of data transformations that transform data on demand (downsampling for example).

Sparkle-time can be run as a library inside another server, or it can run standalone if no customization is needed.

###sg.js 
**sg.js** is a d3.js based javascript library for displaying interactive charts of potentially large data sets.  Charts are customizable with declarative javascript, and extensible with d3. sg.js currently offers support for line charts, scatter plots, bar charts, etc. and is easily extensible. All charts support a drag-based zooming UI. 

sg.js also includes a dashboard component, for constructing pages that aggregate many charts on the same page. Graphs in the dashboard are resizable. The browser's back button works to undo/redo zoom navigation and chart resizing.

sg.js supports the sparkle-time api, enabling zooming charts of potentially huge server hosted data sets, the option of offloading data transformations to the server, and (soon) server pushed updates to locally displayed charts.

(sg.js can be used as standalone charting library.) 

###Sparkle Data API
To enable interoperation of multiple clients and servers, the sparkle-time server and sg.js javascript library speak a well-defined data protocol: the Sparkle Data Protocol. The protocol allows clients to request data transformations and data streams from a comformant server.

See [Sparkle Data Protocol](https://docs.google.com/document/d/1OvRxFbTzjuLSh7J3NXEM3jNQKxCCiBEfKr5fE6EeBJk/pub) for details of the protocol. See [Sparkle Transforms](https://docs.google.com/document/d/1rz_7otdjla5d9990zdvM6Uev-5c_jqbZhepyLIKQO6U/pub) for the current set of built in transforms.


Plans
-------
Sparkle is being revised to:
* clean out legacy code 
* fully support a websocket based api between visualization clients and the server
* include a pre-built 'sg' launcher 
* a sample dashboard for streams

More ideas in the [todo list](https://github.com/mighdoll/sparkle/blob/master/ToDo)

Using sparkle 
-------
See [Using-sg](https://github.com/mighdoll/sparkle/blob/master/Using-sg.md)

Contributing to Sparkle 
-------
See [Contributing](https://github.com/mighdoll/sparkle/blob/master/contributing.md), [Building](https://github.com/mighdoll/sparkle/blob/master/Building.md), and
 [javascript component model](https://github.com/mighdoll/sparkle/blob/master/component-model.md).


