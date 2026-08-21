package it.univr.cps.lazyeval;

import it.univr.cps.error.CPSRuntimeError;
import it.univr.cps.value.ExpValue;

import java.util.function.Supplier;

/**
 * Una computazione sospesa: l'espressione di un assegnamento pigro, non ancora valutata, insieme a
 * tutto cio' che serve per valutarla.
 * <p>
 * Il contesto necessario e' catturato dentro la {@code Supplier} costruita dall'interprete, che
 * chiude su una <em>istantanea</em> delle variabili libere dell'espressione. Il thunk e' quindi
 * autocontenuto: puo' sopravvivere allo scope in cui e' nato senza riferirsi a variabili sparite.
 * <p>
 * Il risultato viene memoizzato al primo forzamento, cosi' un'espressione pigra usata piu' volte
 * viene calcolata una volta sola.
 */
public final class Thunk {

    private Supplier<ExpValue<?>> computation;
    private ExpValue<?> result;
    private boolean forced;
    private boolean forcing;

    public Thunk(Supplier<ExpValue<?>> computation) {
        this.computation = computation;
    }

    public ExpValue<?> force() {
        if (forced) return result;

        if (forcing) // rete di sicurezza: una dipendenza pigra circolare non deve ciclare all'infinito
            throw new CPSRuntimeError("dipendenza circolare fra espressioni pigre");

        forcing = true;
        try {
            result = computation.get();
        } finally {
            forcing = false;
        }

        forced = true;
        computation = null; // l'istantanea catturata non serve piu': si lascia liberare la memoria
        return result;
    }

    public boolean isForced() {
        return forced;
    }
}
