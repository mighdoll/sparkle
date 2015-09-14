Sparkle Graph
---
Sparkle Graph **sg.js** is a d3.js based javascript library for displaying 
interactive charts of potentially large data sets.  
Charts are customizable with declarative javascript, and extensible with d3. 
sg.js currently offers support for line charts, scatter plots, bar charts, etc. 
and is easily extensible to support more chart types.

Notable in Sparkle Graph:

* All charts support a drag-based zooming UI. 

* Sparkle Graph supports the sparkle-data-server api, 
enabling zooming charts of potentially huge server hosted data sets, 
the option of offloading data transformations to the server, 
and (soon) server pushed updates to locally displayed charts.


Dashboard 
---
The repo includes an Angular.js / Material Design dashboard built around Sparkle Graph.

The dashboard includes:

* An autocomplete based chooser for selecting data series from the server.
* A widget for uploading .csv/.tsv files via drag and drop.
* A control panel for changing graph types, summary parameters, time series and non time series, etc.
