package it.univr.cint;

import it.univr.cint.env.Cell;
import it.univr.cint.env.FunctionTable;
import it.univr.cint.env.Scope;
import it.univr.cint.error.CINTRuntimeError;
import it.univr.cint.error.ReturnSignal;
import it.univr.cint.interpolation.StringInterpolation;
import it.univr.cint.lazyeval.FreeVariables;
import it.univr.cint.lazyeval.Thunk;
import it.univr.cint.type.*;
import it.univr.cint.value.*;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Interprete di CINT: valuta l'albero sintattico secondo la semantica operazionale del linguaggio.
 * <p>
 * Gira sempre dopo {@link CINTTypeSystem} e ne sfrutta le garanzie: nessun controllo di tipo viene
 * ripetuto qui. Restano invece i controlli che dipendono dai valori e non dai tipi — divisione per
 * zero, limiti degli array, profondita' della ricorsione — che sollevano {@link CINTRuntimeError} e
 * che il programma puo' intercettare con {@code try}/{@code catch}.
 * <p>
 * Lo stato e' un unico campo {@link #scope}: le entrate e le uscite dai blocchi lo spostano lungo la
 * catena, sempre dentro un {@code try/finally} in modo che un'uscita anticipata — un {@code return},
 * un errore catturato — non lasci l'ambiente disallineato.
 */
public final class CINTInterpreter extends CINTBaseVisitor<Value> {

    /**
     * Ogni chiamata CINT consuma diversi frame della pila Java: il limite tiene la ricorsione infinita
     * dentro un errore del linguaggio, catturabile, invece di farla degenerare in uno StackOverflowError
     * della JVM, che non lo è.
     */
    private static final int MAX_CALL_DEPTH = 1000;

    private final FunctionTable functions;

    private Scope<Cell> scope = new Scope<>();
    private int callDepth;

    public CINTInterpreter(FunctionTable functions) {
        this.functions = functions;
    }

    /** Esegue il comando principale del programma. La sezione delle funzioni non è eseguibile. */
    public void run(CINTParser.ProgramContext program) {
        scope = new Scope<>();
        callDepth = 0;
        visit(program.com());
    }

    // ------------------------------------------------------------------ utilita'

    private ExpValue<?> value(CINTParser.ExpContext ctx) {
        return (ExpValue<?>) visit(ctx);
    }

    private boolean condition(CINTParser.ExpContext ctx) {
        return ((BoolValue) value(ctx)).isTrue();
    }

    private int intValue(CINTParser.ExpContext ctx) {
        return ((IntValue) value(ctx)).toValue();
    }

    /**
     * Applica l'upcast implicito {@code int -> dec} nel momento in cui un valore viene <em>riposto</em>
     * da qualche parte: dichiarazione, assegnamento, legame di un parametro, valore di ritorno.
     * Concentrare qui la conversione evita di doverla ripetere in ogni punto di scrittura.
     */
    private static ExpValue<?> coerce(ExpValue<?> value, ExpType target) {
        if (target == SimpleType.DEC && value instanceof IntValue integer)
            return new DecValue(integer.toValue());
        return value;
    }

    /** Esegue un blocco nel proprio scope, ripristinando l'ambiente qualunque cosa accada dentro. */
    private void executeBlock(CINTParser.BlockContext block) {
        if (block.com() == null) return;

        Scope<Cell> enclosing = scope;
        scope = scope.push();
        try {
            visit(block.com());
        } finally {
            scope = enclosing;
        }
    }

    /** La cella denotata da un bersaglio assegnabile. */
    private Cell cellOf(CINTParser.LvalueContext ctx) {
        if (ctx instanceof CINTParser.VarLvalueContext variable)
            return scope.lookup(variable.ID().getText());

        CINTParser.CellLvalueContext element = (CINTParser.CellLvalueContext) ctx;
        ArrayValue array = (ArrayValue) cellOf(element.lvalue()).get();
        return array.cell(intValue(element.exp()));
    }

