package it.univr.cps.value;

import it.univr.cps.env.Cell;
import it.univr.cps.error.CPSRuntimeError;
import it.univr.cps.type.ExpType;

import java.util.StringJoiner;

/**
 * Un array di CPS: un vettore di {@link Cell}, non di valori.
 * <p>
 * Sono le celle a rendere assegnabile il singolo elemento ({@code a[i] = v}, {@code a[i]++}) e a
 * permettere che un elemento resti sospeso se prodotto da un'espressione pigra.
 * <p>
 * La semantica e' <b>a riferimento</b>: {@code b = a} non copia nulla, i due nomi denotano lo stesso
 * array, e passare un array a una funzione lo espone alle modifiche del chiamato. Coerentemente,
 * {@code ==} confronta l'identita' dei riferimenti, non il contenuto — comportamento che si ottiene
 * gratuitamente dall'uguaglianza di {@code Cell[]}, che e' per identita'.
 */
public final class ArrayValue extends ExpValue<Cell[]> {

    private final ExpType elementType;

    public ArrayValue(ExpType elementType, Cell[] cells) {
        super(cells);
        this.elementType = elementType;
    }

    public ExpType getElementType() {
        return elementType;
    }

    public int length() {
        return toValue().length;
    }

    /** Accesso con controllo dei limiti: fuori intervallo e' un errore runtime catturabile. */
    public Cell cell(int index) {
        Cell[] cells = toValue();
        if (index < 0 || index >= cells.length)
            throw new CPSRuntimeError(
                    "indice " + index + " fuori dai limiti dell'array (lunghezza " + cells.length + ")");
        return cells[index];
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (Cell cell : toValue())
            joiner.add(String.valueOf(cell.get()));
        return joiner.toString();
    }
}
