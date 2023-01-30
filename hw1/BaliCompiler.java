import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;
import java.util.HashMap;

import java.util.ArrayList;

// TODO: make a `method` class so we don't have to keep 
// passing around the symbol table and the end label

// BaliCompiler parses directly, without creating an AST
public class BaliCompiler {
	private SamTokenizer t;
	// `labelCount` is used by `generateLabel()` to generate
	// unique SaM labels
	private int labelCount = 0;
	private HashMap<String, MethodInfo> methodInfos = new HashMap<String, MethodInfo>();

	// `compile()` is the only public method; it
	// takes in fileName and outputs the SaM code string
	public String compile(String fileName) {
		// returns SaM code for program in file
		try {
			t = new SamTokenizer(fileName);
			String pgm = getProgram() + "STOP\n";
			return pgm;
		} 
		catch (Exception e) {
			System.out.println(e.getMessage());
			return "STOP\n";
		}
	}
	
	private String getProgram() {
		String pgm = "";
		while (t.peekAtKind() != TokenType.EOF) {
			pgm += getMethod();
		}
		return pgm;
	}

	private String getMethod() {
		BaliSymbolTable s = new BaliSymbolTable();
		
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

		String methodCode = methodLabel + ":\n";
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
		int rvFBROffset = -numParameters - 1;
		assertAndConsume(')');

		// parse the body
		assertAndConsume('{');

		// first, parse the variable declarations
		// there can be multiple lines of declarations, and each
		// line can have multiple variables declared
		int offset = 2; // first local is at address `FBR + 2`
		while (t.test("int")) {
			// consume a line of variable declaration
			while (t.check("int")) {
				String varID = t.getWord();
				if (t.check('=')) {
					methodCode += "PUSHIMM " + t.getInt() + "\n";
				} else {
					methodCode += "PUSHIMM 0\n";
				}

				s.add(varID, offset);
				offset += 1;

				if (!t.check(','))
					break;
			}

			assertAndConsume(';');
		}
		int numLocalVars = offset - 2;

		// then, parse the statements
		methodCode += getStatements(s, endLabel, null);
		assertAndConsume('}');

		// set up the end of the method
		methodCode += endLabel + ":\n";

		// store the TOS value in the return value address
		// (assuming that the return value is undefined if no explicit
		// return statement exists)
		methodCode += "STOREOFF " + rvFBROffset + '\n';

		// pop the local variables off (simply move the Stack Pointer)
		methodCode += "ADDSP " + (-numLocalVars) + '\n';

		// return to caller
		methodCode += "JUMPIND\n";

		return methodCode;
	}
	
	// return the number of formals (to determine the return value's FBR offset)
	private int parseFormals(BaliSymbolTable s) {
		// first, store the formals in an array
		ArrayList<String> formals = new ArrayList<String>(4);

		// if (t.test(')')) {
		// 	return 0;
		// }
		// assertAndConsume("int");
		// formals.add(t.getWord());
		// while (!t.test(')')) {
		// 	assertAndConsume(',');
		// 	assertAndConsume("int");
		// 	formals.add(t.getWord());
		// }

		while (t.check("int")) {
			formals.add(t.getWord());
			if (!t.check(',')) break;
		}

		// now, remember their FBR offsets in the symbol table
		int numFormals = formals.size();
		int i = 0;
		while (i < numFormals) {
			s.add(formals.get(i), -numFormals + i);
			i += 1;
		}

		return numFormals;
	}

	// returns the SaM code for the Bali statements parsed
	// breakLabel is the label for where a `break` should jump to; null means a
	// `break` statement is not expected
	private String getStatements(BaliSymbolTable s, String endLabel, String breakLabel) {
		String statementsCode = "";

		while (true) {
			if (t.peekAtKind() == TokenType.EOF) {
				throw new Error("EOF encountered while parsing statements on line " + t.lineNo());
			}
			if (t.test('}')) break;

			statementsCode += getStatement(s, endLabel, breakLabel);
		}

		return statementsCode;
	}

	// return the SaM code for the Bali statement parsed
	private String getStatement(BaliSymbolTable s, String endLabel, String breakLabel) {
		String statementCode = "";
		switch (t.peekAtKind()) {
			case OPERATOR:
				switch (t.getOp()) {
					// `BLOCK` case
					case '{':
						statementCode += getStatements(s, endLabel, breakLabel);
						assertAndConsume('}');
						break;
					case ';':
						break;
					default:
						throw new Error(
								"Error parsing a statement on line " + t.lineNo() + " (expecting either a `{` or a `;`)");
				}
				break;
			case WORD:
				String word = t.getWord(); // needed for the assignment/default case
				switch (word) {
					case "return":
						// evaluate the expression
						statementCode += getExpression(s);
						
						// go to the end of the method
						statementCode += "JUMP " + endLabel + '\n';
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
						statementCode += "JUMP " + breakLabel + '\n';
						assertAndConsume(';');
						break;
					default:
						// default must be an assignment, otherwise parse error
						int assigneeFBROffset = getFBROffset(s, word);
						assertAndConsume('=');
						statementCode += getExpression(s);
						statementCode += "STOREOFF " + assigneeFBROffset + '\n';
						break;
				}
				break;
			default:
				throw new Error(
						"Error parsing statements; expecting either an operator or a word at the beginning but neither was found");
		}

		return statementCode;
	}
	
