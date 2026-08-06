package it.univr.cint.error;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Sostituisce il listener di default di ANTLR, che stampa l'errore su stderr e prosegue con il
 * recupero dell'errore. Qui il primo problema lessicale o sintattico interrompe subito l'analisi:
 * un programma malformato non deve arrivare al type system con un albero rattoppato.
 */
public final class CINTErrorListener extends BaseErrorListener {

    public static final CINTErrorListener INSTANCE = new CINTErrorListener();

    private CINTErrorListener() { }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer,
                            Object offendingSymbol,
                            int line,
                            int charPositionInLine,
                            String msg,
                            RecognitionException e) {
        throw new SyntaxError(msg + " @" + line + ":" + charPositionInLine);
    }
}
