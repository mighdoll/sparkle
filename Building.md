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

