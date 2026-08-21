package it.univr.cps.type;

/** Il tipo dei comandi: un comando e' ben tipato oppure non lo e', non produce alcun valore. */
public final class ComType implements Type {

    public static final ComType INSTANCE = new ComType();

    private ComType() { }

    @Override
    public boolean accepts(Type source) {
        return source instanceof ComType;
    }

    @Override
    public String getName() {
        return "com";
    }
}
