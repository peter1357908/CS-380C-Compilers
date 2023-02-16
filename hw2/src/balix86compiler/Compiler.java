package balix86compiler;
import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;

import java.util.HashMap;
import java.util.ArrayList;

// TODO: make a `method` class so we don't have to keep 
// passing around the symbol table and the end label

// NOTES: the stack frame design can be found in the assignment link
// in README.md

// BaliCompiler parses directly, without creating an AST
public class Compiler {
	private SamTokenizer t;
	// `labelCount` is used by `generateLabel()` to generate
	// unique X86 labels
	private int labelCount = 0;
	private HashMap<String, MethodInfo> methodInfos = new HashMap<String, MethodInfo>();
	private static final int WORD_SIZE = 4;

	// `compile()` is the only public method; it
	// takes in fileName and outputs the X86 code string
	public String compile(String fileName) {
		// returns X86 code for program in file
		try {
			t = new SamTokenizer(fileName);
			return getProgram();
		} 
		catch (Exception e) {
			System.out.println(e.getMessage());
			// return an empty string if there was an error
			return "";
		}
	}
	
	private String getProgram() {
		/*
		 * if there was no requirement of printing the returned value
		 * to the console, we can simply label the Bali `main` as `CMAIN` in
		 * X86. However, given the requirement, we have to hardcode `CMAIN`
		 * to call `main` instead. This is somewhat similar to how we had to
		 * bootstrap `main` for SaM.
		 * 
		 * we don't have to record `CMAIN` as a method; the X86 program
		 * should never need to jump to `CMAIN`. Yet we still need to hardcode
		 * the method label for `main` since we need it in `CMAIN`.
		 * Further, it can't just be `main` since apparently `CMAIN` is just `main`
		 * in X86??
		 */
		MethodInfo mainMethodInfo = new MethodInfo();
		mainMethodInfo.methodLabel = "mainBali"; // the only hardcoded method label
		mainMethodInfo.numParameters = 0;
		methodInfos.put("main", mainMethodInfo);

		String pgm = """
				%include \"io.inc\"

				section .text
					global CMAIN
				CMAIN:
					push ebp
					mov ebp, esp

					call mainBali

					PRINT_DEC 4, eax
					NEWLINE

					pop ebp
					ret
				""";

		while (t.peekAtKind() != TokenType.EOF) {
			pgm += getMethod();
		}
		return pgm;
	}

	private String getMethod() {
		// `s` is a symbol table implemented with a Java HashMap
		HashMap<String, Integer> s = new HashMap<String, Integer>();
		
		// the only type in Bali is `int`
		assertAndConsume("int");

		// set up the method label and the end label
		String methodID = t.getWord();
		// try to look up the method; only create a new label if necessary.
		// A label could exist if a method call is made before the method
		// is defined.
		MethodInfo currMethodInfo = methodInfos.get(methodID);
		if (currMethodInfo == null) {
			currMethodInfo = new MethodInfo();
			currMethodInfo.methodLabel = generateLabel(methodID);
			methodInfos.put(methodID, currMethodInfo);
		}
		String methodLabel = currMethodInfo.methodLabel;

		String methodCode = methodLabel + """
				:
				push ebp
				mov ebp, esp
				""";
		String endLabel = generateLabel(methodID + "_end");
		
		// parse the formals and remember the offset of the return value
		assertAndConsume('(');
		int numParameters = parseFormals(s);

		// if not previously called, update the numParameters
		// otherwise, check if numParameters matches the previous call
		if (currMethodInfo.numParameters == -1) {
			currMethodInfo.numParameters = numParameters;
		} else if (currMethodInfo.numParameters != numParameters) {
			throw new Error("Error parsing method declaration for `" + methodID + "`; its number of formals is different from the number of actuals in a call before its definition");
		}
		assertAndConsume(')');

		// parse the body
		assertAndConsume('{');

		// first, parse the variable declarations
		// there can be multiple lines of declarations, and each
		// line can have multiple variables declared
		int offset = 0; // no local variables yet; at `ebp`
		String varID;
		while (t.check("int")) {
			// consume a line of variable declaration
			while (true) {
				varID = t.getWord();
				// error out if the variable has already been defined
				if (s.get(varID) != null) {
					throw new Error("Error parsing a method's local variables: duplicate declaration of variable/parameter `"
							+ varID + "` on line " + t.lineNo());
				}

				if (t.check('=')) {
					methodCode += getExpression(s) + "push eax\n";
				} else {
					methodCode += "push dword 0\n";
				}

				offset -= WORD_SIZE;
				s.put(varID, Integer.valueOf(offset));

				if (!t.check(','))
					break;
			}

			assertAndConsume(';');
		}

		// then, parse the statements
		methodCode += getStatements(s, endLabel, null);
		assertAndConsume('}');

		// set up the end of the method
		// NOTE that we assume every expression return to `eax` so at this
		// point the return value should already in `eax`
		methodCode += endLabel + ":\n";

		// pop the local variables off (simply move the Stack Pointer)
		methodCode += "add esp, " + (-offset) + '\n';

		// pop the EBP and return to caller
		methodCode += """
				pop ebp
				ret
				""";

		return methodCode;
	}
	
