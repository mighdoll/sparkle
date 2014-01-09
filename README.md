sparkle-graph
=============

**This is a pre-release version of sparkle-graph.  The documentation is wrong, the API will change.**

Introduction
-------
**Sparkle-graph** creates interactive dashboards from time series data.  

[links to example zooming charts]

Components
------
Sparkle-graph contains two primary components:

###Sparkle-Graph Server 
A mini web server that reads .csv/.tsv time series data files from disk or retrieves time 
series data from cassandra and provides an HTTP api so that dashboards can request sections of the time series data.  

Sparkle-graph server can also serve the javascript/html files for your custom dashboard via the --root command line option.  
Built-in serving is handy in development, but production services will normally prefer to serve static files through e.g. nginx or cloudfront.

###sg.js 
A d3 based javascript library for displaying interactive charts of potentially large data sets (by relying
on a server to downsample data on demand).  Includes basic support for zooming line charts, zooming categorized scatter plots, zooming
bar charts.  Charts can be combined in a simple visual dashboard, including support for browsing history and 

Directories
------

##### sg.js
- *sg* - The sg.js library for interactive server-centric charts.
  
- *css* - Styling for sg.js charts and dashboards.
  
- *dashboard* - Example sparkle-graph dashboards.
  
- *jslib* - Javascript libraries used by sg.js  (primarily: require.js, when.js, d3.js) 

##### sg.js tests
- *test/web/spec* - unit tests (using jasmine 2)

- *test/web/test* - utilities for unit tests.

##### sparkle-graph server
- */scala/nest/sparkle/util* - generic utilities used by sparkle-graph server (Filesystem watching, option and future conversion, etc.)

- *scala/test/sparkle/graph* - file system client time series .csv files, ram based column store, 
http api protocol (v0 is being slowly replaced by v1), basic data transformations.  [TBD refactor these into separate packages]

- *scala/test/sparkle/store/cassandra* - cassandra client for time series data storage

##### sparkle-graph server tests
- *src/test/scala/nest/* - unit tests

- *src/test/resources* - unit test data files

- *src/it/scala/nest/sparkle/store/cassandra/* - cassandra integration tests

##### sparkle-graph server build files
- *project* - sbt build files

- *sbt* - embedded copy of the sbt launcher

- *target* - (build generated .class and .jar files)


Building
------
    $ cd sparkle-graph
    $ sbt/sbt
    > assembly
    > exit


Running tests
------
    $ sbt/sbt

unit tests

    > test

integration tests (requires that cassandra is running locally)

    > it:test

javascript tests in e.g. chrome, browse to: 

    file:///Users/MyAccountNameHere/sparkle-graph/src/test/web/SpecRunner.html


Command line Options 
------

  Usage: sg [OPTIONS] path

    <path>              directory containing .tsv or .csv data
    --root path         directory containing custom web pages to serve

    --display           navigate the desktop web browser to the current dashboard
    --port port         tcp port for the web server (default port is 1234)
    --debug             turn on debug logging
    --help              show this help


Developing Your Own Dashboard
------
####Custom Dashboards 
A custom dashboard is an html or javascript file that specifies the data and look and feel of a dashboard.
Most of the work is done by the sg.js library, so a simple dashboard is mostly configuration: picking chart types, colors and etc.
Custom dashboards require just a few lines of javascript configuration.  Here's an example:

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

It's also possible to extend the server to include custom code on the server that your dashboard can use.  For example, see 
[TestCustomApi](https://github.com/mighdoll/sparkle/blob/master/src/test/scala/nest/sparkle/graph/TestCustomApi.scala)

#####Trying custom dashboards
Run with your dashboard and data:

    > run --root my-dashboard-dir my-data-dir

While developing, just edit the dashboard file and refresh the browser.  It is not necessary to restart the server after changing data files or dashboard javascript files.
(The server doesn't cache the dashboard html/javascript files.  The server caches data files, but it is clever enough to reload the data files as they change on disk.)


Data formats for .tsv .csv files
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


