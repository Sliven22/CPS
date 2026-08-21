package it.univr.cps.error;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Sostituisce il listener di default di ANTLR, che stampa l'errore su stderr e prosegue con il
 * recupero dell'errore. Qui il primo problema lessicale o sintattico interrompe subito l'analisi:
 * un programma malformato non deve arrivare al type system con un albero rattoppato.
 */
public final class CPSErrorListener extends BaseErrorListener {

    public static final CPSErrorListener INSTANCE = new CPSErrorListener();

    private CPSErrorListener() { }

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
