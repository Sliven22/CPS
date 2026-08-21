package it.univr.cps.interpolation;

import it.univr.cps.CPSLexer;
import it.univr.cps.CPSParser;
import it.univr.cps.error.CPSErrorListener;
import it.univr.cps.error.SyntaxError;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scompone un letterale stringa nei suoi pezzi letterali e nelle espressioni interpolate
 * {@code ${...}}, come in {@code "Risultato: ${x + y}"}.
 * <p>
 * <b>Perche' non i lexer mode di ANTLR.</b> La soluzione idiomatica sarebbe entrare in un mode
 * dedicato all'apertura di {@code ${} e uscirne alla graffa di chiusura. Per gestire correttamente
 * le espressioni annidate servirebbe pero' un contatore di profondita' gestito da azioni lessicali,
 * cioe' codice Java dentro la grammatica, che renderebbe il {@code .g4} dipendente dal linguaggio
 * di implementazione.
 * <p>
 * Si e' preferito tenere la grammatica pulita: il lexer produce un unico token {@code STRING} e la
 * scomposizione avviene qui, riparsando ogni frammento con un parser CPS usa e getta. Il prezzo e'
 * che le posizioni riportate per un errore dentro un frammento sono relative al frammento stesso.
 * <p>
 * Il risultato e' messo in cache sul testo del token: senza cache, una stringa interpolata dentro un
 * ciclo verrebbe riparsata a ogni iterazione.
 */
public final class StringInterpolation {

    /** Un pezzo di stringa: testo costante oppure espressione da valutare. */
    public sealed interface Part permits Literal, Interpolated { }

    public record Literal(String text) implements Part { }

    public record Interpolated(CPSParser.ExpContext exp, String source) implements Part { }

    private static final Map<String, List<Part>> CACHE = new HashMap<>();

    private StringInterpolation() { }

    /**
     * @param token il testo del token {@code STRING}, virgolette comprese
     */
    public static List<Part> parse(String token) {
        return CACHE.computeIfAbsent(token, StringInterpolation::split);
    }

    private static List<Part> split(String token) {
        String body = token.substring(1, token.length() - 1); // via le virgolette

        List<Part> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();

        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);

            if (c == '\\' && i + 1 < body.length()) {
                literal.append(unescapeOne(body.charAt(i + 1)));
                i += 2;
                continue;
            }

            if (c == '$' && i + 1 < body.length() && body.charAt(i + 1) == '{') {
                int close = matchingBrace(body, i + 1);
                String source = body.substring(i + 2, close);

                if (!literal.isEmpty()) {
                    parts.add(new Literal(literal.toString()));
                    literal.setLength(0);
                }
                parts.add(new Interpolated(parseExpression(source), source));

                i = close + 1;
                continue;
            }

            literal.append(c);
            i++;
        }

        if (!literal.isEmpty() || parts.isEmpty())
            parts.add(new Literal(literal.toString()));

        return List.copyOf(parts);
    }

    /** Indice della graffa che chiude quella in {@code open}, tenendo conto dell'annidamento. */
    private static int matchingBrace(String body, int open) {
        int depth = 0;
        for (int i = open; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        throw new SyntaxError("interpolazione senza graffa di chiusura in \"" + body + "\"");
    }

    /** Compila il frammento fra graffe con un parser CPS usa e getta. */
    private static CPSParser.ExpContext parseExpression(String source) {
        if (source.isBlank())
            throw new SyntaxError("interpolazione vuota: ${}");

        CPSLexer lexer = new CPSLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(CPSErrorListener.INSTANCE);

        CPSParser parser = new CPSParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(CPSErrorListener.INSTANCE);

        CPSParser.ExpContext exp = parser.exp();

        // parser.exp() si ferma appena ha un'espressione completa: senza questo controllo
        // "${x y}" passerebbe silenziosamente come "${x}".
        if (parser.getCurrentToken().getType() != Token.EOF)
            throw new SyntaxError("interpolazione malformata: ${" + source + "}");

        return exp;
    }

    /** Applica le sequenze di escape a un letterale stringa o carattere. */
    public static String unescape(String body) {
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                out.append(unescapeOne(body.charAt(++i)));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static char unescapeOne(char escaped) {
        return switch (escaped) {
            case 'b'  -> '\b';
            case 't'  -> '\t';
            case 'n'  -> '\n';
            case 'f'  -> '\f';
            case 'r'  -> '\r';
            case '"'  -> '"';
            case '\'' -> '\'';
            case '\\' -> '\\';
            case '$'  -> '$';
            default   -> escaped;
        };
    }
}
