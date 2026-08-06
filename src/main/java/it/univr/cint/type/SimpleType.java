package it.univr.cint.type;

/**
 * I cinque tipi primitivi di CINT. Tre di essi ({@code int}, {@code dec}, {@code char}) sono
 * "oltre {@code bool} e {@code string}", come richiesto dai requisiti minimi del linguaggio.
 * <p>
 * L'unica relazione di sottotipaggio e' {@code int <: dec}: e' l'unico upcast implicito. Ogni altra
 * conversione fra tipi primitivi richiede un cast esplicito, e {@code bool} e {@code string} non
 * partecipano ad alcuna conversione.
 */
public enum SimpleType implements ExpType {

    INT("int"),
    DEC("dec"),
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
        return this == DEC && source == INT; // unico upcast implicito
    }

    @Override
    public String toString() {
        return name;
    }
}
