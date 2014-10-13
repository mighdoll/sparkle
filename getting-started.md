###  To run in development mode

      $ cd sparkle
      $ sbt/sbt
      > project sparkle-data-server

    # the first time you run them, these next two tasks will take a while, 
    # downloading libraries 

    # start cassandra (alternately you can run download and start cassandra separately)
      > startDependencies

    # start the data server with the demos html page, 
    # and load some test data into the store
      > reStart --root ../dashboard/demos --files ../sparkle-tests/src/test/resources/epochs.csv

    # optional, reloads .js files after edits, etc.
      > ~ products   

    # then browse to localhost:1234
    # (localhost:1235 for the admin page)


