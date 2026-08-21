package it.univr.cps.value;

/** L'esito di un comando eseguito con successo: unico valore possibile, quindi un singoletto. */
public final class ComValue extends Value {

    public static final ComValue INSTANCE = new ComValue();

    private ComValue() { }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ComValue;
    }

    @Override
    public int hashCode() {
        return ComValue.class.hashCode();
    }

    @Override
    public String toString() {
        return "com";
    }
}
