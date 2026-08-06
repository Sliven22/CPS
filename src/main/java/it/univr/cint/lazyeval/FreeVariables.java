package it.univr.cint.lazyeval;

import it.univr.cint.CINTBaseVisitor;
import it.univr.cint.CINTParser;
import it.univr.cint.interpolation.StringInterpolation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Raccoglie i nomi di variabile che compaiono liberi in un'espressione.
 * <p>
 * Serve alla valutazione pigra: al momento di sospendere un'espressione si fotografano soltanto le
 * variabili che quell'espressione usera' davvero, invece di copiare l'intero ambiente visibile.
 * <p>
 * Due casi meritano attenzione. Il nome di una funzione in {@code f(x)} <em>non</em> e' una variabile
 * libera, quindi si visitano solo gli argomenti. E le espressioni interpolate dentro le stringhe non
 * sono nodi dell'albero — vivono dentro un token {@code STRING} — quindi vanno espanse esplicitamente,
 * altrimenti la {@code x} di {@code "${x}"} sfuggirebbe alla cattura.
 */
public final class FreeVariables extends CINTBaseVisitor<Void> {

    private final Set<String> names = new LinkedHashSet<>();

    private FreeVariables() { }

    /** I nomi liberi dell'espressione, nell'ordine in cui compaiono. */
    public static Set<String> of(CINTParser.ExpContext exp) {
        FreeVariables collector = new FreeVariables();
        collector.visit(exp);
        return collector.names;
    }

    @Override
    public Void visitId(CINTParser.IdContext ctx) {
        names.add(ctx.ID().getText());
        return null;
    }

    @Override
    public Void visitVarLvalue(CINTParser.VarLvalueContext ctx) {
        names.add(ctx.ID().getText());
        return null;
    }

    @Override
    public Void visitCall(CINTParser.CallContext ctx) {
        if (ctx.args() != null) visit(ctx.args()); // il nome della funzione non e' una variabile
        return null;
    }

    @Override
    public Void visitString(CINTParser.StringContext ctx) {
        for (StringInterpolation.Part part : StringInterpolation.parse(ctx.STRING().getText()))
            if (part instanceof StringInterpolation.Interpolated interpolated)
                visit(interpolated.exp());
        return null;
    }
}
