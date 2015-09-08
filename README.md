Sparkle
=======

This is the 0.5.x version of Sparkle. 

Introduction
-------
**Sparkle** is a small suite of libraries for making interactive data visualizations. 
Sparkle includes a javascript client library and a scala/jvm server library.
The client and server are connected by websockets protocol for requesting and delivering data for visualization. 

As a standalone tool, sparkle gives users an easy way to make visualize data from a directory of .csv files. 
The graphs are interactively zoomable in a d3 based web interface. 
With some easy customization, graphs can be aggregated into dashboards, and customized with simple declarative javascript, and data read in from other sources such as Apache Kafka.

See [Sparkle Intro Talk](https://docs.google.com/presentation/d/1j704Lcj7HhL1O6K2sOYdQeDyhQ_porkbur7UOkKeD50) for an introduction presentation.
See [Visualize the Things](https://docs.google.com/presentation/d/1YGeO2FEvdjGgSRihxU5HdEDdktBbv7mfYDtVAxW0FKU) for a brief introduction to sparkle, lessons on garbage collection with reative streams, and comments on the future of the Lambda Architecture.
See [The Live Layer](https://docs.google.com/presentation/d/16onrz3i4aUORHxnKwdbX9mk9UgG9JJyq6eO4qDeZSn0) for a discussion of the Live Layer, 
a key component missing from the Lambda Architecture.
See [Sparkle Google Docs Folder](https://drive.google.com/folderview?id=0B6uZet2ug3aKfm9KRTdXcFZUc3o2UnFyU3FscWk2T2pNazdoR1AzMlZiU3lLRXFILXJHdlU) for other docs.

Components
------
Sparkle contains three core components:

###Sparkle Data Server 
**sparkle-data-server** is a mini web server that provides HTTP and websocket apis for data visualization. 
Sparkle-data-server collects data from an extensible set of inputs (Apache Kafka and .csv/.tsv data files, hadoop and netcat support coming soon). 
Sparkle-data-server stores data in Cassandra.  
Sparkle-time also hosts an extensible set of data transformations that transform data on demand (e.g. for aggregation).

Sparkle-time can be run as a library inside another server, or it can run standalone if no customization is needed.

###sg.js 
**sg.js** is a d3.js based javascript library for displaying interactive charts of potentially large data sets.  Charts are customizable with declarative javascript, and extensible with d3. sg.js currently offers support for line charts, scatter plots, bar charts, etc. and is easily extensible. All charts support a drag-based zooming UI. 

sg.js also includes a dashboard component for constructing pages that aggregate many charts on the same page. Graphs in the dashboard are resizable. The browser's back button works to undo/redo zoom navigation and chart resizing.

sg.js supports the sparkle-time api, enabling zooming charts of potentially huge server hosted data sets, the option of offloading data transformations to the server, and (soon) server pushed updates to locally displayed charts.

###Sparkle Data API
To enable interoperation of multiple clients and servers, the sparkle-time server and sg.js javascript library speak a well-defined data protocol: the [Sparkle Data Protocol](https://docs.google.com/document/d/1OvRxFbTzjuLSh7J3NXEM3jNQKxCCiBEfKr5fE6EeBJk/pub). The protocol allows clients to request data transformations and data streams from a comformant server.

See [Sparkle Data Protocol](https://docs.google.com/document/d/1OvRxFbTzjuLSh7J3NXEM3jNQKxCCiBEfKr5fE6EeBJk/pub) for details of the protocol. See [Sparkle Transforms](https://docs.google.com/document/d/1rz_7otdjla5d9990zdvM6Uev-5c_jqbZhepyLIKQO6U/pub) for the current set of built in transforms.


Plans
-------
Sparkle is currently at version 0.5.x.  Sparkle 0.6 will:
* clean out legacy code 
* more fully support the [Sparkle Data Protocol](https://docs.google.com/document/d/1OvRxFbTzjuLSh7J3NXEM3jNQKxCCiBEfKr5fE6EeBJk/pub) between visualization client and visualizaton server.

Many more future features are listed in the [todo list](https://github.com/mighdoll/sparkle/blob/master/ToDo)

Using sparkle 
-------
See [Using-sg](https://github.com/mighdoll/sparkle/blob/master/Using-sg.md)

Contributing to Sparkle 
-------
See [Contributing](https://github.com/mighdoll/sparkle/blob/master/contributing.md), [Building](https://github.com/mighdoll/sparkle/blob/master/Building.md), and
 [javascript component model](https://github.com/mighdoll/sparkle/blob/master/component-model.md).

