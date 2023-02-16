[assignment link](https://www.cs.utexas.edu/~pingali/CS380C/2023/assignments/assignment3-x86/Assignment3.html)

In this repo, `x86` specifically refers to the `NASM` syntax for `x86`

TODO:
- check if the `break` statement is properly accounted for
- ensure that all method calls are valid at compile time (currently calls to undefined methods still pass compilation)
- furnish error messages more (add context to assertions)
- find a way to build automatically in CLI that INCLUDES DEPENDENCIES IN THE JAR FILE... currently relies on VS Code's Java Project Manager...
- write a script to test automatically in CLI (currently just compiles the files and the output is not directly helpful)
- indent the output code

Takeaways:
- Java sucks. Needed to ensure a newline at the end of the MANIFEST file. Need to follow packaging conventions (have a `src` folder, then under it are `package` folders, and under which are the actual `.java` files...)