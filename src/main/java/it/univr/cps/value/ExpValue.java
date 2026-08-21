package it.univr.cps.value;

/**
 * Valore di un'espressione, involucro tipizzato attorno al corrispondente valore Java.
 * <p>
 * L'uguaglianza e' delegata al valore incapsulato: per i tipi primitivi questo da' l'uguaglianza
 * strutturale attesa, mentre per gli array {@code Cell[]} usa l'identita' del riferimento — ed e'
 * esattamente la semantica voluta, dato che in CPS gli array sono riferimenti.
 */
public abstract class ExpValue<T> extends Value {

    private final T value;

    protected ExpValue(T value) {
        this.value = value;
    }

    public T toValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return value.equals(((ExpValue<?>) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
