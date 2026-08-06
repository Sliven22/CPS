package it.univr.cint.error;

/**
 * Errore a tempo d'esecuzione, sollevato dall'interprete oppure dal comando {@code throw}.
 * <p>
 * A differenza di {@link StaticError} questo errore e' <em>catturabile dal programma CINT</em>
 * tramite {@code try { ... } catch (e) { ... }}: e' il meccanismo con cui il linguaggio soddisfa
 * il requisito di gestire in modo programmatico gli errori a tempo d'esecuzione.
 */
public class CINTRuntimeError extends RuntimeException {

    public CINTRuntimeError(String message) {
        super(message);
    }
}
