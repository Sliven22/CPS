package it.univr.cint.error;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Errore rilevato prima dell'esecuzione, dal type system. Non e' catturabile dal programma CINT:
 * un programma che contiene un errore statico non viene eseguito affatto.
 */
public class StaticError extends RuntimeException {

    public StaticError(String message) {
        super(message);
    }

    public StaticError(String message, ParserRuleContext ctx) {
        super(message + at(ctx));
    }

    protected static String at(ParserRuleContext ctx) {
        if (ctx == null) return "";
        return " @" + ctx.start.getLine() + ":" + ctx.start.getCharPositionInLine();
    }
}
