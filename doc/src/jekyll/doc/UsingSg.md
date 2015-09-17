---
layout: default
title: Using the sg Tool
---

Sparkle comes with a standalone `sg` tool for desktop visualization of data series.
The `sg` tool is an easy way for engineers to make exploratory interactive visualizations. 


_The standalone sg build is broken as of 9/15/2015. 
Also, this documentation doesn't yet describe the new angular.js based dashboard. 
Stay tuned._ 


Command line Options 
------

  Usage: sg [OPTIONS] 

    --files <path>      directory containing .tsv or .csv data
    --root path         directory containing custom web pages to serve

    --display           navigate the desktop web browser to the current dashboard
    --port port         tcp port for the web server (default port is 1234)
    --debug             turn on debug logging
    --format            erase and reformat the database
    --help              show this help


Developing Your Own Dashboard
---

###Custom Dashboards 
A custom dashboard is an html or javascript file that specifies the data and look and feel of a dashboard.  Most of the work is done by the sg.js library, so a simple dashboard is mostly configuration: picking chart types, colors and etc.  An example:

    { title: "90th Percentile Request Time",
      groups: [ {
        label: "seconds",
        axis: sideAxis(),
        named: [ { name: "epochs.csv/p90" } ]
      } ]
    }

Here are some simple examples:
[request time](https://github.com/mighdoll/sparkle/tree/master/dashboard/simple/simple.js) and
[db requests](https://github.com/mighdoll/sparkle/tree/master/dashboard/db-requests/dbDashboard.js).
See [sparkle-graph example dashboards](https://github.com/mighdoll/sparkle/tree/master/dashboard) for more examples.

It is also possible to extend the server to include custom code on the server that your dashboard can use.  For example, see 
[TestCustomApi](https://github.com/mighdoll/sparkle/blob/master/src/test/scala/nest/sparkle/graph/TestCustomApi.scala)

###Trying custom dashboards
Run with your dashboard and data:

    > run --root my-dashboard-dir --files my-data-dir

While developing, just edit the dashboard file and refresh the browser.  
It is not necessary to restart the server after changing data files or dashboard javascript files.
The server doesn't cache the dashboard html/javascript files. 
The server caches data files, but it is clever enough to reload the data files as they change on disk.

Built-in serving is handy in development, but production services will normally prefer to serve static files through e.g. nginx or cloudfront.

Data formats for .tsv and .csv files
------
Sparkle-Graph Server reads time series numerical data.  You can point the server at either a single file or a directory.

1. The data files must be in tab separated value format or comma separated value format.  (The server will automatically choose based on the file contents)
1. The first line in each data file must be a header line, naming the columns.
1. There must be a time column must be named something like 'time' or 'date'.  See findTimeColumn in the [source](https://github.com/mighdoll/sparkle/blob/master/src/main/scala/nest/sparkle/graph/FileLoadedDataSet.scala).
1. The time column data format can be one of:
  1. a positive whole number - milliseconds since the epoch
  1. a decimal fraction - seconds and fractional seconds since the epoch
  1. a date string of the form: 2013-02-15T01:32:50.186
1. All other data columns are interpreted as double precision floating point numbers.

