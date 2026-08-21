package it.univr.cps.value;

/** Valore numerico: raccoglie {@link IntValue} e {@link RealValue} sotto un unico tipo statico. */
public abstract class NumValue<T extends Number> extends ExpValue<T> {

    protected NumValue(T value) {
        super(value);
    }

    /** Il valore promosso a {@code double}, per le operazioni miste {@code int}/{@code real}. */
    public double asDouble() {
        return toValue().doubleValue();
    }
}
