package it.univr.cps.error;

/**
 * Errore a tempo d'esecuzione, sollevato dall'interprete oppure dal comando {@code throw}.
 * <p>
 * A differenza di {@link StaticError} questo errore e' <em>catturabile dal programma CPS</em>
 * tramite {@code try: ... catch (e): ... end}: e' il meccanismo con cui il linguaggio soddisfa
 * il requisito di gestire in modo programmatico gli errori a tempo d'esecuzione.
 */
public class CPSRuntimeError extends RuntimeException {

    public CPSRuntimeError(String message) {
        super(message);
    }
}
