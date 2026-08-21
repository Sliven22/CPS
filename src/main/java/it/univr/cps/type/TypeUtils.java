package it.univr.cps.type;

import it.univr.cps.CPSParser;
import it.univr.cps.env.Cell;
import it.univr.cps.error.CPSRuntimeError;
import it.univr.cps.value.*;

/** Funzioni di servizio sui tipi: costruzione, inferenza dai valori, conversioni, valori di default. */
public final class TypeUtils {

    private TypeUtils() { }

    /** Traduce l'annotazione di tipo scritta nel sorgente ({@code int}, {@code array real}, ...). */
    public static ExpType fromContext(CPSParser.TypeContext ctx) {
        return fromName(ctx.TYPE().getText(), ctx.ARRAY().size());
    }

    /** Tipo completo di una dichiarazione CPS, incluse le dimensioni scritte dopo il nome. */
    public static ExpType fromDeclaration(CPSParser.DeclContext ctx) {
        CPSParser.TypeContext type = ctx.type();
        int depth = Math.max(type.ARRAY().size(), ctx.dimensions().size());
        return fromName(type.TYPE().getText(), depth);
    }

    /** Costruisce il tipo a partire dal nome primitivo e dal numero di coppie di parentesi quadre. */
    public static ExpType fromName(String name, int arrayDepth) {
        ExpType type = switch (name) {
            case "int"    -> SimpleType.INT;
            case "real"   -> SimpleType.REAL;
            case "char"   -> SimpleType.CHAR;
            case "bool"   -> SimpleType.BOOL;
            case "string" -> SimpleType.STRING;
            default       -> throw new IllegalArgumentException("tipo sconosciuto: " + name);
        };
        for (int i = 0; i < arrayDepth; i++)
            type = new ArrayType(type);
        return type;
    }

    public static ExpType fromValue(ExpValue<?> value) {
        if (value instanceof IntValue)    return SimpleType.INT;
        if (value instanceof RealValue)   return SimpleType.REAL;
        if (value instanceof CharValue)   return SimpleType.CHAR;
        if (value instanceof BoolValue)   return SimpleType.BOOL;
        if (value instanceof StringValue) return SimpleType.STRING;
        if (value instanceof ArrayValue array) return new ArrayType(array.getElementType());
        throw new IllegalArgumentException("valore di tipo sconosciuto: " + value);
    }

    public static boolean isNumeric(Type type) {
        return type == SimpleType.INT || type == SimpleType.REAL;
    }

    /** I tipi su cui hanno senso gli operatori d'ordine: i numerici piu' {@code char}. */
    public static boolean isOrdered(Type type) {
        return isNumeric(type) || type == SimpleType.CHAR;
    }

    /**
     * Il tipo del risultato di un'operazione aritmetica fra due tipi numerici: {@code real} se almeno
     * un operando e' {@code real}, altrimenti {@code int}.
     */
    public static SimpleType arithmeticJoin(Type left, Type right) {
        return (left == SimpleType.REAL || right == SimpleType.REAL) ? SimpleType.REAL : SimpleType.INT;
    }

    /**
     * Il cast esplicito e' ammesso solo all'interno del gruppo {@code int}/{@code real}/{@code char},
     * cioe' fra i tipi che condividono una rappresentazione numerica. Un cast verso lo stesso tipo e'
     * sempre lecito; {@code bool}, {@code string} e gli array non sono convertibili.
     */
    public static boolean canCast(ExpType from, ExpType to) {
        if (from.equals(to)) return true;
        return isConvertibleGroup(from) && isConvertibleGroup(to);
    }

    private static boolean isConvertibleGroup(ExpType type) {
        return type == SimpleType.INT || type == SimpleType.REAL || type == SimpleType.CHAR;
    }

    /** Applica il cast a runtime. Il type system ha gia' verificato che la conversione sia lecita. */
    public static ExpValue<?> cast(ExpValue<?> value, ExpType target) {
        if (target == SimpleType.INT)  return new IntValue(toInt(value));
        if (target == SimpleType.REAL) return new RealValue(toDouble(value));
        if (target == SimpleType.CHAR) return new CharValue(toChar(value));
        return value; // cast identita'
    }

    private static int toInt(ExpValue<?> value) {
        if (value instanceof NumValue<?> num) return (int) num.asDouble();
        if (value instanceof CharValue c)     return c.toValue();
        throw new CPSRuntimeError("conversione a int non applicabile a " + value);
    }

    private static double toDouble(ExpValue<?> value) {
        if (value instanceof NumValue<?> num) return num.asDouble();
        if (value instanceof CharValue c)     return c.toValue();
        throw new CPSRuntimeError("conversione a real non applicabile a " + value);
    }

    private static char toChar(ExpValue<?> value) {
        if (value instanceof CharValue c) return c.toValue();
        if (value instanceof NumValue<?> num) {
            int code = (int) num.asDouble();
            if (code < Character.MIN_VALUE || code > Character.MAX_VALUE)
                throw new CPSRuntimeError("il codice " + code + " non corrisponde ad alcun char");
            return (char) code;
        }
        throw new CPSRuntimeError("conversione a char non applicabile a " + value);
    }

    /**
     * Il valore con cui nasce una variabile dichiarata senza inizializzatore, e con cui vengono
     * riempite le celle di un array appena allocato. CPS preferisce un default deterministico a un
     * errore di "variabile non inizializzata", che richiederebbe un'analisi di assegnamento definito.
     */
    public static ExpValue<?> defaultValue(ExpType type) {
        if (type == SimpleType.INT)    return new IntValue(0);
        if (type == SimpleType.REAL)   return new RealValue(0.0);
        if (type == SimpleType.CHAR)   return new CharValue('\0');
        if (type == SimpleType.BOOL)   return BoolValue.FALSE;
        if (type == SimpleType.STRING) return new StringValue("");
        if (type instanceof ArrayType array) return new ArrayValue(array.getElementType(), new Cell[0]);
        throw new IllegalArgumentException("nessun valore di default per " + type.getName());
    }
}
