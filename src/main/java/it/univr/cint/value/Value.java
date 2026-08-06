package it.univr.cint.value;

/** Radice dei valori prodotti dall'interprete: valori di espressione oppure esito di un comando. */
public abstract class Value {

    @Override
    public abstract int hashCode();

    @Override
    public abstract boolean equals(Object obj);
}
