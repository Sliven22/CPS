# CINT

Interprete di **CINT**, un linguaggio imperativo tipizzato staticamente con sintassi ispirata al C.
Il progetto è realizzato per il Laboratorio di Linguaggi (A.A. 2025/2026) e usa ANTLR per generare
lexer, parser e visitor.

La descrizione completa della sintassi e della semantica è disponibile in [`doc.md`](doc.md).
Questo README contiene invece le istruzioni necessarie per installare i requisiti, compilare ed
eseguire il progetto.

## Requisiti

- **JDK 25 o successivo**. Il `pom.xml` compila con `maven.compiler.source` e `maven.compiler.target`
  impostati a `25`.
- **Maven 3.9 o successivo**.
- Accesso alla rete alla prima compilazione, per scaricare Maven e le dipendenze ANTLR. Dopo il
  download, Maven utilizza la propria cache locale.

Verifica gli strumenti dal terminale:

```bash
java -version
mvn -version
```

La versione del runtime ANTLR utilizzata dal progetto è **4.13.2**. Viene scaricata automaticamente
dal plugin Maven, quindi non è necessario installare ANTLR separatamente.

## Ottenere il progetto

Entrare nella directory principale del progetto:

```bash
cd ~/Project
```

Se il progetto è stato copiato in un'altra posizione, usare il percorso della nuova directory.

## Compilazione

Per cancellare gli artefatti precedenti, generare il codice ANTLR, compilare le classi Java ed
assemblare il JAR eseguibile:

```bash
mvn clean package
```

La compilazione esegue queste fasi principali:

1. `clean` elimina la directory `target/` precedente;
2. `generate-sources` esegue `antlr4-maven-plugin` sulla grammatica `CINT.g4`;
3. `compile` compila i sorgenti Java con JDK 25;
4. `test` esegue gli eventuali test presenti;
5. `package` crea i JAR e l'assembly con le dipendenze.

Al termine, nella directory `target/` sono disponibili:

```text
CINT-1.0-SNAPSHOT.jar
CINT-1.0-SNAPSHOT-jar-with-dependencies.jar
generated-sources/antlr4/     sorgenti generati da ANTLR
```

Per l'esecuzione usare il file `*-jar-with-dependencies.jar`, che contiene anche il runtime ANTLR.

### Maven incluso in IntelliJ IDEA

Se `mvn` non è presente nel `PATH`, è possibile usare il Maven distribuito con IntelliJ IDEA. Su
macOS, un'installazione standard può essere avviata così:

```bash
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" clean package
```

In alternativa si può eseguire il goal `package` dalla finestra Maven di IntelliJ.

## Esecuzione di un programma CINT

L'interprete accetta **un solo argomento**, cioè il percorso del file sorgente `.cint`:

```bash
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/hello.cint
```

Output:

```text
Hello, CINT!
```

Il file sorgente non viene passato tramite input standard. Per eseguire un altro esempio, sostituire
semplicemente il percorso:

```bash
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/fattoriale.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/fibonacci.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/bubblesort.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/matrice.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/lazy.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/zucchero.cint
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/errori.cint
```

È possibile eseguire tutti gli esempi, uno alla volta, con:

```bash
for file in programs/*.cint; do
    echo "--- $file"
    java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar "$file"
done
```

## Codici di uscita

`MainCINT` usa codici diversi per distinguere i problemi:

| Codice | Significato |
|---:|---|
| `0` | esecuzione completata correttamente |
| `2` | numero errato di argomenti, file inesistente/non leggibile o errore di lettura |
| `3` | errore lessicale o sintattico |
| `4` | errore statico: tipo, dichiarazione o firma di funzione non valida |
| `5` | errore runtime non intercettato dal programma |

Gli errori runtime, come divisione per zero o indice fuori dai limiti, possono essere gestiti dal
programma con `try`/`catch`. Gli errori statici non sono intercettabili e impediscono sempre
l'esecuzione.

## Esempio di programma

Creare, per esempio, un file `esempio.cint`:

```cint
int quadrato(int n) {
    return n * n
}

int i = 1;
while (i <= 5) {
    print "il quadrato di ${i} e' ${quadrato(i)}";
    i++
}
```

Eseguirlo dalla directory del progetto:

```bash
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar esempio.cint
```

Il punto e virgola separa i comandi semplici. L'ultimo comando di un blocco può non averlo, come
`i++` nell'esempio.

## Funzionalità del linguaggio

CINT supporta:

- tipi `int`, `dec`, `char`, `bool`, `string`;
- array monodimensionali e multidimensionali;
- dichiarazioni, assegnamenti, `if`, `while`, `try`/`catch`, `throw`, `return`, `print` e `nop`;
- funzioni, ricorsione, passaggio per valore e passaggio per riferimento con `ref`;
- incrementi/decrementi prefissi e postfissi (`++`, `--`);
- assegnamenti composti (`+=`, `-=`, `*=`, `/=`, `%=`);
- operatore ternario (`condizione ? valore1 : valore2`);
- interpolazione delle stringhe con `${espressione}`;
- valutazione pigra con `lazy`, cattura per valore e memoizzazione;
- controllo statico completo prima dell'esecuzione;
- errori runtime gestibili con `try`/`catch`.

Limitazioni sintattiche importanti:

- non esiste il ciclo `for`: usare `while`;
- non esistono `break` e `continue`;
- dentro `${...}` non si possono usare stringhe delimitate da doppi apici annidate;
- il `;` è un separatore, non un terminatore obbligatorio dell'ultimo comando.

Per regole dettagliate su tipi, precedenza degli operatori, scope, array, funzioni, lazy evaluation e
semantica operazionale, consultare [`doc.md`](doc.md).

## Struttura del progetto

```text
Project/
├── README.md
├── doc.md
├── pom.xml
├── programs/                         programmi CINT di esempio
└── src/main/
    ├── antlr4/it/univr/cint/CINT.g4   grammatica ANTLR
    └── java/it/univr/cint/
        ├── MainCINT.java              punto d'ingresso
        ├── CINTTypeSystem.java        controllo statico
        ├── CINTInterpreter.java       esecuzione
        ├── env/                       scope, celle e funzioni
        ├── type/                      gerarchia dei tipi
        ├── value/                     valori runtime
        ├── lazyeval/                  thunk e variabili libere
        ├── interpolation/             stringhe interpolate
        └── error/                     errori e segnali di controllo
```

La grammatica viene modificata in `src/main/antlr4/it/univr/cint/CINT.g4`; i sorgenti generati non
devono essere modificati a mano, perché vengono ricreati durante `mvn clean package`.

## Sviluppo e verifica

Dopo ogni modifica è consigliato eseguire:

```bash
mvn clean package
java -jar target/CINT-1.0-SNAPSHOT-jar-with-dependencies.jar programs/hello.cint
```

Per una verifica più ampia, eseguire anche i programmi presenti in `programs/`. Attualmente il
progetto non contiene una suite di test in `src/test`; gli esempi sono quindi controlli funzionali
manuali (smoke test).

## Contesto

Il progetto è materiale didattico del Laboratorio di Linguaggi, A.A. 2025/2026. Consultare i file
presenti nel repository per eventuali informazioni aggiuntive sulla consegna.
