package it.univr.cps;

import it.univr.cps.env.FunctionTable;
import it.univr.cps.env.Scope;
import it.univr.cps.error.DeclarationError;
import it.univr.cps.error.TypeError;
import it.univr.cps.interpolation.StringInterpolation;
import it.univr.cps.type.*;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.List;

/**
 * Type system di CPS: visita l'albero sintattico e assegna un tipo a ogni nodo, oppure fallisce.
 * <p>
 * CPS e' tipizzato <b>staticamente</b>: l'interprete parte solo se questa visita si conclude senza
 * errori, e puo' quindi assumere che ogni operazione riceva operandi del tipo giusto senza doverlo
 * ricontrollare. Gli unici controlli lasciati a runtime sono quelli che dipendono dai valori e non
 * dai tipi: divisione per zero, limiti degli array, profondita' della ricorsione.
 * <p>
 * L'ambiente e' una {@link Scope} di tipi che rispecchia esattamente la catena di scope usata
 * dall'interprete: le due visite entrano ed escono dai blocchi negli stessi punti, quindi un nome
 * risolto qui denota a runtime la stessa dichiarazione.
 */
public final class CPSTypeSystem extends CPSBaseVisitor<Type> {

    private final FunctionTable functions;

    private Scope<ExpType> scope = new Scope<>();

    /** Tipo di ritorno della funzione in corso di controllo; null nel corpo principale. */
    private Type returnType;

    public CPSTypeSystem(FunctionTable functions) {
        this.functions = functions;
    }

    // ------------------------------------------------------------------ ingresso

    /** Controlla l'intero programma: prima i corpi delle funzioni, poi il comando principale. */
    public void check(CPSParser.ProgramContext program) {
        for (FunctionTable.Signature signature : functions.all())
            checkFunction(signature);

        scope = new Scope<>();
        returnType = null;
        visit(program.com());
    }

    /**
     * Le funzioni di CPS sono <b>chiuse rispetto allo stato globale</b>: il corpo parte da uno scope
     * radice nuovo, che contiene i soli parametri. Comunicano con il resto del programma unicamente
     * attraverso parametri e valore di ritorno.
     */
    private void checkFunction(FunctionTable.Signature signature) {
        scope = new Scope<>();
        returnType = signature.returnType();

        for (FunctionTable.Param param : signature.params())
            scope.declare(param.name(), param.type());

        CPSParser.ComContext body = signature.declaration().com();
        if (body != null) {
            scope = scope.push();
            visit(body);
            scope = scope.pop();
        }

        if (!signature.isVoid() && !alwaysReturns(body))
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
    private boolean alwaysReturns(CPSParser.ComContext ctx) {
        if (ctx == null) return false;

        if (ctx.funDecl() != null)
            return alwaysReturns(ctx.com());

        boolean head = ctx.simpleCom() != null
                ? alwaysReturns(ctx.simpleCom())
                : alwaysReturns(ctx.closedCom());

        return head || alwaysReturns(ctx.com());
    }

    /** {@code return} esce per definizione, {@code throw} esce comunque dalla funzione. */
    private boolean alwaysReturns(CPSParser.SimpleComContext ctx) {
        return ctx instanceof CPSParser.ReturnContext || ctx instanceof CPSParser.ThrowContext;
    }

    private boolean alwaysReturns(CPSParser.ClosedComContext ctx) {
        return switch (ctx) {
            case CPSParser.IfChainContext chain ->
                    chain.ELSE().size() == chain.condition().size()
                            && chain.com().size() == chain.condition().size() + 1
                            && chain.com().stream().allMatch(this::alwaysReturns);
            case CPSParser.TryCatchContext tryCatch ->
                    tryCatch.com().size() >= 2
                            && alwaysReturns(tryCatch.com(0)) && alwaysReturns(tryCatch.com(1));
            // un 'if' senza else e un 'while' possono non entrare mai nel corpo
            default -> false;
        };
    }

    // ------------------------------------------------------------------ utilita'

    /** Tipo di un'espressione, con il controllo che sia davvero un valore (non void, non comando). */
    private ExpType typeOf(CPSParser.ExpContext ctx) {
        Type type = visit(ctx);
        if (!(type instanceof ExpType expType))
            throw new TypeError("qui serve un'espressione con un valore, trovato "
                    + type.getName(), ctx);
        return expType;
    }

    private void require(SimpleType expected, CPSParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!expected.accepts(actual))
            throw new TypeError("atteso " + expected.getName() + ", trovato " + actual.getName(), ctx);
    }

