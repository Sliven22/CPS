package it.univr.cint.error;

import it.univr.cint.value.ExpValue;

/**
 * Non e' un errore ma un salto di controllo: trasporta il valore di {@code return} dal punto in cui
 * compare fino alla chiamata di funzione che lo ha originato, attraversando blocchi e cicli annidati.
 * <p>
 * Il visitor ANTLR restituisce un valore per ogni nodo visitato e non ha modo di interrompere una
 * visita a meta': l'eccezione e' il modo idiomatico di implementare un'uscita anticipata in un
 * interprete scritto con il pattern visitor.
 */
public class ReturnSignal extends RuntimeException {

    private final transient ExpValue<?> value;

    public ReturnSignal(ExpValue<?> value) {
        super(null, null, false, false); // niente stack trace: e' controllo di flusso, non un errore
        this.value = value;
    }

    /** Il valore restituito, oppure {@code null} per un {@code return} senza espressione. */
    public ExpValue<?> getValue() {
        return value;
    }
}
