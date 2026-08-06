package it.univr.cint;

import it.univr.cint.env.FunctionTable;
import it.univr.cint.env.Scope;
import it.univr.cint.error.DeclarationError;
import it.univr.cint.error.TypeError;
import it.univr.cint.interpolation.StringInterpolation;
import it.univr.cint.type.*;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.List;

/**
 * Type system di CINT: visita l'albero sintattico e assegna un tipo a ogni nodo, oppure fallisce.
 * <p>
 * CINT e' tipizzato <b>staticamente</b>: l'interprete parte solo se questa visita si conclude senza
 * errori, e puo' quindi assumere che ogni operazione riceva operandi del tipo giusto senza doverlo
 * ricontrollare. Gli unici controlli lasciati a runtime sono quelli che dipendono dai valori e non
 * dai tipi: divisione per zero, limiti degli array, profondita' della ricorsione.
 * <p>
 * L'ambiente e' una {@link Scope} di tipi che rispecchia esattamente la catena di scope usata
 * dall'interprete: le due visite entrano ed escono dai blocchi negli stessi punti, quindi un nome
 * risolto qui denota a runtime la stessa dichiarazione.
 */
public final class CINTTypeSystem extends CINTBaseVisitor<Type> {

    private final FunctionTable functions;

    private Scope<ExpType> scope = new Scope<>();

    /** Tipo di ritorno della funzione in corso di controllo; null nel corpo principale. */
    private Type returnType;

    public CINTTypeSystem(FunctionTable functions) {
        this.functions = functions;
    }

    // ------------------------------------------------------------------ ingresso

    /** Controlla l'intero programma: prima i corpi delle funzioni, poi il comando principale. */
    public void check(CINTParser.ProgramContext program) {
        for (FunctionTable.Signature signature : functions.all())
            checkFunction(signature);

        scope = new Scope<>();
        returnType = null;
        visit(program.com());
    }

    /**
     * Le funzioni di CINT sono <b>chiuse rispetto allo stato globale</b>: il corpo parte da uno scope
     * radice nuovo, che contiene i soli parametri. Comunicano con il resto del programma unicamente
     * attraverso parametri e valore di ritorno.
     */
    private void checkFunction(FunctionTable.Signature signature) {
        scope = new Scope<>();
        returnType = signature.returnType();

        for (FunctionTable.Param param : signature.params())
            scope.declare(param.name(), param.type());

        CINTParser.BlockContext body = signature.declaration().block();
        if (body.com() != null) {
            scope = scope.push();
            visit(body.com());
            scope = scope.pop();
        }

        if (!signature.isVoid() && !alwaysReturns(body.com()))
            throw new TypeError("la funzione '" + signature.name() + "' di tipo "
                    + signature.returnType().getName() + " puo' terminare senza return",
                    signature.declaration());
    }

    /**
     * Controllo strutturale conservativo: true solo se ogni cammino di esecuzione del comando incontra
     * un {@code return}. Non si tiene conto del valore delle condizioni, quindi un {@code while (true)}
     * senza return conta come possibile uscita: e' un'approssimazione dal lato sicuro, che al massimo
     * rifiuta qualche programma corretto ma non ne accetta nessuno scorretto.
     */
    /** Una sequenza garantisce il return se lo garantisce uno qualunque dei comandi che la compongono. */
    private boolean alwaysReturns(CINTParser.ComContext ctx) {
        if (ctx == null) return false;

        boolean head = ctx.simpleCom() != null
                ? alwaysReturns(ctx.simpleCom())
                : alwaysReturns(ctx.closedCom());

        return head || alwaysReturns(ctx.com());
    }

    /** {@code return} esce per definizione, {@code throw} esce comunque dalla funzione. */
    private boolean alwaysReturns(CINTParser.SimpleComContext ctx) {
        return ctx instanceof CINTParser.ReturnContext || ctx instanceof CINTParser.ThrowContext;
    }

