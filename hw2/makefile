all: clean compileTests

testDir = testcases
compiledTestFiles = $(testDir)\*.asm
javaCompiler = java
javaCompilerFlags = -jar
Balicompiler = BaliX86Compiler.jar
compilation = $(javaCompiler) $(javaCompilerFlags) $(Balicompiler)

compileTests:
	$(foreach file, $(wildcard $(testDir)/*.bali), $(compilation) $(file) $(file).asm &) echo done

clean:
	del $(compiledTestFiles)