    private ExpType requireNumeric(CPSParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!TypeUtils.isNumeric(actual))
            throw new TypeError("attesa un'espressione numerica, trovato " + actual.getName(), ctx);
        return actual;
    }

    private ArrayType requireArray(CPSParser.ExpContext ctx) {
        ExpType actual = typeOf(ctx);
        if (!(actual instanceof ArrayType arrayType))
            throw new TypeError("atteso un array, trovato " + actual.getName(), ctx);
        return arrayType;
    }

    private void visitComScoped(CPSParser.ComContext command) {
        if (command == null) return;
        scope = scope.push();
        visit(command);
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
    private static CPSParser.ExpContext unwrap(CPSParser.ExpContext ctx) {
        while (ctx instanceof CPSParser.ParExpContext parenthesised)
            ctx = parenthesised.exp();
        return ctx;
    }

    // ------------------------------------------------------------------ comandi

    @Override
    public Type visitDecl(CPSParser.DeclContext ctx) {
        ExpType declared = TypeUtils.fromDeclaration(ctx);
        String id = ctx.ID().getText();

        for (CPSParser.DimensionsContext dimension : ctx.dimensions())
            require(SimpleType.INT, dimension.exp());

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
    public Type visitLazyDecl(CPSParser.LazyDeclContext ctx) {
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
    public Type visitAssign(CPSParser.AssignContext ctx) {
        ExpType target = (ExpType) visit(ctx.lvalue());
        ExpType actual = typeOf(ctx.exp());

        if (!target.accepts(actual))
            throw new TypeError("non si puo' assegnare " + actual.getName()
                    + " a un bersaglio di tipo " + target.getName(), ctx);

        return ComType.INSTANCE;
    }

    @Override
    public Type visitCompoundAssign(CPSParser.CompoundAssignContext ctx) {
        ExpType target = (ExpType) visit(ctx.lvalue());
        ExpType operand = typeOf(ctx.exp());
        String op = ctx.op.getText();

        // 'x op= e' e' definito come 'x = x op e': si tipa l'operazione e poi l'assegnamento.
        ExpType result;
        if (ctx.op.getType() == CPSParser.ADD_A && target == SimpleType.STRING) {
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
    public Type visitPostCrementCom(CPSParser.PostCrementComContext ctx) {
        requireNumericLvalue(ctx.lvalue(), ctx);
        return ComType.INSTANCE;
    }

    @Override
    public Type visitPreCrementCom(CPSParser.PreCrementComContext ctx) {
        requireNumericLvalue(ctx.lvalue(), ctx);
        return ComType.INSTANCE;
    }

    private ExpType requireNumericLvalue(CPSParser.LvalueContext lvalue, ParserRuleContext ctx) {
        ExpType type = (ExpType) visit(lvalue);
        if (!TypeUtils.isNumeric(type))
            throw new TypeError("++ e -- si applicano solo a int e real, trovato "
                    + type.getName(), ctx);
        return type;
    }

    @Override
    public Type visitIfChain(CPSParser.IfChainContext ctx) {
        for (int i = 0; i < ctx.condition().size(); i++) {
            require(SimpleType.BOOL, ctx.condition(i).exp());
            if (i < ctx.com().size()) visitComScoped(ctx.com(i));
        }
        if (ctx.ELSE().size() == ctx.condition().size()
                && ctx.com().size() > ctx.condition().size())
            visitComScoped(ctx.com(ctx.condition().size()));
        return ComType.INSTANCE;
    }

    @Override
    public Type visitWhile(CPSParser.WhileContext ctx) {
        require(SimpleType.BOOL, ctx.condition().exp());
        visitComScoped(ctx.com());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitTryCatch(CPSParser.TryCatchContext ctx) {
        visitComScoped(ctx.com(0));

        // la variabile del catch e' visibile solo nel blocco di gestione, e contiene il messaggio
        scope = scope.push();
        scope.declare(ctx.ID().getText(), SimpleType.STRING);
        visitComScoped(ctx.com(1));
        scope = scope.pop();

        return ComType.INSTANCE;
    }

    @Override
    public Type visitForEach(CPSParser.ForEachContext ctx) {
        ArrayType array = requireArray(ctx.exp());
        ExpType variable = TypeUtils.fromContext(ctx.type());
        if (!variable.accepts(array.getElementType()))
            throw new TypeError("la variabile del for e' di tipo " + variable.getName()
                    + ", ma l'array contiene " + array.getElementType().getName(), ctx);
        scope = scope.push();
        declare(ctx.ID().getText(), variable, ctx);
        visitComScoped(ctx.com());
        scope = scope.pop();
        return ComType.INSTANCE;
    }

    @Override
    public Type visitForRange(CPSParser.ForRangeContext ctx) {
        require(SimpleType.INT, ctx.exp(0));
        require(SimpleType.INT, ctx.exp(1));
        ExpType variable = TypeUtils.fromContext(ctx.type());
        if (variable != SimpleType.INT)
            throw new TypeError("il contatore di un for su intervallo deve essere int", ctx);
        scope = scope.push();
        declare(ctx.ID().getText(), variable, ctx);
        visitComScoped(ctx.com());
        scope = scope.pop();
        return ComType.INSTANCE;
    }

    @Override
    public Type visitForDestructuring(CPSParser.ForDestructuringContext ctx) {
        ArrayType array = requireArray(ctx.exp());
        ExpType indexType = TypeUtils.fromContext(ctx.type(0));
        ExpType valueType = TypeUtils.fromContext(ctx.type(1));
        if (indexType != SimpleType.INT || !valueType.accepts(array.getElementType()))
            throw new TypeError("tipi incompatibili nel for con destrutturazione", ctx);
        scope = scope.push();
        declare(ctx.ID(0).getText(), indexType, ctx);
        declare(ctx.ID(1).getText(), valueType, ctx);
        visitComScoped(ctx.com());
        scope = scope.pop();
        return ComType.INSTANCE;
    }

    @Override
    public Type visitDoWhile(CPSParser.DoWhileContext ctx) {
        visitComScoped(ctx.com());
        require(SimpleType.BOOL, ctx.condition().exp());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitThrow(CPSParser.ThrowContext ctx) {
        require(SimpleType.STRING, ctx.exp());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitReturn(CPSParser.ReturnContext ctx) {
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
    public Type visitPrint(CPSParser.PrintContext ctx) {
        typeOf(ctx.exp()); // print accetta qualunque valore e lo converte in stringa
        return ComType.INSTANCE;
    }

    @Override
    public Type visitCallCom(CPSParser.CallComContext ctx) {
        checkCall(ctx.ID().getText(), ctx.args(), ctx);
        return ComType.INSTANCE; // il valore di ritorno, se c'e', viene scartato
    }

    /** Sequenza: si controlla il comando in testa e poi il resto, nell'ordine in cui sono scritti. */
    @Override
    public Type visitCom(CPSParser.ComContext ctx) {
        // Le funzioni sono dichiarazioni, non comandi eseguibili.
        if (ctx.funDecl() != null) {
            if (ctx.com() != null) visit(ctx.com());
            return ComType.INSTANCE;
        }

        if (ctx.simpleCom() != null) visit(ctx.simpleCom());
        else if (ctx.closedCom() != null) visit(ctx.closedCom());
        else visit(ctx.doWhileCom());
        if (ctx.com() != null) visit(ctx.com());
        return ComType.INSTANCE;
    }

    @Override
    public Type visitNop(CPSParser.NopContext ctx) {
        return ComType.INSTANCE;
    }

    // ------------------------------------------------------------------ lvalue

    @Override
    public Type visitVarLvalue(CPSParser.VarLvalueContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }

    @Override
    public Type visitCellLvalue(CPSParser.CellLvalueContext ctx) {
        Type base = visit(ctx.lvalue());
        if (!(base instanceof ArrayType arrayType))
            throw new TypeError("indicizzazione applicata a " + base.getName()
                    + ", che non e' un array", ctx);

        require(SimpleType.INT, ctx.exp());
        return arrayType.getElementType();
    }

    // ------------------------------------------------------------------ chiamate

    private Type checkCall(String name, CPSParser.ArgsContext args, ParserRuleContext ctx) {
        FunctionTable.Signature signature = functions.get(name);
        if (signature == null)
            throw new DeclarationError("funzione '" + name + "' non dichiarata", ctx);

        List<CPSParser.ExpContext> actuals = args == null ? List.of() : args.exp();
        if (actuals.size() != signature.arity())
            throw new TypeError("la funzione '" + name + "' vuole " + signature.arity()
                    + " argomenti, ne riceve " + actuals.size(), ctx);

        for (int i = 0; i < actuals.size(); i++) {
            FunctionTable.Param formal = signature.params().get(i);
            CPSParser.ExpContext actual = actuals.get(i);
            ExpType actualType = typeOf(actual);

            if (formal.byRef()) {
                // Un parametro per riferimento condivide la cella del chiamante: l'argomento deve
                // quindi essere assegnabile, e il tipo deve coincidere esattamente. Ammettere
                // una conversione implicita permetterebbe alla funzione di scrivere un real dentro una
                // variabile int del chiamante.
                CPSParser.ExpContext bare = unwrap(actual);
                boolean assignable = bare instanceof CPSParser.IdContext
                        || bare instanceof CPSParser.IndexContext;

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
    public Type visitCall(CPSParser.CallContext ctx) {
        Type result = checkCall(ctx.ID().getText(), ctx.args(), ctx);

        if (result instanceof VoidType)
            throw new TypeError("la funzione '" + ctx.ID().getText()
                    + "' e' void e non puo' comparire in un'espressione", ctx);

        return result;
    }

    // ------------------------------------------------------------------ espressioni

    @Override
    public Type visitNumeric(CPSParser.NumericContext ctx) {
        return visit(ctx.num());
    }

    @Override
    public Type visitIntNum(CPSParser.IntNumContext ctx) {
        return SimpleType.INT;
    }

    @Override
    public Type visitRealNum(CPSParser.RealNumContext ctx) {
        return SimpleType.REAL;
    }

    @Override
    public Type visitBoolean(CPSParser.BooleanContext ctx) {
        return SimpleType.BOOL;
    }

    @Override
    public Type visitCharacter(CPSParser.CharacterContext ctx) {
        return SimpleType.CHAR;
    }

    @Override
    public Type visitString(CPSParser.StringContext ctx) {
        // Le espressioni interpolate sono nascoste dentro il token: vanno tipate anche loro,
        // altrimenti "${y}" con y non dichiarata passerebbe il controllo statico.
        for (StringInterpolation.Part part : StringInterpolation.parse(ctx.STRING().getText()))
            if (part instanceof StringInterpolation.Interpolated interpolated)
                typeOf(interpolated.exp());

        return SimpleType.STRING;
    }

    @Override
    public Type visitArrayLit(CPSParser.ArrayLitContext ctx) {
        if (ctx.args() == null)
            throw new TypeError("il letterale [] non permette di dedurre il tipo degli elementi: "
                    + "si usi 'new T[0]'", ctx);

        List<CPSParser.ExpContext> elements = ctx.args().exp();
        ExpType elementType = typeOf(elements.get(0));

        for (int i = 1; i < elements.size(); i++) {
            ExpType current = typeOf(elements.get(i));

            if (elementType.accepts(current)) continue;      // l'elemento entra nel tipo corrente
            if (current.accepts(elementType)) {              // il tipo si allarga verso real
                elementType = current;
                continue;
            }

            throw new TypeError("gli elementi del letterale hanno tipi incompatibili: "
                    + elementType.getName() + " e " + current.getName(), elements.get(i));
        }

        return new ArrayType(elementType);
    }

    @Override
    public Type visitArrayNew(CPSParser.ArrayNewContext ctx) {
        for (CPSParser.ExpContext dimension : ctx.exp())
            require(SimpleType.INT, dimension);

        return TypeUtils.fromName(ctx.TYPE().getText(), ctx.exp().size());
    }

    @Override
    public Type visitParExp(CPSParser.ParExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public Type visitLen(CPSParser.LenContext ctx) {
        requireArray(ctx.exp());
        return SimpleType.INT;
    }

    @Override
    public Type visitToStr(CPSParser.ToStrContext ctx) {
        typeOf(ctx.exp());
        return SimpleType.STRING;
    }

    @Override
    public Type visitIndex(CPSParser.IndexContext ctx) {
        ArrayType array = requireArray(ctx.exp(0));
        require(SimpleType.INT, ctx.exp(1));
        return array.getElementType();
    }

    @Override
    public Type visitPow(CPSParser.PowContext ctx) {
        ExpType base = requireNumeric(ctx.exp(0));
        ExpType exponent = requireNumeric(ctx.exp(1));
        return TypeUtils.arithmeticJoin(base, exponent);
    }

    @Override
    public Type visitPostCrement(CPSParser.PostCrementContext ctx) {
        return requireNumericLvalue(ctx.lvalue(), ctx);
    }

    @Override
    public Type visitPreCrement(CPSParser.PreCrementContext ctx) {
        return requireNumericLvalue(ctx.lvalue(), ctx);
    }

    @Override
    public Type visitCast(CPSParser.CastContext ctx) {
        ExpType target = TypeUtils.fromName(ctx.TYPE().getText(), 0);
        ExpType source = typeOf(ctx.exp());

        if (!TypeUtils.canCast(source, target))
            throw new TypeError("non esiste conversione da " + source.getName()
                    + " a " + target.getName(), ctx);

        return target;
    }

    @Override
    public Type visitNot(CPSParser.NotContext ctx) {
        require(SimpleType.BOOL, ctx.exp());
        return SimpleType.BOOL;
    }

    @Override
    public Type visitNeg(CPSParser.NegContext ctx) {
        return requireNumeric(ctx.exp());
    }

    @Override
    public Type visitMulDivMod(CPSParser.MulDivModContext ctx) {
        ExpType left = requireNumeric(ctx.exp(0));
        ExpType right = requireNumeric(ctx.exp(1));
        return TypeUtils.arithmeticJoin(left, right);
    }

    @Override
    public Type visitAddSub(CPSParser.AddSubContext ctx) {
        ExpType left = typeOf(ctx.exp(0));
        ExpType right = typeOf(ctx.exp(1));

        // '+' e' sovraccarico: se uno dei due operandi e' una stringa, concatena convertendo l'altro
        if (ctx.op.getType() == CPSParser.ADD
                && (left == SimpleType.STRING || right == SimpleType.STRING))
            return SimpleType.STRING;

        if (!TypeUtils.isNumeric(left) || !TypeUtils.isNumeric(right))
            throw new TypeError("l'operatore " + ctx.op.getText() + " non si applica a "
                    + left.getName() + " e " + right.getName(), ctx);

        return TypeUtils.arithmeticJoin(left, right);
    }

    @Override
    public Type visitCmpExp(CPSParser.CmpExpContext ctx) {
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
    public Type visitEqExp(CPSParser.EqExpContext ctx) {
        ExpType left = typeOf(ctx.exp(0));
        ExpType right = typeOf(ctx.exp(1));

        if (!left.accepts(right) && !right.accepts(left))
            throw new TypeError("confronto fra tipi incompatibili: "
                    + left.getName() + " e " + right.getName(), ctx);

        return SimpleType.BOOL;
    }

    @Override
    public Type visitAnd(CPSParser.AndContext ctx) {
        require(SimpleType.BOOL, ctx.exp(0));
        require(SimpleType.BOOL, ctx.exp(1));
        return SimpleType.BOOL;
    }

    @Override
    public Type visitOr(CPSParser.OrContext ctx) {
        require(SimpleType.BOOL, ctx.exp(0));
        require(SimpleType.BOOL, ctx.exp(1));
        return SimpleType.BOOL;
    }

    @Override
    public Type visitTernary(CPSParser.TernaryContext ctx) {
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
    public Type visitId(CPSParser.IdContext ctx) {
        return lookup(ctx.ID().getText(), ctx);
    }
}
