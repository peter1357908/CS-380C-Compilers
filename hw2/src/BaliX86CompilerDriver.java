import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BaliX86CompilerDriver {
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println(
          "This compiler requires 2 input parameters; first one is the Bali file name, and the second one the target NASM file name.\n");
      return;
    }

    String Bali_filename = args[0];
    String NASM_filename = args[1];

    BaliX86Compiler compiler = new BaliX86Compiler();
    String NASM_Program = compiler.compile(Bali_filename);
    Files.writeString(Path.of(NASM_filename), NASM_Program);
  }
}
