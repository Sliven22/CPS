package it.univr.cps.type;

/**
 * Un tipo di CPS.
 * <p>
 * La relazione di sottotipaggio e' espressa da {@link #accepts(Type)}, il cui verso e' fissato una
 * volta per tutte: <em>il ricevente e' il tipo atteso, l'argomento e' il tipo trovato</em>. Cosi'
 * {@code REAL.accepts(INT)} e' vero e {@code INT.accepts(REAL)} e' vero: CPS consente entrambe le
 * conversioni numeriche implicite, con troncamento quando un {@code real} viene assegnato a {@code int}.
 */
public interface Type {

    /** true se un valore di tipo {@code source} e' utilizzabile dove serve un valore di questo tipo. */
    boolean accepts(Type source);

    /** Il nome con cui il tipo compare nei messaggi d'errore e nel codice sorgente. */
    String getName();
}
