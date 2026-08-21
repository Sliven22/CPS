package it.univr.cps;

import it.univr.cps.env.Cell;
import it.univr.cps.env.FunctionTable;
import it.univr.cps.env.Scope;
import it.univr.cps.error.CPSRuntimeError;
import it.univr.cps.error.ReturnSignal;
import it.univr.cps.interpolation.StringInterpolation;
import it.univr.cps.lazyeval.FreeVariables;
import it.univr.cps.lazyeval.Thunk;
import it.univr.cps.type.*;
import it.univr.cps.value.*;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Interprete di CPS: valuta l'albero sintattico secondo la semantica operazionale del linguaggio.
 * <p>
 * Gira sempre dopo {@link CPSTypeSystem} e ne sfrutta le garanzie: nessun controllo di tipo viene
 * ripetuto qui. Restano invece i controlli che dipendono dai valori e non dai tipi — divisione per
 * zero, limiti degli array, profondita' della ricorsione — che sollevano {@link CPSRuntimeError} e
 * che il programma puo' intercettare con {@code try}/{@code catch}.
 * <p>
 * Lo stato e' un unico campo {@link #scope}: le entrate e le uscite dai blocchi lo spostano lungo la
 * catena, sempre dentro un {@code try/finally} in modo che un'uscita anticipata — un {@code return},
 * un errore catturato — non lasci l'ambiente disallineato.
 */
public final class CPSInterpreter extends CPSBaseVisitor<Value> {

    /**
     * Ogni chiamata CPS consuma diversi frame della pila Java: il limite tiene la ricorsione infinita
     * dentro un errore del linguaggio, catturabile, invece di farla degenerare in uno StackOverflowError
     * della JVM, che non lo è.
     */
    private static final int MAX_CALL_DEPTH = 1000;

    private final FunctionTable functions;

    private Scope<Cell> scope = new Scope<>();
    private int callDepth;

    public CPSInterpreter(FunctionTable functions) {
        this.functions = functions;
    }

    /** Esegue il comando principale del programma. La sezione delle funzioni non è eseguibile. */
    public void run(CPSParser.ProgramContext program) {
        scope = new Scope<>();
        callDepth = 0;
        visit(program.com());
    }

    // ------------------------------------------------------------------ utilita'

    private ExpValue<?> value(CPSParser.ExpContext ctx) {
        return (ExpValue<?>) visit(ctx);
    }

    private boolean condition(CPSParser.ExpContext ctx) {
        return ((BoolValue) value(ctx)).isTrue();
    }

    private int intValue(CPSParser.ExpContext ctx) {
        return ((IntValue) value(ctx)).toValue();
    }

    /**
     * Applica la conversione numerica implicita {@code int <-> real} nel momento in cui un valore viene <em>riposto</em>
     * da qualche parte: dichiarazione, assegnamento, legame di un parametro, valore di ritorno.
     * Concentrare qui la conversione evita di doverla ripetere in ogni punto di scrittura.
     */
    private static ExpValue<?> coerce(ExpValue<?> value, ExpType target) {
        if (target == SimpleType.REAL && value instanceof IntValue integer)
            return new RealValue(integer.toValue());
        if (target == SimpleType.INT && value instanceof RealValue real)
            return new IntValue(real.toValue().intValue());
        return value;
    }

    /** La cella denotata da un bersaglio assegnabile. */
    private Cell cellOf(CPSParser.LvalueContext ctx) {
        if (ctx instanceof CPSParser.VarLvalueContext variable)
            return scope.lookup(variable.ID().getText());

        CPSParser.CellLvalueContext element = (CPSParser.CellLvalueContext) ctx;
        ArrayValue array = (ArrayValue) cellOf(element.lvalue()).get();
        return array.cell(intValue(element.exp()));
    }

    /**
     * La cella denotata da un argomento passato per riferimento. Il type system ha gia' verificato
     * che l'espressione sia una variabile o un elemento di array.
     */
    private Cell cellOfArgument(CPSParser.ExpContext ctx) {
        while (ctx instanceof CPSParser.ParExpContext parenthesised)
            ctx = parenthesised.exp();

        if (ctx instanceof CPSParser.IdContext identifier)
            return scope.lookup(identifier.ID().getText());

        CPSParser.IndexContext index = (CPSParser.IndexContext) ctx;
        ArrayValue array = (ArrayValue) value(index.exp(0));
        return array.cell(intValue(index.exp(1)));
    }

    // ------------------------------------------------------------------ comandi

