package it.univr.cint.type;

/** Il tipo di ritorno delle procedure, cioe' delle funzioni dichiarate {@code void}. */
public final class VoidType implements Type {

    public static final VoidType INSTANCE = new VoidType();

    private VoidType() { }

    @Override
    public boolean accepts(Type source) {
        return source instanceof VoidType;
    }

    @Override
    public String getName() {
        return "void";
    }
}
