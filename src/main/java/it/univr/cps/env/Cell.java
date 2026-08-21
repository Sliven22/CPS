package it.univr.cps.env;

import it.univr.cps.lazyeval.Thunk;
import it.univr.cps.type.ExpType;
import it.univr.cps.value.ExpValue;

/**
 * Il contenitore di un valore modificabile: una variabile, un parametro, oppure un elemento di array.
 * <p>
 * Introdurre questo livello di indirezione risolve tre problemi con un'unica astrazione:
 * <ul>
 *   <li>il <b>passaggio per riferimento</b> consiste nel condividere la cella con il chiamante;</li>
 *   <li>gli <b>array</b> sono vettori di celle, quindi indicizzabili e assegnabili elemento per elemento;</li>
 *   <li>la <b>valutazione pigra</b> mette nella cella un {@link Thunk} al posto di un valore, e la
 *       lettura lo forza in modo trasparente.</li>
 * </ul>
 */
public final class Cell {

    private final ExpType type;
    private ExpValue<?> value;
    private Thunk thunk;

    private Cell(ExpType type, ExpValue<?> value, Thunk thunk) {
        this.type = type;
        this.value = value;
        this.thunk = thunk;
    }

    public static Cell of(ExpType type, ExpValue<?> value) {
        return new Cell(type, value, null);
    }

    public static Cell lazyOf(ExpType type, Thunk thunk) {
        return new Cell(type, null, thunk);
    }

    public ExpType getType() {
        return type;
    }

    /** Lettura: se la cella e' pigra e non ancora forzata, la forza adesso e memoizza il risultato. */
    public ExpValue<?> get() {
        if (thunk != null) {
            value = thunk.force();
            thunk = null;
        }
        return value;
    }

    public void set(ExpValue<?> newValue) {
        this.value = newValue;
        this.thunk = null; // un assegnamento normale annulla la sospensione precedente
    }

    public void setLazy(Thunk newThunk) {
        this.value = null;
        this.thunk = newThunk;
    }

    /** true se la cella contiene una computazione ancora sospesa. */
    public boolean isPending() {
        return thunk != null;
    }

    /**
     * Copia usata per la cattura per valore dei thunk. La nuova cella e' indipendente da questa —
     * assegnamenti successivi all'originale non la toccano — ma se il valore e' ancora sospeso le
     * due celle <em>condividono lo stesso thunk</em>, cosi' la pigrizia si propaga senza forzare
     * nulla al momento della cattura e la memoizzazione resta unica.
     */
    public Cell snapshot() {
        return new Cell(type, value, thunk);
    }

    @Override
    public String toString() {
        return thunk != null ? "<sospeso>" : String.valueOf(value);
    }
}
