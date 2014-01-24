Sparkle
=======

**This is a pre-release version of sparkle.  The documentation is wrong, the API will change.**

Introduction
-------
**Sparkle** is a visualization suite interactive dashboards from time series data.  

In its current version, Sparkle gives users an easy way to make a zoomable graphs of data from a directory of .csv files.  The resulting graphs are interactively zoomable in an attractive d3 based web interface.  Graphs can be aggregated into dashboards, and customized with simple declarative javascript.

[links to example zooming charts and dashboards]

Components
------
Sparkle contains two core components:

###Sparkle-Time Server 
**sparkle-time** is a mini web server that provides an HTTP api for visualizations.  It is backed by a directory containing .csv/.tsv time series data files (cassandra storage coming soon).  Sparkle-time is separated into layers - it designed to be included as a library in other servers, although it can run standalone.

###sg.js 
**sg.js** is a d3.js based javascript library for displaying interactive charts of potentially large data sets.  sg.js can be used as standalone charting library independently from sparkle-time, it ooffers support for line charts, scatter plots, and bar charts.  Charts are customizable with declarative javascript, and extensible with d3.  Unlike most charting libraries, all charts support a drag-based zooming UI. 

sg.js also includes a dashboard component, for constructing pages that aggregate many charts on the same page.  Graphs in the dashboard are resizable.  The browser's back button works to undo/redo zoom navigation and chart resizing.

sg.js supports the sparkle-time api, enabling zooming charts of potentially huge server hosted data sets, the option of offloading data transformations to the server, and (soon) server pushed updates to locally displayed charts.

Plans
-------
Sparkle is being revised to:
* use a websocket based api between visualization clients and the server
* support multiple storage backends (file system, cassandra, kafka)
* allow for easy application specific extensions of data transformations
* include a pre-built 'sg' launcher and a sample dashboard for streams

More details in the [todo list](https://github.com/mighdoll/sparkle/blob/master/ToDo)

Using sparkle 
-------
See [Using-sg](https://github.com/mighdoll/sparkle/blob/master/Using-sg.md)

Contributing to Sparkle 
-------
See [Contributing](https://github.com/mighdoll/sparkle/blob/master/contributing.md), [Building](https://github.com/mighdoll/sparkle/blob/master/Building.md), and
 [javascript component model](https://github.com/mighdoll/sparkle/blob/master/component-model.md).