	// called after having already consumed `if`; returns the SaM code
	private String parseIfStatement(BaliSymbolTable s, String endLabel, String breakLabel) {
		String ifStatement = "";

		// first, generate the labels and parse the Bali code
		String trueLabel = generateLabel("true");
		String postConditionalLabel = generateLabel("post_conditional");
		assertAndConsume('(');
		String conditionalExpression = getExpression(s);
		assertAndConsume(')');
		String trueStatement = getStatement(s, endLabel, breakLabel);
		assertAndConsume("else");
		String falseStatement = getStatement(s, endLabel, breakLabel);

		// then, generate the SaM code
		ifStatement += conditionalExpression;
		ifStatement += "JUMPC " + trueLabel + '\n';
		ifStatement += falseStatement;
		ifStatement += "JUMP " + postConditionalLabel + '\n';
		ifStatement += trueLabel + ":\n";
		ifStatement += trueStatement;
		ifStatement += postConditionalLabel + ":\n";

		return ifStatement;
	}
	
	// called after having already consumed `while`; returns the SaM code
	private String parseWhileStatement(BaliSymbolTable s, String endLabel) {
		String whileStatement = "";

		// first, generate the labels and parse the Bali code
		String whileStartLabel = generateLabel("while_start");
		String breakLabel = generateLabel("break");
		assertAndConsume('(');
		String conditionalExpression = getExpression(s);
		assertAndConsume(')');
		String whileInnerStatement = getStatement(s, endLabel, breakLabel);

		// then, generate the SaM code
		whileStatement += whileStartLabel + ":\n";
		whileStatement += conditionalExpression;
		whileStatement += "ISNIL\n";
		whileStatement += "JUMPC " + breakLabel + '\n'; // break if cond == false
		whileStatement += whileInnerStatement; // otherwise, execute the inner STMT
		whileStatement += "JUMP " + whileStartLabel + '\n';
		whileStatement += breakLabel + ":\n";

		return whileStatement;
	}

	private String getExpression(BaliSymbolTable s) {
		switch (t.peekAtKind()) {
			case INTEGER:
				return "PUSHIMM " + t.getInt() + "\n";
			case WORD:
				String word = t.getWord();
				switch (word) {
					case "true":
						return "PUSHIMM 1\n";
					case "false":
						return "PUSHIMM 0\n";
					default:
						// peek ahead to see if it's a method
						if (t.check('(')) {
							return getMethodCall(s, word);
						} else {
							// if not a method, must be a valid location
							return "PUSHOFF " + getFBROffset(s, word) + '\n';
						}
				}
			case OPERATOR:
				String expressionCode = "";
				assertAndConsume('(');
				char operator;

				if (t.peekAtKind() == TokenType.OPERATOR) {
					// unary operation
					operator = t.getOp();
					switch (operator) {
						case '-':
							expressionCode = getExpression(s) + "PUSHIMM -1\nTIMES\n";
							break;
						case '!':
							expressionCode = getExpression(s) + "NOT\n";
							break;
						default:
							throw new Error("Error parsing expression; unexpected operator `" + operator + "` on line " + t.lineNo());
					}
				} else {
					// binary operation, or "just an expression"
					expressionCode = getExpression(s);
					operator = t.getOp();
					switch (operator) {
						case '+':
							expressionCode += getExpression(s) + "ADD\n";
							break;
						case '-':
							expressionCode += getExpression(s) + "SUB\n";
							break;
						case '*':
							expressionCode += getExpression(s) + "TIMES\n";
							break;
						case '/':
							expressionCode += getExpression(s) + "DIV\n";
							break;
						case '&':
							expressionCode += getExpression(s) + "AND\n";
							break;
						case '|':
							expressionCode += getExpression(s) + "OR\n";
							break;
						case '<':
							expressionCode += getExpression(s) + "LESS\n";
							break;
						case '>':
							expressionCode += getExpression(s) + "GREATER\n";
							break;
						case '=':
							expressionCode += getExpression(s) + "EQUAL\n";
							break;
						case ')':
							// already consumed the right paren, so just return
							return expressionCode;
						default:
							throw new Error("Error parsing expression; unexpected operator `" + operator + "` on line " + t.lineNo());
					}
				}
				assertAndConsume(')');
				return expressionCode;
			default:
				throw new Error("Error parsing expression; unexpected token on line " + t.lineNo());
		}
	}
	
	// called after having already consumed the left parenthesis; returns SaM
	private String getMethodCall(BaliSymbolTable s, String methodID) {
		String methodCallCode = "PUSHIMM 0\n"; // slot for return value
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
				throw new Error("EOF encountered while parsing actuals of a method on " + t.lineNo());
			}
			if (t.test(')')) break;

			methodCallCode += getExpression(s);
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
		
		methodCallCode += "LINK\n";
		methodCallCode += "JSR " + methodLabel + '\n';
		methodCallCode += "POPFBR\n";
		methodCallCode += "ADDSP " + (-numParameters) + '\n';

		return methodCallCode;
	}

	// ===============================
	// the following are utility/helper functions
	// ===============================
	private void assertAndConsume(String word) {
		if (!t.check(word))
			// TODO: print the unexpected token
			throw new Error("Expected token " + word + " on line " + t.lineNo());
	}

	private void assertAndConsume(char c) {
		if (!t.check(c))
			// TODO: print the unexpected token
			throw new Error("Expected token " + c + " on line " + t.lineNo());
	}

	// we make every label unique by appending a unique suffix to each
	// given label prefix (e.g., `main` would become `main_0`)
	private String generateLabel(String prefix) {
		return prefix + "_" + labelCount++;
	}

	// get the FBR offset for a symbol; throws error if not found
	private int getFBROffset(BaliSymbolTable s, String symbol) {
		int FBROffset = s.resolveAddress(symbol);
		if (FBROffset == 0) {
			throw new Error("Error parsing location reference; " + symbol + " is not a valid variable, found on line " + t.lineNo());
		}
		return FBROffset;
	}
}

class MethodInfo {
	public String methodLabel = null;
	public int numParameters = -1;
}
