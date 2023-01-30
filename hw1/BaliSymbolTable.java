import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * SymbolTable implementation modified from SamSymbolTable
 * the only change is resolveAddress returns `0` instead of `-1`
 * to indicate that a symbol is not found (since BaliSymbolTable
 * is only used for the FBR offset of parameters and local variables;
 * such an offset cannot be 0, which would be pointing to the FBR itself
 */
public class BaliSymbolTable implements edu.cornell.cs.sam.core.SymbolTable, java.io.Serializable {
  private HashMap<String, Integer> sym2adr = new HashMap<String, Integer>();
  private HashMap<Integer, ArrayList<String>> adr2sym = new HashMap<Integer, ArrayList<String>>();

  public void add(String symbol, int address) {
    Integer adr = new Integer(address);
    sym2adr.put(symbol, adr);

    ArrayList<String> v;
    if (adr2sym.containsKey(adr)) {
      v = adr2sym.get(adr);
      if (v.contains(symbol) == false)
        v.add(symbol);
    }

    else {
      v = new ArrayList<String>();
      v.add(symbol);
      adr2sym.put(adr, v);
    }
  }

  public Collection<String> resolveSymbols(int address) {
    return adr2sym.get(address);
  }

  public String resolveSymbol(int address) {
    ArrayList<String> labels = adr2sym.get(address);
    return (labels != null) ? labels.get(0) : null;
  }

  public int resolveAddress(String label) {
    Integer addr = sym2adr.get(label);
    return (addr != null) ? addr : 0;
  }

  public Collection<String> getSymbols() {
    return sym2adr.keySet();
  }

  public String toString() {
    return sym2adr.toString();
  }
}
