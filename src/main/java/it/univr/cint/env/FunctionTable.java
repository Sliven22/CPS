package it.univr.cint.env;

import it.univr.cint.CINTParser;
import it.univr.cint.error.DeclarationError;
import it.univr.cint.type.ExpType;
import it.univr.cint.type.Type;
import it.univr.cint.type.TypeUtils;
import it.univr.cint.type.VoidType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le firme di tutte le funzioni del programma, raccolte in una passata preliminare.
 * <p>
 * La passata preliminare non e' un dettaglio implementativo ma una necessita': serve perche' una
 * funzione possa chiamarne un'altra definita piu' avanti nel file, e in particolare perche' due
 * funzioni possano chiamarsi <b>a vicenda</b>. Se le firme venissero raccolte durante il type
 * checking, la prima delle due non troverebbe la seconda.
 */
public final class FunctionTable {

    /** Un parametro formale. {@code byRef} distingue il passaggio per riferimento da quello per valore. */
    public record Param(String name, ExpType type, boolean byRef) { }

    /** La firma di una funzione, con il puntatore al nodo sintattico del corpo. */
    public record Signature(String name,
                            Type returnType,
                            List<Param> params,
                            CINTParser.FunDeclContext declaration) {

        public boolean isVoid() {
            return returnType instanceof VoidType;
        }

        public int arity() {
            return params.size();
        }
    }

    private final Map<String, Signature> functions = new LinkedHashMap<>();

    /** Esegue la passata preliminare sull'intero programma. */
    public static FunctionTable collect(CINTParser.ProgramContext program) {
        FunctionTable table = new FunctionTable();

        for (CINTParser.FunDeclContext decl : program.funDecl()) {
            String name = decl.ID().getText();

            if (table.functions.containsKey(name))
                throw new DeclarationError("funzione '" + name + "' dichiarata piu' di una volta", decl);

            table.functions.put(name, new Signature(name, returnTypeOf(decl), paramsOf(decl), decl));
        }

        return table;
    }

    private static Type returnTypeOf(CINTParser.FunDeclContext decl) {
        CINTParser.RetTypeContext retType = decl.retType();
        return retType.VOID() != null ? VoidType.INSTANCE : TypeUtils.fromContext(retType.type());
    }

    private static List<Param> paramsOf(CINTParser.FunDeclContext decl) {
        List<Param> params = new ArrayList<>();
        if (decl.params() == null) return params;

        Set<String> seen = new HashSet<>();
        for (CINTParser.ParamContext param : decl.params().param()) {
            String name = param.ID().getText();

            if (!seen.add(name))
                throw new DeclarationError("parametro '" + name + "' ripetuto nella funzione '"
                        + decl.ID().getText() + "'", param);

            params.add(new Param(name, TypeUtils.fromContext(param.type()), param.REF() != null));
        }
        return params;
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    public Signature get(String name) {
        return functions.get(name);
    }

    public Collection<Signature> all() {
        return functions.values();
    }
}
