###  To run in development mode

    # in your bash shell, start sbt
      $ cd sparkle
      $ sbt/sbt
    
    # in sbt, switch the to the sparkle-data-server project 
      root > project sparkle-data-server

    # the first time you run them, these next two tasks will take a while, 
    # downloading libraries 

    # start cassandra (alternately you can download and start cassandra separately from its own repo)
      sparkle-data-server > startDependencies

    # start the data server with the demos html page, 
    # and load some test data into the store
      sparkle-data-server > reStart --root ../dashboard/demos --files ../sparkle-tests/src/test/resources/epochs.csv

    # optional, reloads .js files after edits, etc.
      sparkle-data-server > ~ products   

    # then browse to localhost:1234
    # (localhost:1235 for the admin page)