	// return the number of formals (which would be recorded in the MethodInfos for verification)
	private int parseFormals(HashMap<String, Integer> s) {
		// first, store the formals in an array
		ArrayList<String> formals = new ArrayList<String>(4);

		while (t.check("int")) {
			formals.add(t.getWord());
			if (!t.check(',')) break;
		}

		// now, remember their EBP offsets in the symbol table
		int numFormals = formals.size();
		int i = 0;
		String currFormalID;
		while (i < numFormals) {
			currFormalID = formals.get(i);
			if (s.get(currFormalID) != null) {
				throw new Error("Error parsing formals on line " + t.lineNo() + ": duplicate parameter name `" + currFormalID + "`");
			}
			
			s.put(currFormalID, Integer.valueOf((numFormals + 1 - i) * WORD_SIZE));
			i += 1;
		}

		return numFormals;
	}

	// returns the X86 code for the Bali statements parsed
	// breakLabel is the label for where a `break` should jump to; null means a
	// `break` statement is not expected
	private String getStatements(HashMap<String, Integer> s, String endLabel, String breakLabel) {
		String statementsCode = "";

		while (true) {
			if (t.peekAtKind() == TokenType.EOF) {
				throw new Error("EOF encountered while parsing statements on line " + t.nextLineNo());
			}
			if (t.test('}')) break;

			statementsCode += getStatement(s, endLabel, breakLabel);
		}

		return statementsCode;
	}

	// return the X86 code for the Bali statement parsed
	private String getStatement(HashMap<String, Integer> s, String endLabel, String breakLabel) {
		String statementCode = "";
		switch (t.peekAtKind()) {
			case OPERATOR:
				char operator = t.getOp();
				switch (operator) {
					// `BLOCK` case
					case '{':
						statementCode += getStatements(s, endLabel, breakLabel);
						assertAndConsume('}');
						break;
					case ';':
						break;
					default:
						throw new Error(
								"Error parsing a statement on line " + t.lineNo() + "; expecting either a `{` or a `;` but found `" + operator + "` instead");
				}
				break;
			case WORD:
				String word = t.getWord(); // needed for the assignment/default case
				switch (word) {
					case "return":
						// evaluate the expression
						statementCode += getExpression(s);
						assertAndConsume(';');

						// go to the end of the method
						statementCode += "jmp " + endLabel + '\n';
						break;
					case "if":
						statementCode += parseIfStatement(s, endLabel, breakLabel);
						break;
					case "while":
						statementCode += parseWhileStatement(s, endLabel);
						break;
					case "break":
						if (breakLabel == null) {
							throw new Error("Error parsing statements; not expecting a `break` on line " + t.lineNo());
						}
						statementCode += "jmp " + breakLabel + '\n';
						assertAndConsume(';');
						break;
					default:
						// default must be an assignment, otherwise parse error
						int assigneeEBPOffset = getEBPOffset(s, word);
						assertAndConsume('=');
						statementCode += getExpression(s);
						assertAndConsume(';');
						statementCode += String.format("mov [ebp%+d], eax\n", assigneeEBPOffset);
						break;
				}
				break;
			default:
				throw new Error(
						"Error parsing statements; expecting either an operator or a word at the beginning but neither was found");
		}

		return statementCode;
	}
	
	// called after having already consumed `if`; returns the X86 code
	private String parseIfStatement(HashMap<String, Integer> s, String endLabel, String breakLabel) {

		// first, parse the Bali code components
		assertAndConsume('(');
		String conditionalExpression = getExpression(s);
		assertAndConsume(')');
		String trueStatement = getStatement(s, endLabel, breakLabel);
		assertAndConsume("else");
		String falseStatement = getStatement(s, endLabel, breakLabel);

		// then, generate the X86 code
		String ifStatement = conditionalExpression;
		ifStatement += generateComparison("eax", "0", "jne", trueStatement, falseStatement);

		return ifStatement;
	}
	