    @Override
    public Value visitDecl(CPSParser.DeclContext ctx) {
        ExpType declared = TypeUtils.fromDeclaration(ctx);

        ExpValue<?> initial = ctx.exp() == null
                ? (ctx.dimensions().isEmpty()
                    ? TypeUtils.defaultValue(declared)
                    : allocate(TypeUtils.fromName(ctx.type().TYPE().getText(), 0),
                        ctx.dimensions().stream().map(d -> intValue(d.exp())).toList(), 0))
                : coerce(value(ctx.exp()), declared);

        scope.declare(ctx.ID().getText(), Cell.of(declared, initial));
        return ComValue.INSTANCE;
    }

    /**
     * Assegnamento pigro. L'espressione non viene valutata adesso: si costruisce un {@link Thunk} che
     * chiude su un'istantanea delle sue variabili libere, e lo si ripone nella cella.
     * <p>
     * La cattura è <b>per valore</b>, come impone la semantica del linguaggio: in
     * {@code y = 4; lazy int x = y + 3; y = 0; print x} la stampa deve dare 7, cioe' il valore che
     * {@code y} aveva alla dichiarazione, non quello che ha al forzamento.
     */
    @Override
    public Value visitLazyDecl(CPSParser.LazyDeclContext ctx) {
        ExpType declared = TypeUtils.fromContext(ctx.type());
        CPSParser.ExpContext suspended = ctx.exp();
        Scope<Cell> captured = capture(suspended);

        Thunk thunk = new Thunk(() -> coerce(evaluateIn(captured, suspended), declared));

        scope.declare(ctx.ID().getText(), Cell.lazyOf(declared, thunk));
        return ComValue.INSTANCE;
    }

    /**
     * Costruisce l'istantanea: un ambiente nuovo che contiene una copia delle sole celle nominate
     * dall'espressione. Le copie sono indipendenti dalle originali, ma se una cella e' a sua volta
     * ancora sospesa la copia ne condivide il thunk — cosi' una catena di variabili pigre resta pigra
     * invece di essere forzata al momento della cattura.
     */
    private Scope<Cell> capture(CPSParser.ExpContext exp) {
        Scope<Cell> snapshot = new Scope<>();

        for (String name : FreeVariables.of(exp)) {
            Cell cell = scope.lookup(name);
            if (cell != null) snapshot.declare(name, cell.snapshot());
        }

        return snapshot;
    }

    /** Valuta un'espressione in un ambiente diverso da quello corrente, poi ripristina. */
    private ExpValue<?> evaluateIn(Scope<Cell> environment, CPSParser.ExpContext exp) {
        Scope<Cell> saved = scope;
        scope = environment;
        try {
            return value(exp);
        } finally {
            scope = saved;
        }
    }

