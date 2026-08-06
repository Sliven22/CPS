package it.univr.cint.error;

import org.antlr.v4.runtime.ParserRuleContext;

/** Incompatibilita' fra il tipo atteso e il tipo trovato. */
public class TypeError extends StaticError {

    public TypeError(String message, ParserRuleContext ctx) {
        super(message, ctx);
    }
}