	// called after having already consumed `while`; returns the X86 code
	private String parseWhileStatement(HashMap<String, Integer> s, String endLabel) {
		String whileStatement = "";

		// first, generate the labels and parse the Bali code
		String whileStartLabel = generateLabel("while_start");
		String breakLabel = generateLabel("break");
		assertAndConsume('(');
		String conditionalExpression = getExpression(s);
		assertAndConsume(')');
		String whileInnerStatement = getStatement(s, endLabel, breakLabel);

		// then, generate the X86 code. Here we won't be using the
		// generateComparison() abstraction directly
		whileStatement += whileStartLabel + ":\n";
		whileStatement += conditionalExpression;
		// if `false`, break
		String falseBranch = "jmp " + breakLabel + '\n';
		// if `true`, execute the inner STMT then go back to the beginning
		String trueBranch = whileInnerStatement + "jmp " + whileStartLabel + '\n';

		whileStatement += generateComparison("eax", "0", "jne", trueBranch, falseBranch);

		whileStatement += breakLabel + ":\n";

		return whileStatement;
	}

	// all expressions' result value will be in the EAX register after evaluation
	private String getExpression(HashMap<String, Integer> s) {
		switch (t.peekAtKind()) {
			case INTEGER:
				return "mov eax, " + t.getInt() + "\n";
			case WORD:
				String word = t.getWord();
				switch (word) {
					case "true":
						return "mov eax, 1\n";
					case "false":
						return "mov eax, 0\n";
					default:
						// peek ahead to see if it's a method
						if (t.check('(')) {
							return getMethodCall(s, word);
						} else {
							// if not a method, must be a valid location
							return String.format("mov eax, [ebp%+d]\n", getEBPOffset(s, word));
						}
				}
			case OPERATOR:
				String expressionCode;
				assertAndConsume('(');

				// if the next token isn't `-` or `!`, it must be another expression
				if (t.check('-')) {
					expressionCode = getExpression(s) + "neg eax\n";
				} else if (t.check('!')) {
					expressionCode = getExpression(s) + generateComparison("eax", "0", "je", "mov eax, 1\n", "mov eax, 0\n");
				} else {
					// get the first value and push it onto the stack
					expressionCode = getExpression(s) + "push eax\n";
					// and the next token must be an operator
					char operator = t.getOp();
					// if it's a right parenthesis, just return (already consumed it)
					if (operator == ')') {
						return expressionCode;
					}
					// otherwise, there must be another operand. After getting that
					// operand into `eax`, we'll pop the first operand into `ebx`
					expressionCode += getExpression(s) + "pop ebx\n";

					switch (operator) {
						case '+':
							expressionCode += "add eax, ebx\n";
							break;
						case '-':
							expressionCode += """
									sub ebx, eax
									mov eax, ebx
									""";
							break;
						case '*':
							expressionCode += "imul eax, ebx\n";
							break;
						case '/':
							// swap EAX and EBX for the proper ordering using EDX
							// then clear EDX (we need to clear EDX either way since `idiv` tries
							// divide with EDX:EAX but we only care about EAX)
							expressionCode += """
									mov edx, eax
									mov eax, ebx
									mov ebx, edx
									mov edx, 0
									idiv ebx
									""";
							break;
						case '&':
							// both EAX and EBX need to be greater than 0.
							// first, we test if EAX is `true` (!= 0)
							// if EAX is `false`, then we return `false` (put `0` in EAX)
							// otherwise, we evaluate whether EBX is `true` and put that in EAX
							// NOTE how the expressions are nested...
							// TODO: more readable and efficient if just hard-coded...
							expressionCode += generateComparison("eax", "0", "jne", generateComparison("ebx", "0", "jne", "mov eax, 1\n", "mov eax, 0\n"), "mov eax, 0\n");
							break;
						case '|':
							// one of EAX or EBX need to be greater than 0.
							// first, we test if EAX is `true` (!= 0)
							// if EAX is `true`, then we return `true` (put `1` in EAX)
							// otherwise, we evaluate whether EBX is `true` and put that in EAX
							// NOTE how the expressions are nested...
							// TODO: more readable and efficient if just hard-coded...
							expressionCode += generateComparison("eax", "0", "jne", "mov eax, 1\n", generateComparison("ebx", "0", "jne", "mov eax, 1\n", "mov eax, 0\n"));
							break;
						case '<':
							expressionCode += generateComparison("ebx", "eax", "jl", "mov eax, 1\n", "mov eax, 0\n");
							break;
						case '>':
							expressionCode += generateComparison("ebx", "eax", "jg", "mov eax, 1\n", "mov eax, 0\n");
							break;
						case '=':
							expressionCode += generateComparison("ebx", "eax", "je", "mov eax, 1\n", "mov eax, 0\n");
							break;
						default:
							throw new Error("Error parsing expression; found unexpected operator `" + operator + "` on line " + t.lineNo());
					}
				}
				assertAndConsume(')');
				return expressionCode;
			default:
				throw new Error("Error parsing expression; unexpected token on line " + t.nextLineNo());
		}
	}
	
