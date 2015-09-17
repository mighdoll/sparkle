---
layout: default
title: Building Sparkle
---

Building
------
    $ sbt/sbt
    > compile


Running tests
------
    $ sbt/sbt

Unit tests

    > test

Integration tests 

    > it:test


Viewing documentation site
------

Install [Jekyll](https://jekyllrb.com/docs/installation/) locally.

Package up documentation with sbt:

    $ sbt/sbt
    > project docs
    > packageSite

Or view docs with jekyll directly:
    
    $ cd sparkle/doc/src/jekyll
    $ jekyll serve

Viewing javascript documentation site
------
Install [Jekyll](https://jekyllrb.com/docs/installation/) locally.

View docs with jekyll directly:

    $ cd sparkle/jsdoc
    $ jekyll serve

Viewing scaladoc for server libraries
------
    $ sbt/sbt
    > doc

And point the browser at each project's ../target/scala-2.11/api/index.html