    @Override
    public Value visitAssign(CPSParser.AssignContext ctx) {
        Cell target = cellOf(ctx.lvalue());
        target.set(coerce(value(ctx.exp()), target.getType()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitCompoundAssign(CPSParser.CompoundAssignContext ctx) {
        Cell target = cellOf(ctx.lvalue());
        ExpValue<?> operand = value(ctx.exp());

        // 'x op= e' e' esattamente 'x = x op e', compreso l'upcast in scrittura
        int binaryOp = switch (ctx.op.getType()) {
            case CPSParser.ADD_A -> CPSParser.ADD;
            case CPSParser.SUB_A -> CPSParser.SUB;
            case CPSParser.MUL_A -> CPSParser.MUL;
            case CPSParser.DIV_A -> CPSParser.DIV;
            default               -> CPSParser.MOD;
        };

        target.set(coerce(binary(binaryOp, target.get(), operand), target.getType()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitPostCrementCom(CPSParser.PostCrementComContext ctx) {
        crement(ctx.lvalue(), ctx.op.getType());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitPreCrementCom(CPSParser.PreCrementComContext ctx) {
        crement(ctx.lvalue(), ctx.op.getType());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitIfChain(CPSParser.IfChainContext ctx) {
        for (int i = 0; i < ctx.condition().size(); i++) {
            if (condition(ctx.condition(i).exp())) {
                if (i < ctx.com().size()) executeComScoped(ctx.com(i));
                return ComValue.INSTANCE;
            }
        }

        // In una catena CPS l'ultimo else e' rappresentato dal comando oltre
        // quelli associati alle condizioni, quando presente.
        if (ctx.ELSE().size() == ctx.condition().size()
                && ctx.com().size() > ctx.condition().size())
            executeComScoped(ctx.com(ctx.condition().size()));
        return ComValue.INSTANCE;
    }

    /** Ciclo iterativo, non ricorsivo: la profondita' della pila Java non deve dipendere dai giri. */
    @Override
    public Value visitWhile(CPSParser.WhileContext ctx) {
        while (condition(ctx.condition().exp()))
            executeComScoped(ctx.com());

        return ComValue.INSTANCE;
    }

    @Override
    public Value visitTryCatch(CPSParser.TryCatchContext ctx) {
        try {
            executeComScoped(ctx.com(0));
        } catch (CPSRuntimeError error) {
            Scope<Cell> enclosing = scope;
            scope = scope.push();
            try {
                scope.declare(ctx.ID().getText(),
                        Cell.of(SimpleType.STRING, new StringValue(error.getMessage())));
                executeComScoped(ctx.com(1));
            } finally {
                scope = enclosing;
            }
        }
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitForEach(CPSParser.ForEachContext ctx) {
        ArrayValue array = (ArrayValue) value(ctx.exp());
        ExpType variableType = TypeUtils.fromContext(ctx.type());

        for (int i = 0; i < array.length(); i++) {
            Scope<Cell> enclosing = scope;
            scope = scope.push();
            try {
                scope.declare(ctx.ID().getText(),
                        Cell.of(variableType, coerce(array.cell(i).get(), variableType)));
                if (ctx.com() != null) visit(ctx.com());
            } finally {
                scope = enclosing;
            }
        }
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitForRange(CPSParser.ForRangeContext ctx) {
        int first = intValue(ctx.exp(0));
        int last = intValue(ctx.exp(1));
        ExpType variableType = TypeUtils.fromContext(ctx.type());

        for (int i = first; i <= last; i++) {
            Scope<Cell> enclosing = scope;
            scope = scope.push();
            try {
                scope.declare(ctx.ID().getText(), Cell.of(variableType, new IntValue(i)));
                if (ctx.com() != null) visit(ctx.com());
            } finally {
                scope = enclosing;
            }
        }
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitForDestructuring(CPSParser.ForDestructuringContext ctx) {
        ArrayValue array = (ArrayValue) value(ctx.exp());
        ExpType indexType = TypeUtils.fromContext(ctx.type(0));
        ExpType valueType = TypeUtils.fromContext(ctx.type(1));

        for (int i = 0; i < array.length(); i++) {
            Scope<Cell> enclosing = scope;
            scope = scope.push();
            try {
                scope.declare(ctx.ID(0).getText(), Cell.of(indexType, new IntValue(i)));
                scope.declare(ctx.ID(1).getText(),
                        Cell.of(valueType, coerce(array.cell(i).get(), valueType)));
                if (ctx.com() != null) visit(ctx.com());
            } finally {
                scope = enclosing;
            }
        }
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitDoWhile(CPSParser.DoWhileContext ctx) {
        do {
            executeComScoped(ctx.com());
        } while (condition(ctx.condition().exp()));
        return ComValue.INSTANCE;
    }

    private void executeComScoped(CPSParser.ComContext command) {
        if (command == null) return;
        Scope<Cell> enclosing = scope;
        scope = scope.push();
        try {
            visit(command);
        } finally {
            scope = enclosing;
        }
    }

    @Override
    public Value visitThrow(CPSParser.ThrowContext ctx) {
        throw new CPSRuntimeError(((StringValue) value(ctx.exp())).toValue());
    }

    @Override
    public Value visitReturn(CPSParser.ReturnContext ctx) {
        throw new ReturnSignal(ctx.exp() == null ? null : value(ctx.exp()));
    }

    @Override
    public Value visitPrint(CPSParser.PrintContext ctx) {
        System.out.println(value(ctx.exp()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitCallCom(CPSParser.CallComContext ctx) {
        invoke(functions.get(ctx.ID().getText()), ctx.args());
        return ComValue.INSTANCE;
    }

    /**
     * Sequenza: esegue il comando in testa e poi, se c'e', il resto della sequenza. La ricorsione
     * segue la forma della regola {@code com}, che e' ricorsiva a destra.
     */
    @Override
    public Value visitCom(CPSParser.ComContext ctx) {
        // Le funzioni sono dichiarazioni e non fanno parte dell'esecuzione principale.
        if (ctx.funDecl() != null)
            return ctx.com() == null ? ComValue.INSTANCE : visit(ctx.com());

        if (ctx.simpleCom() != null) visit(ctx.simpleCom());
        else if (ctx.closedCom() != null) visit(ctx.closedCom());
        else visit(ctx.doWhileCom());
        return ctx.com() == null ? ComValue.INSTANCE : visit(ctx.com());
    }

    @Override
    public Value visitNop(CPSParser.NopContext ctx) {
        return ComValue.INSTANCE;
    }

    // ------------------------------------------------------------------ chiamate

    /**
     * Esegue una chiamata di funzione.
     * <p>
     * Gli argomenti sono valutati nell'ambiente del <b>chiamante</b>, mentre il corpo gira in un frame
     * con scope radice <b>nuovo</b>: le variabili locali del chiamante non sono visibili al chiamato,
     * e le funzioni non vedono alcuno stato globale.
     * <p>
     * Il legame dei parametri distingue le due modalita' di passaggio: per valore si crea una cella
     * nuova con una copia del valore, per riferimento si condivide la cella del chiamante, cosi' le
     * assegnazioni fatte dentro la funzione si vedono fuori.
     */
    private ExpValue<?> invoke(FunctionTable.Signature signature, CPSParser.ArgsContext args) {
        List<CPSParser.ExpContext> actuals = args == null ? List.of() : args.exp();

        List<Cell> bound = new ArrayList<>(actuals.size());
        for (int i = 0; i < actuals.size(); i++) {
            FunctionTable.Param formal = signature.params().get(i);

            bound.add(formal.byRef()
                    ? cellOfArgument(actuals.get(i))
                    : Cell.of(formal.type(), coerce(value(actuals.get(i)), formal.type())));
        }

        if (++callDepth > MAX_CALL_DEPTH) {
            callDepth--;
            throw new CPSRuntimeError("ricorsione troppo profonda in '" + signature.name()
                    + "' (oltre " + MAX_CALL_DEPTH + " chiamate annidate)");
        }

        Scope<Cell> caller = scope;
        scope = new Scope<>();
        try {
            for (int i = 0; i < bound.size(); i++)
                scope.declare(signature.params().get(i).name(), bound.get(i));

            CPSParser.ComContext body = signature.declaration().com();
            if (body != null) visit(body);

            return null; // caduta in fondo a una procedura void
        } catch (ReturnSignal signal) {
            ExpValue<?> result = signal.getValue();
            return signature.isVoid() ? null : coerce(result, (ExpType) signature.returnType());
        } finally {
            scope = caller;
            callDepth--;
        }
    }

    @Override
    public Value visitCall(CPSParser.CallContext ctx) {
        return invoke(functions.get(ctx.ID().getText()), ctx.args());
    }

    // ------------------------------------------------------------------ espressioni

    @Override
    public Value visitNumeric(CPSParser.NumericContext ctx) {
        return visit(ctx.num());
    }

    @Override
    public Value visitIntNum(CPSParser.IntNumContext ctx) {
        return new IntValue(Integer.parseInt(ctx.INT().getText()));
    }

    @Override
    public Value visitRealNum(CPSParser.RealNumContext ctx) {
        return new RealValue(Double.parseDouble(ctx.REAL_LITERAL().getText()));
    }

    @Override
    public Value visitBoolean(CPSParser.BooleanContext ctx) {
        return BoolValue.of(Boolean.parseBoolean(ctx.BOOL().getText()));
    }

    @Override
    public Value visitCharacter(CPSParser.CharacterContext ctx) {
        String literal = ctx.CHAR().getText();
        String body = literal.substring(1, literal.length() - 1);
        return new CharValue(StringInterpolation.unescape(body).charAt(0));
    }

    /** Un letterale stringa e' la concatenazione dei suoi pezzi letterali e delle parti interpolate. */
    @Override
    public Value visitString(CPSParser.StringContext ctx) {
        StringBuilder text = new StringBuilder();

        for (StringInterpolation.Part part : StringInterpolation.parse(ctx.STRING().getText())) {
            if (part instanceof StringInterpolation.Literal literal)
                text.append(literal.text());
            else
                text.append(value(((StringInterpolation.Interpolated) part).exp()));
        }

        return new StringValue(text.toString());
    }

    @Override
    public Value visitArrayLit(CPSParser.ArrayLitContext ctx) {
        List<CPSParser.ExpContext> elements = ctx.args().exp();

        List<ExpValue<?>> values = new ArrayList<>(elements.size());
        for (CPSParser.ExpContext element : elements)
            values.add(value(element));

        // il tipo degli elementi e' il piu' generale fra quelli presenti (un solo real basta a farli real)
        ExpType elementType = TypeUtils.fromValue(values.get(0));
        for (ExpValue<?> candidate : values) {
            ExpType current = TypeUtils.fromValue(candidate);
            if (current.accepts(elementType)) elementType = current;
        }

        Cell[] cells = new Cell[values.size()];
        for (int i = 0; i < cells.length; i++)
            cells[i] = Cell.of(elementType, coerce(values.get(i), elementType));

        return new ArrayValue(elementType, cells);
    }

    @Override
    public Value visitArrayNew(CPSParser.ArrayNewContext ctx) {
        List<Integer> dimensions = new ArrayList<>();
        for (CPSParser.ExpContext dimension : ctx.exp())
            dimensions.add(intValue(dimension));

        return allocate(TypeUtils.fromName(ctx.TYPE().getText(), 0), dimensions, 0);
    }

    /** Alloca ricorsivamente: l'ultima dimensione riceve i valori di default, le altre sotto-array. */
    private ArrayValue allocate(ExpType base, List<Integer> dimensions, int level) {
        int size = dimensions.get(level);
        if (size < 0)
            throw new CPSRuntimeError("dimensione di array negativa: " + size);

        ExpType elementType = base;
        for (int i = level + 1; i < dimensions.size(); i++)
            elementType = new ArrayType(elementType);

        Cell[] cells = new Cell[size];
        for (int i = 0; i < size; i++)
            cells[i] = Cell.of(elementType, level + 1 < dimensions.size()
                    ? allocate(base, dimensions, level + 1)
                    : TypeUtils.defaultValue(elementType));

        return new ArrayValue(elementType, cells);
    }

    @Override
    public Value visitParExp(CPSParser.ParExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public Value visitLen(CPSParser.LenContext ctx) {
        return new IntValue(((ArrayValue) value(ctx.exp())).length());
    }

    @Override
    public Value visitToStr(CPSParser.ToStrContext ctx) {
        return new StringValue(value(ctx.exp()).toString());
    }

    @Override
    public Value visitIndex(CPSParser.IndexContext ctx) {
        ArrayValue array = (ArrayValue) value(ctx.exp(0));
        return array.cell(intValue(ctx.exp(1))).get();
    }

    @Override
    public Value visitPow(CPSParser.PowContext ctx) {
        NumValue<?> base = (NumValue<?>) value(ctx.exp(0));
        NumValue<?> exponent = (NumValue<?>) value(ctx.exp(1));

        double result = Math.pow(base.asDouble(), exponent.asDouble());

        return bothInt(base, exponent) ? new IntValue((int) result) : new RealValue(result);
    }

    @Override
    public Value visitPostCrement(CPSParser.PostCrementContext ctx) {
        return crement(ctx.lvalue(), ctx.op.getType()).before();
    }

    @Override
    public Value visitPreCrement(CPSParser.PreCrementContext ctx) {
        return crement(ctx.lvalue(), ctx.op.getType()).after();
    }

    /** L'esito di un incremento: la semantica di Java distingue il valore prima e dopo la modifica. */
    private record Crement(ExpValue<?> before, ExpValue<?> after) { }

    private Crement crement(CPSParser.LvalueContext lvalue, int op) {
        Cell cell = cellOf(lvalue);
        ExpValue<?> before = cell.get();
        int step = op == CPSParser.INCR ? 1 : -1;

        ExpValue<?> after = before instanceof IntValue integer
                ? new IntValue(integer.toValue() + step)
                : new RealValue(((RealValue) before).toValue() + step);

        cell.set(after);
        return new Crement(before, after);
    }

    @Override
    public Value visitCast(CPSParser.CastContext ctx) {
        return TypeUtils.cast(value(ctx.exp()), TypeUtils.fromName(ctx.TYPE().getText(), 0));
    }

    @Override
    public Value visitNot(CPSParser.NotContext ctx) {
        return BoolValue.of(!condition(ctx.exp()));
    }

    @Override
    public Value visitNeg(CPSParser.NegContext ctx) {
        NumValue<?> operand = (NumValue<?>) value(ctx.exp());

        return operand instanceof IntValue integer
                ? new IntValue(-integer.toValue())
                : new RealValue(-operand.asDouble());
    }

    @Override
    public Value visitMulDivMod(CPSParser.MulDivModContext ctx) {
        return binary(ctx.op.getType(), value(ctx.exp(0)), value(ctx.exp(1)));
    }

    @Override
    public Value visitAddSub(CPSParser.AddSubContext ctx) {
        return binary(ctx.op.getType(), value(ctx.exp(0)), value(ctx.exp(1)));
    }

    /**
     * Le operazioni binarie aritmetiche. Se entrambi gli operandi sono interi il conto si fa fra
     * interi: passare sempre dai double, come nelle esercitazioni, perderebbe precisione sugli
     * interi grandi e nasconderebbe la divisione per zero dietro a un {@code Infinity}.
     */
    private ExpValue<?> binary(int op, ExpValue<?> left, ExpValue<?> right) {
        if (op == CPSParser.ADD && (left instanceof StringValue || right instanceof StringValue))
            return new StringValue(left.toString() + right);

        NumValue<?> a = (NumValue<?>) left;
        NumValue<?> b = (NumValue<?>) right;

        if (bothInt(a, b)) {
            int x = ((IntValue) a).toValue();
            int y = ((IntValue) b).toValue();

            return new IntValue(switch (op) {
                case CPSParser.ADD -> x + y;
                case CPSParser.SUB -> x - y;
                case CPSParser.MUL -> x * y;
                case CPSParser.DIV -> divide(x, y);
                default             -> modulo(x, y);
            });
        }

        double x = a.asDouble();
        double y = b.asDouble();

        return new RealValue(switch (op) {
            case CPSParser.ADD -> x + y;
            case CPSParser.SUB -> x - y;
            case CPSParser.MUL -> x * y;
            case CPSParser.DIV -> divide(x, y);
            default             -> modulo(x, y);
        });
    }

    private static boolean bothInt(NumValue<?> left, NumValue<?> right) {
        return left instanceof IntValue && right instanceof IntValue;
    }

    private static int divide(int dividend, int divisor) {
        if (divisor == 0) throw new CPSRuntimeError("divisione per zero");
        return dividend / divisor;
    }

    private static double divide(double dividend, double divisor) {
        if (divisor == 0.0) throw new CPSRuntimeError("divisione per zero");
        return dividend / divisor;
    }

    private static int modulo(int dividend, int divisor) {
        if (divisor == 0) throw new CPSRuntimeError("resto con divisore zero");
        return dividend % divisor;
    }

    private static double modulo(double dividend, double divisor) {
        if (divisor == 0.0) throw new CPSRuntimeError("resto con divisore zero");
        return dividend % divisor;
    }

    @Override
    public Value visitCmpExp(CPSParser.CmpExpContext ctx) {
        ExpValue<?> left = value(ctx.exp(0));
        ExpValue<?> right = value(ctx.exp(1));

        double a = left instanceof CharValue character ? character.toValue() : ((NumValue<?>) left).asDouble();
        double b = right instanceof CharValue character ? character.toValue() : ((NumValue<?>) right).asDouble();

        return BoolValue.of(switch (ctx.op.getType()) {
            case CPSParser.LT  -> a < b;
            case CPSParser.LEQ -> a <= b;
            case CPSParser.GT  -> a > b;
            default             -> a >= b;
        });
    }

    @Override
    public Value visitEqExp(CPSParser.EqExpContext ctx) {
        ExpValue<?> left = value(ctx.exp(0));
        ExpValue<?> right = value(ctx.exp(1));

        // il confronto misto int/real passa per il valore numerico, cosi' 1 == 1.0 e' vero
        boolean equal = (left instanceof NumValue<?> a && right instanceof NumValue<?> b)
                ? a.asDouble() == b.asDouble()
                : left.equals(right);

        boolean equality = ctx.op.getType() == CPSParser.EQQ
                || ctx.op.getType() == CPSParser.EQUALS;
        return BoolValue.of(equality ? equal : !equal);
    }

    /**
     * Congiunzione e disgiunzione sono valutate in <b>corto circuito</b>, come in C e in Java:
     * il secondo operando non viene toccato se il primo gia' determina il risultato.
     */
    @Override
    public Value visitAnd(CPSParser.AndContext ctx) {
        return BoolValue.of(condition(ctx.exp(0)) && condition(ctx.exp(1)));
    }

    @Override
    public Value visitOr(CPSParser.OrContext ctx) {
        return BoolValue.of(condition(ctx.exp(0)) || condition(ctx.exp(1)));
    }

    @Override
    public Value visitTernary(CPSParser.TernaryContext ctx) {
        return value(condition(ctx.exp(0)) ? ctx.exp(1) : ctx.exp(2));
    }

    @Override
    public Value visitId(CPSParser.IdContext ctx) {
        return scope.lookup(ctx.ID().getText()).get(); // get() forza un'eventuale cella pigra
    }
}
