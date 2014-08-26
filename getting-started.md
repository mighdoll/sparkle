
###  Setup Cassandra

      cassandra 2.0.x should be is installed and running. We 

###  To run in development mode

      $ cd sparkle
      $ sbt/sbt

      > project sparkle
      > reStart --root dashboard/demos --files ../sparkle-tests/src/test/resources/epochs.csv
      > ~ products   
      # then browse to localhost:1234
      # or localhost:1235 for the admin page

###  To run the assembled jar 

      > project sparkle
      > assembly
        # substitute the current jar name here
      $ java -jar sparkle/sparkle/target/scala-2.10/sparkle-assembly-0.6.0-5d47e71.jar --root sparkle/dashboard/demos --files sparkle/sparkle-tests/src/test/resources/epochs.csv

      # then browse to localhost:1234
      # or localhost:1235 for the admin page
