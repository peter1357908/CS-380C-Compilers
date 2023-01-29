import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;

public class BaliCompiler
{
	static String compiler(String fileName) 
	{
		//returns SaM code for program in file
		try 
		{
			SamTokenizer f = new SamTokenizer (fileName);
			String pgm = getProgram(f);
			return pgm;
		} 
		catch (Exception e) 
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
	}
	static String getProgram(SamTokenizer f)
	{
		try
		{
			String pgm="";
			while(f.peekAtKind()!=TokenType.EOF)
			{
				pgm+= getMethod(f);
			}
			return pgm;
		}
		catch(Exception e)
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}		
	}
	static String getMethod(SamTokenizer f)
	{
		//TODO: add code to convert a method declaration to SaM code.
		//Since the only data type is an int, you can safely check for int 
		//in the tokenizer.
		//TODO: add appropriate exception handlers to generate useful error msgs.
		f.check("int"); //must match at begining
		String methodName = f.getString(); 
		f.check ("("); // must be an opening parenthesis
		String formals = getFormals(f);
		f.check(")");  // must be an closing parenthesis
		//You would need to read in formals if any
		//And then have calls to getDeclarations and getStatements.
		return null;
	}
	static String getExp(SamTokenizer f) 
	{
			  switch (f.peekAtKind()) {
				 case INTEGER: //E -> integer
					return "PUSHIMM " + f.getInt() + "\n";
				 case OPERATOR:  
				 {
				 }
				 default:   return "ERROR\n";
			  }
	}
	static String getFormals(SamTokenizer f){
			return null;
	}
}
