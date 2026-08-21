package it.univr.cps;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import it.univr.cps.env.FunctionTable;
import it.univr.cps.error.CPSErrorListener;

import java.io.File;
import java.util.Arrays;

public class TestAll {

    public static void main(String[] args) {
        File dir = new File("programs");
        File[] files = dir.listFiles((d, name) -> name.endsWith(".cps"));

        if (files == null || files.length == 0) {
            System.err.println("Nessun file .cps trovato nella cartella programs!");
            return;
        }

        // Ordina alfabeticamente i file di test
        Arrays.sort(files);

        System.out.println("==================================================");
        System.out.println("   AVVIO ESECUZIONE AUTOMATICA DI TUTTI I TEST    ");
        System.out.println("==================================================\n");

        int superati = 0;
        int falliti = 0;

        for (File file : files) {
            System.out.println("--------------------------------------------------");
            System.out.println(">> Esecuzione: " + file.getName());
            System.out.println("--------------------------------------------------");

            try {
                // 1. Analisi lessicale e sintattica (Lexer e Parser ANTLR)
                CPSLexer lexer = new CPSLexer(CharStreams.fromPath(file.toPath()));
                lexer.removeErrorListeners();
                lexer.addErrorListener(CPSErrorListener.INSTANCE);

                CPSParser parser = new CPSParser(new CommonTokenStream(lexer));
                parser.removeErrorListeners();
                parser.addErrorListener(CPSErrorListener.INSTANCE);

                CPSParser.ProgramContext program = parser.program();

                // 2. Raccolta funzioni e controllo statico dei tipi (Type System)
                FunctionTable functions = FunctionTable.collect(program);
                new CPSTypeSystem(functions).check(program);

                // 3. Esecuzione del programma (Interprete Visitor)
                new CPSInterpreter(functions).run(program);

                System.out.println("\n[OK] " + file.getName() + " completato con successo.\n");
                superati++;

            } catch (Throwable t) {
                // Cattura sia Exception che Error (incluso StackOverflowError)
                System.out.println("\n[INFO/ERRORE INTERCETTATO]: " + t.getClass().getSimpleName() + " - " + t.getMessage() + "\n");
                if (file.getName().equals("errori.cps")) {
                    superati++; // In errori.cps l'errore o l'eccezione fa parte della suite di test
                } else {
                    falliti++;
                }
            }
        }

        System.out.println("==================================================");
        System.out.println("               RIASSUNTO DEI TEST                 ");
        System.out.println("==================================================");
        System.out.println("Totale programmi: " + files.length);
        System.out.println("Superati:         " + superati);
        System.out.println("Falliti:          " + falliti);
        System.out.println("==================================================");
    }
}