    private boolean alwaysReturns(CINTParser.ClosedComContext ctx) {
        return switch (ctx) {
            case CINTParser.IfElseContext ifElse ->
                    alwaysReturns(ifElse.block(0).com()) && alwaysReturns(ifElse.block(1).com());
            case CINTParser.TryCatchContext tryCatch ->
                    alwaysReturns(tryCatch.block(0).com()) && alwaysReturns(tryCatch.block(1).com());
            case CINTParser.BlockComContext block -> alwaysReturns(block.block().com());
            // un 'if' senza else e un 'while' possono non entrare mai nel corpo
            default -> false;
        };
    }

    // ------------------------------------------------------------------ utilita'

    /** Tipo di un'espressione, con il controllo che sia davvero un valore (non void, non comando). */
    private ExpType typeOf(CINTParser.ExpContext ctx) {
        Type type = visit(ctx);
        if (!(type instanceof ExpType expType))
            throw new TypeError("qui serve un'espressione con un valore, trovato "
                    + type.getName(), ctx);
        return expType;
    }

    private void require(SimpleType expected, CINTParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!expected.accepts(actual))
            throw new TypeError("atteso " + expected.getName() + ", trovato " + actual.getName(), ctx);
    }

    private ExpType requireNumeric(CINTParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!TypeUtils.isNumeric(actual))
            throw new TypeError("attesa un'espressione numerica, trovato " + actual.getName(), ctx);
        return actual;
    }

    private ArrayType requireArray(CINTParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!(actual instanceof ArrayType arrayType))
            throw new TypeError("atteso un array, trovato " + actual.getName(), ctx);
        return arrayType;
    }

    /** Visita un blocco introducendo il suo scope: e' qui che nasce la visibilita' riservata. */
    private void visitBlockScoped(CINTParser.BlockContext block) {
        if (block.com() == null) return;
        scope = scope.push();
        visit(block.com());
        scope = scope.pop();
    }

    private ExpType lookup(String id, ParserRuleContext ctx) {
        ExpType type = scope.lookup(id);
        if (type == null)
            throw new DeclarationError("variabile '" + id + "' non dichiarata", ctx);
        return type;
    }

    private void declare(String id, ExpType type, ParserRuleContext ctx) {
        if (scope.declaredHere(id))
            throw new DeclarationError("variabile '" + id + "' gia' dichiarata in questo blocco", ctx);
        scope.declare(id, type);
    }

    /** Toglie le parentesi superflue, per riconoscere gli lvalue passati a un parametro {@code ref}. */
    private static CINTParser.ExpContext unwrap(CINTParser.ExpContext ctx) {
        while (ctx instanceof CINTParser.ParExpContext parenthesised)
            ctx = parenthesised.exp();
        return ctx;
    }

    // ------------------------------------------------------------------ comandi

    @Override
    public Type visitDecl(CINTParser.DeclContext ctx) {
        ExpType declared = TypeUtils.fromContext(ctx.type());
        String id = ctx.ID().getText();

        // l'inizializzatore si tipa PRIMA della dichiarazione, cosi' 'int x = x' e' un errore
        if (ctx.exp() != null) {
            ExpType actual = typeOf(ctx.exp());
            if (!declared.accepts(actual))
                throw new TypeError("'" + id + "' e' di tipo " + declared.getName()
                        + " e non puo' essere inizializzata con " + actual.getName(), ctx);
        }

        declare(id, declared, ctx);
        return ComType.INSTANCE;
    }

    @Override
    public Type visitLazyDecl(CINTParser.LazyDeclContext ctx) {
        ExpType declared = TypeUtils.fromContext(ctx.type());
        String id = ctx.ID().getText();

        // Il controllo di tipo di un assegnamento pigro avviene alla dichiarazione, esattamente come
        // per uno normale: la pigrizia riguarda quando si calcola, non quando si controlla.
        ExpType actual = typeOf(ctx.exp());
        if (!declared.accepts(actual))
            throw new TypeError("'" + id + "' e' di tipo " + declared.getName()
                    + " e non puo' essere inizializzata pigramente con " + actual.getName(), ctx);

        declare(id, declared, ctx);
        return ComType.INSTANCE;
    }

    @Override
    public Type visitAssign(CINTParser.AssignContext ctx) {
        ExpType target = (ExpType) visit(ctx.lvalue());
        ExpType actual = typeOf(ctx.exp());

        if (!target.accepts(actual))
            throw new TypeError("non si puo' assegnare " + actual.getName()
                    + " a un bersaglio di tipo " + target.getName(), ctx);

        return ComType.INSTANCE;
    }

    @Override
    public Type visitCompoundAssign(CINTParser.CompoundAssignContext ctx) {
        ExpType target = (ExpType) visit(ctx.lvalue());
        ExpType operand = typeOf(ctx.exp());
        String op = ctx.op.getText();

        // 'x op= e' e' definito come 'x = x op e': si tipa l'operazione e poi l'assegnamento.
        ExpType result;
        if (ctx.op.getType() == CINTParser.ADD_A && target == SimpleType.STRING) {
            result = SimpleType.STRING; // concatenazione, come per '+'
        } else {
            if (!TypeUtils.isNumeric(target) || !TypeUtils.isNumeric(operand))
                throw new TypeError("l'operatore " + op + " non si applica a "
                        + target.getName() + " e " + operand.getName(), ctx);
            result = TypeUtils.arithmeticJoin(target, operand);
        }

        if (!target.accepts(result))
            throw new TypeError("il risultato di " + op + " e' " + result.getName()
                    + " e non puo' essere riassegnato a " + target.getName()
                    + ": serve un cast esplicito", ctx);

        return ComType.INSTANCE;
    }

    @Override
    public Type visitPostCrementCom(CINTParser.PostCrementComContext ctx) {
        requireNumericLvalue(ctx.lvalue(), ctx);
        return ComType.INSTANCE;
    }

    @Override
    public Type visitPreCrementCom(CINTParser.PreCrementComContext ctx) {
        requireNumericLvalue(ctx.lvalue(), ctx);
        return ComType.INSTANCE;
    }

    private ExpType requireNumericLvalue(CINTParser.LvalueContext lvalue, ParserRuleContext ctx) {
        ExpType type = (ExpType) visit(lvalue);
        if (!TypeUtils.isNumeric(type))
            throw new TypeError("++ e -- si applicano solo a int e dec, trovato "
                    + type.getName(), ctx);
        return type;
    }

    @Override
    public Type visitIf(CINTParser.IfContext ctx) {
        require(SimpleType.BOOL, ctx.exp());
        visitBlockScoped(ctx.block());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitIfElse(CINTParser.IfElseContext ctx) {
        require(SimpleType.BOOL, ctx.exp());
        visitBlockScoped(ctx.block(0));
        visitBlockScoped(ctx.block(1)); // scope indipendente da quello del ramo then
        return ComType.INSTANCE;
    }

    @Override
    public Type visitWhile(CINTParser.WhileContext ctx) {
        require(SimpleType.BOOL, ctx.exp());
        visitBlockScoped(ctx.block());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitTryCatch(CINTParser.TryCatchContext ctx) {
        visitBlockScoped(ctx.block(0));

        // la variabile del catch e' visibile solo nel blocco di gestione, e contiene il messaggio
        scope = scope.push();
        scope.declare(ctx.ID().getText(), SimpleType.STRING);
        visitBlockScoped(ctx.block(1));
        scope = scope.pop();

        return ComType.INSTANCE;
    }

    @Override
    public Type visitThrow(CINTParser.ThrowContext ctx) {
        require(SimpleType.STRING, ctx.exp());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitReturn(CINTParser.ReturnContext ctx) {
        if (returnType == null)
            throw new TypeError("return fuori dal corpo di una funzione", ctx);

        if (returnType instanceof VoidType) {
            if (ctx.exp() != null)
                throw new TypeError("una funzione void non puo' restituire un valore", ctx);
            return ComType.INSTANCE;
        }

        if (ctx.exp() == null)
            throw new TypeError("attesa un'espressione di tipo " + returnType.getName()
                    + " dopo return", ctx);

        ExpType actual = typeOf(ctx.exp());
        if (!returnType.accepts(actual))
            throw new TypeError("la funzione restituisce " + returnType.getName()
                    + ", trovato " + actual.getName(), ctx);

        return ComType.INSTANCE;
    }

    @Override
    public Type visitPrint(CINTParser.PrintContext ctx) {
        typeOf(ctx.exp()); // print accetta qualunque valore e lo converte in stringa
        return ComType.INSTANCE;
    }

    @Override
    public Type visitCallCom(CINTParser.CallComContext ctx) {
        checkCall(ctx.ID().getText(), ctx.args(), ctx);
        return ComType.INSTANCE; // il valore di ritorno, se c'e', viene scartato
    }

    @Override
    public Type visitBlockCom(CINTParser.BlockComContext ctx) {
        visitBlockScoped(ctx.block());
        return ComType.INSTANCE;
    }

    /** Sequenza: si controlla il comando in testa e poi il resto, nell'ordine in cui sono scritti. */
    @Override
    public Type visitCom(CINTParser.ComContext ctx) {
        visit(ctx.simpleCom() != null ? ctx.simpleCom() : ctx.closedCom());
        if (ctx.com() != null) visit(ctx.com());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitNop(CINTParser.NopContext ctx) {
        return ComType.INSTANCE;
    }

    // ------------------------------------------------------------------ lvalue

    @Override
    public Type visitVarLvalue(CINTParser.VarLvalueContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }

    @Override
    public Type visitCellLvalue(CINTParser.CellLvalueContext ctx) {
        Type base = visit(ctx.lvalue());
        if (!(base instanceof ArrayType arrayType))
            throw new TypeError("indicizzazione applicata a " + base.getName()
                    + ", che non e' un array", ctx);

        require(SimpleType.INT, ctx.exp());
        return arrayType.getElementType();
    }

    // ------------------------------------------------------------------ chiamate

    private Type checkCall(String name, CINTParser.ArgsContext args, ParserRuleContext ctx) {
        FunctionTable.Signature signature = functions.get(name);
        if (signature == null)
            throw new DeclarationError("funzione '" + name + "' non dichiarata", ctx);

        List<CINTParser.ExpContext> actuals = args == null ? List.of() : args.exp();
        if (actuals.size() != signature.arity())
            throw new TypeError("la funzione '" + name + "' vuole " + signature.arity()
                    + " argomenti, ne riceve " + actuals.size(), ctx);

        for (int i = 0; i < actuals.size(); i++) {
            FunctionTable.Param formal = signature.params().get(i);
            CINTParser.ExpContext actual = actuals.get(i);
            ExpType actualType = typeOf(actual);

            if (formal.byRef()) {
                // Un parametro per riferimento condivide la cella del chiamante: l'argomento deve
                // quindi essere assegnabile, e il tipo deve coincidere esattamente. Ammettere
                // l'upcast int -> dec permetterebbe alla funzione di scrivere un dec dentro una
                // variabile int del chiamante.
                CINTParser.ExpContext bare = unwrap(actual);
                boolean assignable = bare instanceof CINTParser.IdContext
                        || bare instanceof CINTParser.IndexContext;

                if (!assignable)
                    throw new TypeError("il parametro '" + formal.name() + "' di '" + name
                            + "' e' per riferimento: serve una variabile o un elemento di array",
                            actual);

                if (!formal.type().equals(actualType))
                    throw new TypeError("il parametro per riferimento '" + formal.name()
                            + "' e' di tipo " + formal.type().getName()
                            + ", trovato " + actualType.getName(), actual);
            } else if (!formal.type().accepts(actualType)) {
                throw new TypeError("il parametro '" + formal.name() + "' di '" + name
                        + "' e' di tipo " + formal.type().getName()
                        + ", trovato " + actualType.getName(), actual);
            }
        }

        return signature.returnType();
    }

    @Override
    public Type visitCall(CINTParser.CallContext ctx) {
        Type result = checkCall(ctx.ID().getText(), ctx.args(), ctx);

        if (result instanceof VoidType)
            throw new TypeError("la funzione '" + ctx.ID().getText()
                    + "' e' void e non puo' comparire in un'espressione", ctx);

        return result;
    }

    // ------------------------------------------------------------------ espressioni

    @Override
    public Type visitNumeric(CINTParser.NumericContext ctx) {
        return visit(ctx.num());
    }

    @Override
    public Type visitIntNum(CINTParser.IntNumContext ctx) {
        return SimpleType.INT;
    }

    @Override
    public Type visitDecNum(CINTParser.DecNumContext ctx) {
        return SimpleType.DEC;
    }

    @Override
    public Type visitBoolean(CINTParser.BooleanContext ctx) {
        return SimpleType.BOOL;
    }

    @Override
    public Type visitCharacter(CINTParser.CharacterContext ctx) {
        return SimpleType.CHAR;
    }

    @Override
    public Type visitString(CINTParser.StringContext ctx) {
        // Le espressioni interpolate sono nascoste dentro il token: vanno tipate anche loro,
        // altrimenti "${y}" con y non dichiarata passerebbe il controllo statico.
        for (StringInterpolation.Part part : StringInterpolation.parse(ctx.STRING().getText()))
            if (part instanceof StringInterpolation.Interpolated interpolated)
                typeOf(interpolated.exp());

        return SimpleType.STRING;
    }

    @Override
    public Type visitArrayLit(CINTParser.ArrayLitContext ctx) {
        if (ctx.args() == null)
            throw new TypeError("il letterale [] non permette di dedurre il tipo degli elementi: "
                    + "si usi 'new T[0]'", ctx);

        List<CINTParser.ExpContext> elements = ctx.args().exp();
        ExpType elementType = typeOf(elements.get(0));

        for (int i = 1; i < elements.size(); i++) {
            ExpType current = typeOf(elements.get(i));

            if (elementType.accepts(current)) continue;      // l'elemento entra nel tipo corrente
            if (current.accepts(elementType)) {              // il tipo si allarga (int -> dec)
                elementType = current;
                continue;
            }

            throw new TypeError("gli elementi del letterale hanno tipi incompatibili: "
                    + elementType.getName() + " e " + current.getName(), elements.get(i));
        }

        return new ArrayType(elementType);
    }

    @Override
    public Type visitArrayNew(CINTParser.ArrayNewContext ctx) {
        for (CINTParser.ExpContext dimension : ctx.exp())
            require(SimpleType.INT, dimension);

        return TypeUtils.fromName(ctx.TYPE().getText(), ctx.exp().size());
    }

    @Override
    public Type visitParExp(CINTParser.ParExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public Type visitLen(CINTParser.LenContext ctx) {
        requireArray(ctx.exp());
        return SimpleType.INT;
    }

    @Override
    public Type visitToStr(CINTParser.ToStrContext ctx) {
        typeOf(ctx.exp());
        return SimpleType.STRING;
    }

    @Override
    public Type visitIndex(CINTParser.IndexContext ctx) {
        ArrayType array = requireArray(ctx.exp(0));
        require(SimpleType.INT, ctx.exp(1));
        return array.getElementType();
    }

    @Override
    public Type visitPow(CINTParser.PowContext ctx) {
        ExpType base = requireNumeric(ctx.exp(0));
        ExpType exponent = requireNumeric(ctx.exp(1));
        return TypeUtils.arithmeticJoin(base, exponent);
    }

    @Override
    public Type visitPostCrement(CINTParser.PostCrementContext ctx) {
        return requireNumericLvalue(ctx.lvalue(), ctx);
    }

    @Override
    public Type visitPreCrement(CINTParser.PreCrementContext ctx) {
        return requireNumericLvalue(ctx.lvalue(), ctx);
    }

    @Override
    public Type visitCast(CINTParser.CastContext ctx) {
        ExpType target = TypeUtils.fromName(ctx.TYPE().getText(), 0);
        ExpType source = typeOf(ctx.exp());

        if (!TypeUtils.canCast(source, target))
            throw new TypeError("non esiste conversione da " + source.getName()
                    + " a " + target.getName(), ctx);

        return target;
    }

    @Override
    public Type visitNot(CINTParser.NotContext ctx) {
        require(SimpleType.BOOL, ctx.exp());
        return SimpleType.BOOL;
    }

    @Override
    public Type visitNeg(CINTParser.NegContext ctx) {
        return requireNumeric(ctx.exp());
    }

    @Override
    public Type visitMulDivMod(CINTParser.MulDivModContext ctx) {
        ExpType left = requireNumeric(ctx.exp(0));
        ExpType right = requireNumeric(ctx.exp(1));
        return TypeUtils.arithmeticJoin(left, right);
    }

    @Override
    public Type visitAddSub(CINTParser.AddSubContext ctx) {
        ExpType left = typeOf(ctx.exp(0));
        ExpType right = typeOf(ctx.exp(1));

        // '+' e' sovraccarico: se uno dei due operandi e' una stringa, concatena convertendo l'altro
        if (ctx.op.getType() == CINTParser.ADD
                && (left == SimpleType.STRING || right == SimpleType.STRING))
            return SimpleType.STRING;

        if (!TypeUtils.isNumeric(left) || !TypeUtils.isNumeric(right))
            throw new TypeError("l'operatore " + ctx.op.getText() + " non si applica a "
                    + left.getName() + " e " + right.getName(), ctx);

        return TypeUtils.arithmeticJoin(left, right);
    }

    @Override
    public Type visitCmpExp(CINTParser.CmpExpContext ctx) {
        ExpType left = typeOf(ctx.exp(0));
        ExpType right = typeOf(ctx.exp(1));

        boolean comparable = (TypeUtils.isNumeric(left) && TypeUtils.isNumeric(right))
                || (left == SimpleType.CHAR && right == SimpleType.CHAR);

        if (!comparable)
            throw new TypeError("l'operatore " + ctx.op.getText() + " confronta valori numerici "
                    + "oppure due char, trovato " + left.getName() + " e " + right.getName(), ctx);

        return SimpleType.BOOL;
    }

    @Override
    public Type visitEqExp(CINTParser.EqExpContext ctx) {
        ExpType left = typeOf(ctx.exp(0));
        ExpType right = typeOf(ctx.exp(1));

        if (!left.accepts(right) && !right.accepts(left))
            throw new TypeError("confronto fra tipi incompatibili: "
                    + left.getName() + " e " + right.getName(), ctx);

        return SimpleType.BOOL;
    }

    @Override
    public Type visitAnd(CINTParser.AndContext ctx) {
        require(SimpleType.BOOL, ctx.exp(0));
        require(SimpleType.BOOL, ctx.exp(1));
        return SimpleType.BOOL;
    }

    @Override
    public Type visitOr(CINTParser.OrContext ctx) {
        require(SimpleType.BOOL, ctx.exp(0));
        require(SimpleType.BOOL, ctx.exp(1));
        return SimpleType.BOOL;
    }

    @Override
    public Type visitTernary(CINTParser.TernaryContext ctx) {
        require(SimpleType.BOOL, ctx.exp(0));

        ExpType whenTrue = typeOf(ctx.exp(1));
        ExpType whenFalse = typeOf(ctx.exp(2));

        // il tipo del ternario e' il piu' generale dei due rami, se esiste
        if (whenTrue.accepts(whenFalse)) return whenTrue;
        if (whenFalse.accepts(whenTrue)) return whenFalse;

        throw new TypeError("i due rami del ternario hanno tipi incompatibili: "
                + whenTrue.getName() + " e " + whenFalse.getName(), ctx);
    }

    @Override
    public Type visitId(CINTParser.IdContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }
}