	// called after having already consumed the left parenthesis; returns X86
	private String getMethodCall(HashMap<String, Integer> s, String methodID) {
		String methodCallCode = "";
		int numParameters = 0;
		MethodInfo currMethodInfo = methodInfos.get(methodID);

		// if the method hasn't been declared yet, record the info based on
		// the current call
		if (currMethodInfo == null) {
			currMethodInfo = new MethodInfo();
			currMethodInfo.methodLabel = generateLabel(methodID);
			methodInfos.put(methodID, currMethodInfo);
		}
		String methodLabel = currMethodInfo.methodLabel;

		while (true) {
			if (t.peekAtKind() == TokenType.EOF) {
				throw new Error("EOF encountered while parsing actuals of a method on " + t.nextLineNo());
			}
			if (t.test(')')) break;
			// for each actual encountered, push it onto the stack
			methodCallCode += getExpression(s) + "push eax\n";
			numParameters += 1;
			if (!t.check(',')) break;
		}

		assertAndConsume(')');

		// if not previously declared/called, update the numParameters
		// otherwise, check if numParameters matches the previous call/declaration
		if (currMethodInfo.numParameters == -1) {
			currMethodInfo.numParameters = numParameters;
		} else if (currMethodInfo.numParameters != numParameters) {
			throw new Error("Error parsing method call for `" + methodID + "`; its number of parameters is different from the number of parameters in a previous call/in its declaration");
		}
		
		methodCallCode += "call " + methodLabel + '\n';
		methodCallCode += "add esp, " + (numParameters * WORD_SIZE) + '\n';

		return methodCallCode;
	}

	// ===============================
	// the following are utility/helper functions
	// ===============================
	private void assertAndConsume(String word) {
		if (!t.check(word))
			// TODO: print the unexpected token
			throw new Error("Expected token `" + word + "` on line " + t.nextLineNo());
	}

	private void assertAndConsume(char c) {
		if (!t.check(c))
			// TODO: print the unexpected token
			throw new Error("Expected token `" + c + "` on line " + t.nextLineNo());
	}

	// we make every label unique by appending a unique suffix to each
	// given label prefix (e.g., `main` would become `main_0`)
	private String generateLabel(String prefix) {
		return prefix + "_" + labelCount++;
	}

	/* shorthand for generating a comparison expression to avoid needing to
	 * declare labels and calling the label generators. NOTE: the `trueStatement`
	 * and `falseStatement` need to be terminated with a newline
	 */
	private String generateComparison(String leftOperand, String rightOperand, String jCondition, String trueStatement, String falseStatement) {
		String trueLabel = generateLabel("true");
		String postConditionalLabel = generateLabel("post_conditional");
		return String.format("""
				cmp %s, %s
				%s %s
				%sjmp %s
				%s:
				%s%s:
				""", leftOperand, rightOperand, jCondition, trueLabel, 
				falseStatement, postConditionalLabel, trueLabel, 
				trueStatement, postConditionalLabel);
	}

	// get the EBP offset for a symbol as an int.
	// Throws error if the symbol is not found in the symbol table
	private int getEBPOffset(HashMap<String, Integer> s, String symbol) {
		Integer EBPOffset = s.get(symbol);
		if (EBPOffset == null) {
			throw new Error(
					"Error parsing location reference; " + symbol + " is not a valid variable, found on line " + t.lineNo());
		}
		return EBPOffset.intValue();
	}
}

class MethodInfo {
	public String methodLabel = null;
	public int numParameters = -1;
}
