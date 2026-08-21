package it.univr.cps.type;

/**
 * I cinque tipi primitivi di CPS. Tre di essi ({@code int}, {@code real}, {@code char}) sono
 * "oltre {@code bool} e {@code string}", come richiesto dai requisiti minimi del linguaggio.
 * <p>
 * Le conversioni numeriche fra {@code int} e {@code real} sono implicite, come richiesto dalla
 * sintassi CPS; le altre conversioni fra primitivi richiedono un cast esplicito.
 */
public enum SimpleType implements ExpType {

    INT("int"),
    REAL("real"),
    CHAR("char"),
    BOOL("bool"),
    STRING("string");

    private final String name;

    SimpleType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean accepts(Type source) {
        if (this == source) return true;
        return (this == REAL && source == INT) || (this == INT && source == REAL);
    }

    @Override
    public String toString() {
        return name;
    }
}
