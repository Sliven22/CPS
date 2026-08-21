package it.univr.cps;

import it.univr.cps.env.FunctionTable;
import it.univr.cps.error.CPSErrorListener;
import it.univr.cps.error.CPSRuntimeError;
import it.univr.cps.error.StaticError;
import it.univr.cps.error.SyntaxError;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Punto d'ingresso dell'interprete: legge un sorgente {@code .cps} e lo esegue.
 * <p>
 * Le quattro fasi sono nettamente separate — analisi lessicale e sintattica, raccolta delle firme
 * delle funzioni, controllo dei tipi, esecuzione — e ciascuna deve concludersi prima che inizi la
 * successiva. In particolare, un errore di tipo impedisce l'esecuzione di <em>qualunque</em> parte
 * del programma, anche di quella corretta che lo precede.
 */
public final class MainCPS {

    private static final int EXIT_USAGE   = 2;
    private static final int EXIT_SYNTAX  = 3;
    private static final int EXIT_STATIC  = 4;
    private static final int EXIT_RUNTIME = 5;

    private MainCPS() { }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("uso: cps <programma.cps>");
            System.exit(EXIT_USAGE);
        }

        Path source = Path.of(args[0]);
        if (!Files.isReadable(source)) {
            System.err.println("file non leggibile: " + source);
            System.exit(EXIT_USAGE);
        }

        try {
            execute(CharStreams.fromPath(source));
        } catch (IOException e) {
            System.err.println("lettura fallita: " + e.getMessage());
            System.exit(EXIT_USAGE);
        } catch (SyntaxError e) {
            System.err.println("Errore di sintassi: " + e.getMessage());
            System.exit(EXIT_SYNTAX);
        } catch (StaticError e) {
            System.err.println("Errore statico: " + e.getMessage());
            System.exit(EXIT_STATIC);
        } catch (CPSRuntimeError e) {
            // un errore runtime che nessun try/catch del programma ha intercettato
            System.err.println("Errore a tempo d'esecuzione non gestito: " + e.getMessage());
            System.exit(EXIT_RUNTIME);
        }
    }

    private static void execute(CharStream input) {
        CPSLexer lexer = new CPSLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(CPSErrorListener.INSTANCE);

        CPSParser parser = new CPSParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(CPSErrorListener.INSTANCE);

        CPSParser.ProgramContext program = parser.program();

        FunctionTable functions = FunctionTable.collect(program);

        new CPSTypeSystem(functions).check(program);
        new CPSInterpreter(functions).run(program);
    }
}
