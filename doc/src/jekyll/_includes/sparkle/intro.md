Sparkle is a small suite of libraries for making interactive data visualizations.
Sparkle includes a javascript client library and a scala/jvm server library.
The client and server are connected by json over webosckets api. 

As a standalone tool, sparkle gives users an easy way to make visualize data from a directory of .csv files.
The graphs are interactively zoomable in a d3 based web interface.

Sparkle is also designed to be customized for integration in larger workgroup data pipelines.

----

Sparkle components 
----

###Sparkle Data Server 
**sparkle-data-server** is a mini web server that provides HTTP and websocket apis for data visualization.

The sparkle data server includes modules to store data in Casssandra or RAM, 
and to collect data from .csv/.tsv data files or Kafka.

the sparkle data server hosts an extensible set of data transformations that transform data on demand 
(e.g. for aggregation). See [Data Transformation](/doc/DataTransform.html) for details.

The sparkle data server can be used as a library inside another server, 
or it can run standalone if no additonal customization is needed.

###sg.js 
**sg.js** is a d3.js based javascript library for displaying interactive charts of potentially large data sets.  Charts are customizable with declarative javascript, and extensible with d3. sg.js currently offers support for line charts, scatter plots, bar charts, etc. and is easily extensible. All charts support a drag-based zooming UI.

sg.js also includes a dashboard component for constructing pages that aggregate many charts on the same page. Graphs in the dashboard are resizable. The browser's back button works to undo/redo zoom navigation and chart resizing.

sg.js supports the sparkle-time api, enabling zooming charts of potentially huge server hosted data sets, the option of offloading data transformations to the server, and (soon) server pushed updates to locally displayed charts.

###Sparkle Data API
To enable interoperation of multiple clients and servers, 
the sparkle-time server and sg.js javascript library speak a well-defined data protocol: 
the [Sparkle Data Protocol](https://docs.google.com/document/d/1OvRxFbTzjuLSh7J3NXEM3jNQKxCCiBEfKr5fE6EeBJk/pub). 
The protocol allows clients to request data transformations and data streams from a comformant server.

----

External Presentations 
----

* See [The Live Layer](https://docs.google.com/presentation/d/16onrz3i4aUORHxnKwdbX9mk9UgG9JJyq6eO4qDeZSn0) for a discussion of the Live Layer,
a key component missing from the Lambda Architecture.
* See [Visualize the Things](https://docs.google.com/presentation/d/1YGeO2FEvdjGgSRihxU5HdEDdktBbv7mfYDtVAxW0FKU) for a brief introduction to sparkle, lessons on garbage collection with reative streams, and comments on the future of the Lambda Architecture.
*  See [Sparkle Intro Talk](https://docs.google.com/presentation/d/1j704Lcj7HhL1O6K2sOYdQeDyhQ_porkbur7UOkKeD50) for an introduction presentation.
* See [Sparkle Google Docs Folder](https://drive.google.com/folderview?id=0B6uZet2ug3aKfm9KRTdXcFZUc3o2UnFyU3FscWk2T2pNazdoR1AzMlZiU3lLRXFILXJHdlU) for other docs.

