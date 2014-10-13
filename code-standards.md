Your commit to master:

Should make the codebase more maintainable, not less.

Must be deployable to production.
* It's not OK to commit incomplete code that doesn't pass existing tests or doesn't meet maintainability standards.
* It's OK (and encouraged) to commit complete code that supports an incomplete larger feature. 
Just disable the larger feature either in the code or the .conf.

Must be covered by automated tests.


Scala Style
* Every public method must have a scaladoc comment.
  * the comment should explain something that's not obvious from the name or type signature of the method
* The code should be automatically formattable with scalariform
* Use meaningful names for values and functions 

