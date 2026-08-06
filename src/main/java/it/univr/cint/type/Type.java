package it.univr.cint.type;

/**
 * Un tipo di CINT.
 * <p>
 * La relazione di sottotipaggio e' espressa da {@link #accepts(Type)}, il cui verso e' fissato una
 * volta per tutte: <em>il ricevente e' il tipo atteso, l'argomento e' il tipo trovato</em>. Cosi'
 * {@code DEC.accepts(INT)} e' vero (un intero sta dove serve un decimale, con upcast implicito)
 * mentre {@code INT.accepts(DEC)} e' falso (serve un cast esplicito).
 */
public interface Type {

    /** true se un valore di tipo {@code source} e' utilizzabile dove serve un valore di questo tipo. */
    boolean accepts(Type source);

    /** Il nome con cui il tipo compare nei messaggi d'errore e nel codice sorgente. */
    String getName();
}
