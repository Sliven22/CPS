package it.univr.cint.value;

/** Valore numerico: raccoglie {@link IntValue} e {@link DecValue} sotto un unico tipo statico. */
public abstract class NumValue<T extends Number> extends ExpValue<T> {

    protected NumValue(T value) {
        super(value);
    }

    /** Il valore promosso a {@code double}, per le operazioni miste {@code int}/{@code dec}. */
    public double asDouble() {
        return toValue().doubleValue();
    }
}