    /**
     * La cella denotata da un argomento passato per riferimento. Il type system ha gia' verificato
     * che l'espressione sia una variabile o un elemento di array.
     */
    private Cell cellOfArgument(CINTParser.ExpContext ctx) {
        while (ctx instanceof CINTParser.ParExpContext parenthesised)
            ctx = parenthesised.exp();

        if (ctx instanceof CINTParser.IdContext identifier)
            return scope.lookup(identifier.ID().getText());

        CINTParser.IndexContext index = (CINTParser.IndexContext) ctx;
        ArrayValue array = (ArrayValue) value(index.exp(0));
        return array.cell(intValue(index.exp(1)));
    }

    // ------------------------------------------------------------------ comandi

    @Override
    public Value visitDecl(CINTParser.DeclContext ctx) {
        ExpType declared = TypeUtils.fromContext(ctx.type());

        ExpValue<?> initial = ctx.exp() == null
                ? TypeUtils.defaultValue(declared)          // default deterministico, mai "non inizializzata"
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
    public Value visitLazyDecl(CINTParser.LazyDeclContext ctx) {
        ExpType declared = TypeUtils.fromContext(ctx.type());
        CINTParser.ExpContext suspended = ctx.exp();
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
    private Scope<Cell> capture(CINTParser.ExpContext exp) {
        Scope<Cell> snapshot = new Scope<>();

        for (String name : FreeVariables.of(exp)) {
            Cell cell = scope.lookup(name);
            if (cell != null) snapshot.declare(name, cell.snapshot());
        }

        return snapshot;
    }

    /** Valuta un'espressione in un ambiente diverso da quello corrente, poi ripristina. */
    private ExpValue<?> evaluateIn(Scope<Cell> environment, CINTParser.ExpContext exp) {
        Scope<Cell> saved = scope;
        scope = environment;
        try {
            return value(exp);
        } finally {
            scope = saved;
        }
    }

    @Override
    public Value visitAssign(CINTParser.AssignContext ctx) {
        Cell target = cellOf(ctx.lvalue());
        target.set(coerce(value(ctx.exp()), target.getType()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitCompoundAssign(CINTParser.CompoundAssignContext ctx) {
        Cell target = cellOf(ctx.lvalue());
        ExpValue<?> operand = value(ctx.exp());

        // 'x op= e' e' esattamente 'x = x op e', compreso l'upcast in scrittura
        int binaryOp = switch (ctx.op.getType()) {
            case CINTParser.ADD_A -> CINTParser.ADD;
            case CINTParser.SUB_A -> CINTParser.SUB;
            case CINTParser.MUL_A -> CINTParser.MUL;
            case CINTParser.DIV_A -> CINTParser.DIV;
            default               -> CINTParser.MOD;
        };

        target.set(coerce(binary(binaryOp, target.get(), operand), target.getType()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitPostCrementCom(CINTParser.PostCrementComContext ctx) {
        crement(ctx.lvalue(), ctx.op.getType());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitPreCrementCom(CINTParser.PreCrementComContext ctx) {
        crement(ctx.lvalue(), ctx.op.getType());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitIf(CINTParser.IfContext ctx) {
        if (condition(ctx.exp())) executeBlock(ctx.block());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitIfElse(CINTParser.IfElseContext ctx) {
        executeBlock(condition(ctx.exp()) ? ctx.block(0) : ctx.block(1));
        return ComValue.INSTANCE;
    }

    /** Ciclo iterativo, non ricorsivo: la profondita' della pila Java non deve dipendere dai giri. */
    @Override
    public Value visitWhile(CINTParser.WhileContext ctx) {
        while (condition(ctx.exp()))
            executeBlock(ctx.block());

        return ComValue.INSTANCE;
    }

    @Override
    public Value visitTryCatch(CINTParser.TryCatchContext ctx) {
        try {
            executeBlock(ctx.block(0));
        } catch (CINTRuntimeError error) {
            Scope<Cell> enclosing = scope;
            scope = scope.push();
            try {
                scope.declare(ctx.ID().getText(),
                        Cell.of(SimpleType.STRING, new StringValue(error.getMessage())));
                executeBlock(ctx.block(1));
            } finally {
                scope = enclosing;
            }
        }
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitThrow(CINTParser.ThrowContext ctx) {
        throw new CINTRuntimeError(((StringValue) value(ctx.exp())).toValue());
    }

    @Override
    public Value visitReturn(CINTParser.ReturnContext ctx) {
        throw new ReturnSignal(ctx.exp() == null ? null : value(ctx.exp()));
    }

    @Override
    public Value visitPrint(CINTParser.PrintContext ctx) {
        System.out.println(value(ctx.exp()));
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitCallCom(CINTParser.CallComContext ctx) {
        invoke(functions.get(ctx.ID().getText()), ctx.args());
        return ComValue.INSTANCE;
    }

    @Override
    public Value visitBlockCom(CINTParser.BlockComContext ctx) {
        executeBlock(ctx.block());
        return ComValue.INSTANCE;
    }

    /**
     * Sequenza: esegue il comando in testa e poi, se c'e', il resto della sequenza. La ricorsione
     * segue la forma della regola {@code com}, che e' ricorsiva a destra.
     */
    @Override
    public Value visitCom(CINTParser.ComContext ctx) {
        visit(ctx.simpleCom() != null ? ctx.simpleCom() : ctx.closedCom());
        return ctx.com() == null ? ComValue.INSTANCE : visit(ctx.com());
    }

    @Override
    public Value visitNop(CINTParser.NopContext ctx) {
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
    private ExpValue<?> invoke(FunctionTable.Signature signature, CINTParser.ArgsContext args) {
        List<CINTParser.ExpContext> actuals = args == null ? List.of() : args.exp();

        List<Cell> bound = new ArrayList<>(actuals.size());
        for (int i = 0; i < actuals.size(); i++) {
            FunctionTable.Param formal = signature.params().get(i);

            bound.add(formal.byRef()
                    ? cellOfArgument(actuals.get(i))
                    : Cell.of(formal.type(), coerce(value(actuals.get(i)), formal.type())));
        }

        if (++callDepth > MAX_CALL_DEPTH) {
            callDepth--;
            throw new CINTRuntimeError("ricorsione troppo profonda in '" + signature.name()
                    + "' (oltre " + MAX_CALL_DEPTH + " chiamate annidate)");
        }

        Scope<Cell> caller = scope;
        scope = new Scope<>();
        try {
            for (int i = 0; i < bound.size(); i++)
                scope.declare(signature.params().get(i).name(), bound.get(i));

            CINTParser.BlockContext body = signature.declaration().block();
            if (body.com() != null) visit(body.com());

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
    public Value visitCall(CINTParser.CallContext ctx) {
        return invoke(functions.get(ctx.ID().getText()), ctx.args());
    }

    // ------------------------------------------------------------------ espressioni

    @Override
    public Value visitNumeric(CINTParser.NumericContext ctx) {
        return visit(ctx.num());
    }

    @Override
    public Value visitIntNum(CINTParser.IntNumContext ctx) {
        return new IntValue(Integer.parseInt(ctx.INT().getText()));
    }

    @Override
    public Value visitDecNum(CINTParser.DecNumContext ctx) {
        return new DecValue(Double.parseDouble(ctx.DEC().getText()));
    }

    @Override
    public Value visitBoolean(CINTParser.BooleanContext ctx) {
        return BoolValue.of(Boolean.parseBoolean(ctx.BOOL().getText()));
    }

    @Override
    public Value visitCharacter(CINTParser.CharacterContext ctx) {
        String literal = ctx.CHAR().getText();
        String body = literal.substring(1, literal.length() - 1);
        return new CharValue(StringInterpolation.unescape(body).charAt(0));
    }

    /** Un letterale stringa e' la concatenazione dei suoi pezzi letterali e delle parti interpolate. */
    @Override
    public Value visitString(CINTParser.StringContext ctx) {
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
    public Value visitArrayLit(CINTParser.ArrayLitContext ctx) {
        List<CINTParser.ExpContext> elements = ctx.args().exp();

        List<ExpValue<?>> values = new ArrayList<>(elements.size());
        for (CINTParser.ExpContext element : elements)
            values.add(value(element));

        // il tipo degli elementi e' il piu' generale fra quelli presenti (un solo dec basta a farli dec)
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
    public Value visitArrayNew(CINTParser.ArrayNewContext ctx) {
        List<Integer> dimensions = new ArrayList<>();
        for (CINTParser.ExpContext dimension : ctx.exp())
            dimensions.add(intValue(dimension));

        return allocate(TypeUtils.fromName(ctx.TYPE().getText(), 0), dimensions, 0);
    }

    /** Alloca ricorsivamente: l'ultima dimensione riceve i valori di default, le altre sotto-array. */
    private ArrayValue allocate(ExpType base, List<Integer> dimensions, int level) {
        int size = dimensions.get(level);
        if (size < 0)
            throw new CINTRuntimeError("dimensione di array negativa: " + size);

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
    public Value visitParExp(CINTParser.ParExpContext ctx) {
        return visit(ctx.exp());
    }

    @Override
    public Value visitLen(CINTParser.LenContext ctx) {
        return new IntValue(((ArrayValue) value(ctx.exp())).length());
    }

    @Override
    public Value visitToStr(CINTParser.ToStrContext ctx) {
        return new StringValue(value(ctx.exp()).toString());
    }

    @Override
    public Value visitIndex(CINTParser.IndexContext ctx) {
        ArrayValue array = (ArrayValue) value(ctx.exp(0));
        return array.cell(intValue(ctx.exp(1))).get();
    }

    @Override
    public Value visitPow(CINTParser.PowContext ctx) {
        NumValue<?> base = (NumValue<?>) value(ctx.exp(0));
        NumValue<?> exponent = (NumValue<?>) value(ctx.exp(1));

        double result = Math.pow(base.asDouble(), exponent.asDouble());

        return bothInt(base, exponent) ? new IntValue((int) result) : new DecValue(result);
    }

    @Override
    public Value visitPostCrement(CINTParser.PostCrementContext ctx) {
        return crement(ctx.lvalue(), ctx.op.getType()).before();
    }

    @Override
    public Value visitPreCrement(CINTParser.PreCrementContext ctx) {
        return crement(ctx.lvalue(), ctx.op.getType()).after();
    }

    /** L'esito di un incremento: la semantica di Java distingue il valore prima e dopo la modifica. */
    private record Crement(ExpValue<?> before, ExpValue<?> after) { }

    private Crement crement(CINTParser.LvalueContext lvalue, int op) {
        Cell cell = cellOf(lvalue);
        ExpValue<?> before = cell.get();
        int step = op == CINTParser.INCR ? 1 : -1;

        ExpValue<?> after = before instanceof IntValue integer
                ? new IntValue(integer.toValue() + step)
                : new DecValue(((DecValue) before).toValue() + step);

        cell.set(after);
        return new Crement(before, after);
    }

    @Override
    public Value visitCast(CINTParser.CastContext ctx) {
        return TypeUtils.cast(value(ctx.exp()), TypeUtils.fromName(ctx.TYPE().getText(), 0));
    }

    @Override
    public Value visitNot(CINTParser.NotContext ctx) {
        return BoolValue.of(!condition(ctx.exp()));
    }

    @Override
    public Value visitNeg(CINTParser.NegContext ctx) {
        NumValue<?> operand = (NumValue<?>) value(ctx.exp());

        return operand instanceof IntValue integer
                ? new IntValue(-integer.toValue())
                : new DecValue(-operand.asDouble());
    }

    @Override
    public Value visitMulDivMod(CINTParser.MulDivModContext ctx) {
        return binary(ctx.op.getType(), value(ctx.exp(0)), value(ctx.exp(1)));
    }

    @Override
    public Value visitAddSub(CINTParser.AddSubContext ctx) {
        return binary(ctx.op.getType(), value(ctx.exp(0)), value(ctx.exp(1)));
    }

    /**
     * Le operazioni binarie aritmetiche. Se entrambi gli operandi sono interi il conto si fa fra
     * interi: passare sempre dai double, come nelle esercitazioni, perderebbe precisione sugli
     * interi grandi e nasconderebbe la divisione per zero dietro a un {@code Infinity}.
     */
    private ExpValue<?> binary(int op, ExpValue<?> left, ExpValue<?> right) {
        if (op == CINTParser.ADD && (left instanceof StringValue || right instanceof StringValue))
            return new StringValue(left.toString() + right);

        NumValue<?> a = (NumValue<?>) left;
        NumValue<?> b = (NumValue<?>) right;

        if (bothInt(a, b)) {
            int x = ((IntValue) a).toValue();
            int y = ((IntValue) b).toValue();

            return new IntValue(switch (op) {
                case CINTParser.ADD -> x + y;
                case CINTParser.SUB -> x - y;
                case CINTParser.MUL -> x * y;
                case CINTParser.DIV -> divide(x, y);
                default             -> modulo(x, y);
            });
        }

        double x = a.asDouble();
        double y = b.asDouble();

        return new DecValue(switch (op) {
            case CINTParser.ADD -> x + y;
            case CINTParser.SUB -> x - y;
            case CINTParser.MUL -> x * y;
            case CINTParser.DIV -> divide(x, y);
            default             -> modulo(x, y);
        });
    }

    private static boolean bothInt(NumValue<?> left, NumValue<?> right) {
        return left instanceof IntValue && right instanceof IntValue;
    }

    private static int divide(int dividend, int divisor) {
        if (divisor == 0) throw new CINTRuntimeError("divisione per zero");
        return dividend / divisor;
    }

    private static double divide(double dividend, double divisor) {
        if (divisor == 0.0) throw new CINTRuntimeError("divisione per zero");
        return dividend / divisor;
    }

    private static int modulo(int dividend, int divisor) {
        if (divisor == 0) throw new CINTRuntimeError("resto con divisore zero");
        return dividend % divisor;
    }

    private static double modulo(double dividend, double divisor) {
        if (divisor == 0.0) throw new CINTRuntimeError("resto con divisore zero");
        return dividend % divisor;
    }

    @Override
    public Value visitCmpExp(CINTParser.CmpExpContext ctx) {
        ExpValue<?> left = value(ctx.exp(0));
        ExpValue<?> right = value(ctx.exp(1));

        double a = left instanceof CharValue character ? character.toValue() : ((NumValue<?>) left).asDouble();
        double b = right instanceof CharValue character ? character.toValue() : ((NumValue<?>) right).asDouble();

        return BoolValue.of(switch (ctx.op.getType()) {
            case CINTParser.LT  -> a < b;
            case CINTParser.LEQ -> a <= b;
            case CINTParser.GT  -> a > b;
            default             -> a >= b;
        });
    }

    @Override
    public Value visitEqExp(CINTParser.EqExpContext ctx) {
        ExpValue<?> left = value(ctx.exp(0));
        ExpValue<?> right = value(ctx.exp(1));

        // il confronto misto int/dec passa per il valore numerico, cosi' 1 == 1.0 e' vero
        boolean equal = (left instanceof NumValue<?> a && right instanceof NumValue<?> b)
                ? a.asDouble() == b.asDouble()
                : left.equals(right);

        return BoolValue.of(ctx.op.getType() == CINTParser.EQQ ? equal : !equal);
    }

    /**
     * Congiunzione e disgiunzione sono valutate in <b>corto circuito</b>, come in C e in Java:
     * il secondo operando non viene toccato se il primo gia' determina il risultato.
     */
    @Override
    public Value visitAnd(CINTParser.AndContext ctx) {
        return BoolValue.of(condition(ctx.exp(0)) && condition(ctx.exp(1)));
    }

    @Override
    public Value visitOr(CINTParser.OrContext ctx) {
        return BoolValue.of(condition(ctx.exp(0)) || condition(ctx.exp(1)));
    }

    @Override
    public Value visitTernary(CINTParser.TernaryContext ctx) {
        return value(condition(ctx.exp(0)) ? ctx.exp(1) : ctx.exp(2));
    }

    @Override
    public Value visitId(CINTParser.IdContext ctx) {
        return scope.lookup(ctx.ID().getText()).get(); // get() forza un'eventuale cella pigra
    }
}
