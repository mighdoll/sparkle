---
layout: default
title: Code Standards
---

Your commit to master:

* Should make the codebase more maintainable, not less.
* Must be deployable to production.

  * It's not OK to commit incomplete code that doesn't pass existing tests or doesn't meet maintainability standards.
  * It's OK (and encouraged) to commit complete code that supports an incomplete larger feature. 
  Just disable the larger feature either in the code or the .conf.

* Must be covered by automated tests.

Logging

* The unit/integration tests should not print to the console in the normal case. Debug logs in tests should
generally go into /tmp/sparkle-tests.log.
* The unit/integration tests should log unexpected warnings and errors to the console - it's convenient for 
identifying and debugging integration test failures.

Scala Style

* Every public method must have a scaladoc comment.

  * the comment should explain something that's not obvious from the name or type signature of the method
* The code should be automatically formattable with scalariform 
* Use meaningful names for values and functions 

