import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BaliCompilerDriver {
  public static void main(String[] args) throws IOException {
    if (args.length != 2) {
      System.out.println(
          "This compiler requires 2 input parameters; first one is the Bali file name, and the second one the target SaM file name.\n");
      return;
    }

    String Bali_filename = args[0];
    String SaM_filename = args[1];

    BaliCompiler compiler = new BaliCompiler();
    String SaMProgram = compiler.compile(Bali_filename);
    Files.writeString(Path.of(SaM_filename), SaMProgram);
  }
}
