package it.univr.cint.error;

/**
 * Errore lessicale o sintattico. Il parser generato da ANTLR, di default, si limita a segnalare
 * l'errore su stderr e prosegue con il recupero: qui invece l'analisi viene interrotta subito,
 * cosi' un programma malformato non arriva mai al type system.
 */
public class SyntaxError extends RuntimeException {

    public SyntaxError(String message) {
        super(message);
    }
}
