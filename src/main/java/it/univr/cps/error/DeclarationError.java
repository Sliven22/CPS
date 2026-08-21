package it.univr.cps.error;

import org.antlr.v4.runtime.ParserRuleContext;

/** Variabile o funzione usata senza dichiarazione, oppure dichiarata due volte nello stesso scope. */
public class DeclarationError extends StaticError {

    public DeclarationError(String message, ParserRuleContext ctx) {
        super(message, ctx);
    }
}
