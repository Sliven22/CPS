# CPS

*Laboratorio di Linguaggi — A.A. 2025/2026*

---

## Introduzione

**CPS — C-Like Pseudocode Script** è un linguaggio di programmazione imperativo, **tipizzato
staticamente**, general-purpose, con una sintassi pseudocodice di impronta C. Le dichiarazioni di
funzione sono non eseguibili e possono essere intercalate alle istruzioni dello script.

Le caratteristiche principali sono:

- cinque tipi primitivi — `int`, `real`, `char`, `bool`, `string` — più gli **array**, definiti per
  ricorsione sul tipo degli elementi e quindi multidimensionali senza casi speciali;
- **controllo dei tipi statico e completo**: un programma che contiene un errore di tipo non viene
  eseguito affatto, nemmeno nella parte corretta che precede l'errore;
- **blocchi con visibilità riservata** e shadowing, realizzati da una catena di scope condivisa fra
  controllo dei tipi ed esecuzione;
- **gestione programmatica degli errori a tempo d'esecuzione**, con `try`/`catch` e `throw`;
- quattro **funzionalità avanzate**: funzioni, array, zucchero sintattico e valutazione pigra.

### Funzionalità avanzate implementate

Il gruppo è composto da due persone, quindi il minimo richiesto è di due funzionalità avanzate. Ne
sono state implementate **quattro**, fra cui una di complessità media.

| Funzionalità | Complessità | Dove |
|---|---|---|
| **Funzioni** — dichiarazione, chiamata, ricorsione anche mutua, passaggio per valore e per riferimento | **media** | [§ Funzioni](#funzioni) |
| **Strutture Dati** — array a dimensione variabile, multidimensionali, per riferimento | semplice | [§ Array](#array) |
| **Zucchero Sintattico** — `++`/`--`, assegnamenti composti, ternario, interpolazione | semplice | [§ Zucchero sintattico](#zucchero-sintattico) |
| **Valutazione Pigra** — assegnamenti `lazy` con cattura per valore e memoizzazione | semplice | [§ Valutazione pigra](#valutazione-pigra) |

### Contesto d'applicazione

CPS nasce come linguaggio **didattico per l'insegnamento della programmazione imperativa**: un
sottoinsieme di C ridotto all'essenziale, ma con tre scelte pensate per rendere visibili concetti che
in C restano impliciti o pericolosi.

La prima è che **ogni errore a tempo d'esecuzione è un errore del linguaggio, mai un comportamento
indefinito**: la divisione per zero, l'indice fuori dai limiti e la ricorsione senza caso base
producono un messaggio diagnostico che il programma può intercettare, invece di un valore spazzatura
o di un crash della macchina sottostante. Chi impara vede subito *cosa* è andato storto.

La seconda è che le **funzioni sono chiuse rispetto allo stato globale**: comunicano solo attraverso
parametri e valore di ritorno. È un vincolo severo, che però rende immediatamente leggibile la
differenza fra passaggio per valore e passaggio per riferimento — la stessa distinzione che in C si
studia attraverso i puntatori, qui isolata dal resto.

La terza è la **valutazione pigra come costrutto esplicito**: `lazy` permette di mostrare in poche
righe la differenza fra *definire* un calcolo e *eseguirlo*, un concetto che di norma si incontra solo
passando a un linguaggio funzionale.

---

## Guida Rapida

### Requisiti

- **JDK 25** o successivo
- **Maven 3.9** o successivo (o l'esecuzione della configurazione Maven da IDE)

Le uniche dipendenze esterne sono il runtime di ANTLR 4.13.2 e il relativo plugin Maven, scaricati
automaticamente alla prima compilazione.

### Generare l'interprete

Il plugin `antlr4-maven-plugin` genera lexer, parser e visitor a partire da `CPS.g4` durante la fase
`generate-sources`; il plugin `maven-assembly-plugin` produce un jar eseguibile autocontenuto.

```
cd Project
mvn clean package
```

Al termine si trovano in `target/`:

- `CPS-1.0-SNAPSHOT.jar` — solo le classi dell'interprete;
- `CPS-1.0-SNAPSHOT-jar-with-dependencies.jar` — jar eseguibile, runtime ANTLR incluso.

I sorgenti generati da ANTLR finiscono in `target/generated-sources/antlr4`.

### Eseguire un programma

```
java -jar target/CPS-1.0-SNAPSHOT-jar-with-dependencies.jar programs/hello.cps
```

L'interprete accetta esattamente un argomento, il percorso di un file sorgente. I codici di uscita
distinguono il tipo di problema:

| Codice | Significato |
|---|---|
| `0` | esecuzione conclusa correttamente |
| `2` | uso errato della riga di comando, o file non leggibile |
| `3` | errore lessicale o sintattico |
| `4` | errore statico (tipi, dichiarazioni) |
| `5` | errore a tempo d'esecuzione non intercettato dal programma |

### Hello world

`programs/hello.cps`:

```
print "Hello, CPS!";
```

```
$ java -jar target/CPS-1.0-SNAPSHOT-jar-with-dependencies.jar programs/hello.cps
Hello, CPS!
```

Un secondo esempio, che mostra tipi, ciclo, funzione e concatenazione nelle stampe:

```
function quadrato(int n) -> int:
    return n * n;
end

int i = 1;
while (i <= 5) do:
    print "il quadrato di " + i + " e' " + quadrato(i);
    i++;
end
```

### Struttura della consegna

```
Project/
├── doc.md                             questo documento
├── pom.xml                            build Maven
├── programs/                          programmi d'esempio
└── src/main/
    ├── antlr4/it/univr/cps/CPS.g4   grammatica ANTLR del linguaggio
    └── java/it/univr/cps/            sorgenti Java dell'interprete
```

Il progetto segue il layout Maven standard: i sorgenti Java stanno in `src/main/java` e la grammatica
in `src/main/antlr4`, sotto la cartella corrispondente al package `it.univr.cps`. Il `pom.xml` non ha
quindi bisogno di alcuna personalizzazione dei percorsi: ANTLR ricava il package dalla posizione del
file `.g4` e genera lexer, parser e visitor già nel package giusto.

---

## Sintassi

Il riferimento completo è `CPS.g4`. Quanto segue ne è la lettura discorsiva.

### Struttura di un programma

```
program : com EOF ;
com     : funDecl com? | simpleCom ... | closedCom ... ;
```

Le dichiarazioni `function ... -> ...: ... end` sono **non eseguibili** e possono comparire anche
dopo istruzioni già presenti nello script. Le funzioni vengono raccolte in una passata preliminare;
il comando principale esegue invece tutte le altre istruzioni nell'ordine in cui compaiono.

### Tipi di dato

| Tipo | Valori | Letterali |
|---|---|---|
| `int` | interi con segno a 32 bit | `0`, `42`, `1000` |
| `real` | numeri in virgola mobile a 64 bit | `3.14`, `0.5`, `2.0` |
| `char` | un carattere | `'a'`, `'Z'`, `'\n'` |
| `bool` | valori di verità | `true`, `false` |
| `string` | sequenze di caratteri | `"ciao"`, `"con ${interpolazione}"` |
| `array T` | array di `T`, anche multidimensionale | `[1, 2, 3]`, `array int valori[3]` |

I letterali numerici sono **senza segno**: il meno unario è un operatore. È una differenza rispetto a
una grammatica in cui `INT` includa il segno, dove `3-2` verrebbe lessicalizzato come i due token
`3` e `-2` invece che come una sottrazione.

Una variabile dichiarata senza inizializzatore riceve un **valore di default deterministico**
(`0`, `0.0`, `'\0'`, `false`, `""`, array vuoto), e lo stesso vale per le celle di un array appena
allocato. La scelta evita sia il comportamento indefinito del C sia la necessità di un'analisi di
assegnamento definito.

### Comandi

```
int x = 5;                            dichiarazione, con o senza inizializzatore
array int valori[3];                  array con dimensione
lazy int y = costoso();               dichiarazione pigra
x = 7;                                assegnamento
x += 2;   x++;   --x;                 assegnamento composto e crementi
if (c): ... else: ... end             condizionale
while (c) do: ... end                 iterazione
do: ... while (c);                    iterazione con test finale
for int x in valori do: ... end       iterazione su array
try: ... catch (e): ... end            gestione degli errori runtime
throw "messaggio";                    sollevamento di un errore
return e;                             uscita da una funzione
print e;                              stampa
f(a, b);                              chiamata di procedura
nop                                   comando nullo
c1 ; c2                               sequenza
```

Il punto e virgola è un **separatore**, non un terminatore: si scrive *fra* due comandi, quindi
l'ultimo comando di un blocco o di un programma non ne ha bisogno.

I comandi si dividono in due specie, e la regola del `;` dipende da quale delle due si sta scrivendo:

| Specie | Comandi | Separazione dal comando successivo |
|---|---|---|
| **semplici** | dichiarazione, assegnamento, crementi, `throw`, `return`, `print`, chiamata, `nop` | `;` obbligatorio |
| **strutturati chiusi da `end`** | `if`, `if/else`, `while`, `for`, `try/catch` | nessun `;` dopo `end` |
| **`do/while`** | `do: ... while (c);` | il `;` finale fa parte del costrutto |

Nei costrutti strutturati CPS `:` apre il corpo. `end` chiude `if`, `while`, `for` e `try/catch`;
`do` viene invece chiuso da `while (condizione);`. Prima di `end` si può omettere il `;` dell'ultimo
comando semplice.

```
int i = 0;                  // comando semplice: ';' prima del prossimo
while (i < 3) do:
    print i;                // semplice: ';'
    i++                     // ultimo del blocco: niente ';'
end                         // chiuso da end: niente ';'
print "fatto";
```

La grammatica CPS non usa parentesi graffe per i blocchi: usa `:` con `end`, oppure il `while` finale
nel caso di `do/while`.

### Espressioni

In ordine di precedenza decrescente:

| Livello | Operatori |
|---|---|
| primarie | letterali, `(e)`, `f(...)`, `len(e)`, `toStr(e)`, `new T[n]`, `[e, ...]`, `e[i]` |
| potenza | `^` (associativo a destra) |
| unari | `++x`, `x++`, `--x`, `x--`, `(T) e`, `!e`, `-e` |
| moltiplicativi | `*`, `/`, `%` |
| additivi | `+`, `-` |
| relazionali | `<`, `<=`, `>=`, `>` |
| uguaglianza | `==`, `!=`, `equals` |
| congiunzione | `&&`, `and` |
| disgiunzione | `\|\|`, `or` |
| condizionale | `c ? e1 : e2` (associativo a destra) |

L'operatore `+` è sovraccarico: fra numeri è l'addizione, mentre se almeno uno dei due operandi è una
`string` diventa concatenazione e converte l'altro operando in stringa.

### Regole lessicali

- **Identificatori**: `[a-zA-Z_][a-zA-Z_0-9]*`.
- **Commenti**: `// fino a fine riga` e `/* su più righe */`.
- **Escape** in stringhe e caratteri: `\b \t \n \f \r \" \' \\ \$`.
- **Interpolazione**: dentro un letterale stringa, `${espressione}` viene sostituito dal valore
  dell'espressione convertito in stringa. Per ottenere un dollaro letterale si scrive `\$`.

### Limitazioni sintattiche note

1. **Niente stringhe fra doppi apici dentro `${...}`.** Il lexer chiude il letterale al primo `"`
   incontrato, quindi `"${b ? "sì" : "no"}"` non è valido. Si aggira portando il ternario fuori
   dall'interpolazione (`"..." + (b ? "sì" : "no")`) oppure usando letterali `char`.
2. **Nessun `break` o `continue`.** Appartengono alle funzionalità avanzate di controllo del flusso,
   che non sono fra quelle scelte.
3. **Al massimo un `;` superfluo.** Un `;` in più è tollerato dopo un comando — sia prima di `end` sia a
   fine programma — ma due di seguito (`;;`) sono un errore di sintassi: non esiste un comando vuoto,
   il posto del comando che non fa niente è preso da `nop`.

---

## Semantica

### Impostazione generale

| Aspetto | Scelta |
|---|---|
| Controllo dei tipi | statico, completo, precedente all'esecuzione |
| Visibilità | a blocchi, catena di scope, **shadowing consentito** |
| Valutazione delle espressioni | *eager* per default, *lazy* su richiesta con la parola chiave `lazy` |
| Operatori logici | in corto circuito |
| Passaggio dei parametri | per valore per default, **per riferimento** con `ref`; gli array sono riferimenti |
| Errori di tipo | statici, non intercettabili, bloccano l'esecuzione |
| Errori a runtime | valori d'errore propaganti, intercettabili con `try`/`catch` |

### Gerarchia dei tipi e strategia di conversione

La relazione di sottotipaggio di CPS è volutamente minimale. L'unica coppia in relazione è

$$\texttt{int} \;\leftrightarrow\; \texttt{real}$$

e le conversioni fra i due tipi sono implicite; `char`, `bool`, `string` e gli array sono fra loro
incomparabili.

Gli **array sono invarianti**: `array real` non accetta un `array int`, benché i valori numerici siano
convertibili. La
covarianza renderebbe il sistema insicuro, perché gli array sono modificabili — attraverso un
riferimento di tipo `array real` si potrebbe scrivere un `real` dentro un array che in realtà contiene
`int`.

La **conversione implicita** (`int` ↔ `real`) avviene quando un
valore viene *riposto* da qualche parte. Cioè in una dichiarazione con inizializzatore, in un
assegnamento, nel legame di un parametro per valore e nel valore di ritorno. Concentrare la
conversione nei punti di scrittura evita di doverla replicare in ogni operatore.

La **conversione esplicita** — il cast `(T) e` — è ammessa solo all'interno del gruppo
`int`/`real`/`char`, cioè fra i tipi che condividono una rappresentazione numerica. `(int) 3.9` tronca,
`(int) 'A'` dà `65`, `(char) 66` dà `'B'`. Il cast di una `string` o di un `bool` è un errore statico;
per ottenere la rappresentazione testuale di un valore qualunque si usa `toStr`.

Nelle operazioni aritmetiche il tipo del risultato è il **join** dei due operandi: `real` se almeno uno
dei due è `real`, altrimenti `int`. Ne consegue che `5 / 2` vale `2`, mentre `(real) 5 / 2` vale `2.5`.

Un esempio di regola di tipo, per la dichiarazione con inizializzatore, in cui si legge sia il
controllo di sottotipaggio sia l'estensione dell'ambiente:

$$\textsc{T-Decl}\quad\frac{\Gamma \vdash e : \tau' \qquad \tau' \le \tau \qquad x \notin \mathrm{dom}(\Gamma_{\text{corrente}})}{\Gamma \vdash \tau\ x = e \;:\; \mathbf{com} \;\dashv\; \Gamma[x : \tau]}$$

La condizione $x \notin \mathrm{dom}(\Gamma_{\text{corrente}})$ guarda **solo il frame corrente**, non
tutta la catena: è precisamente questo che rende lecito lo shadowing e illecita la ridichiarazione.

### Semantica operazionale

Si adotta uno stile **big-step** (semantica naturale), che è quello che il visitor realizza
direttamente: ogni metodo `visit` porta a termine la valutazione del nodo e ne restituisce il
risultato, senza produrre configurazioni intermedie.

Notazione:

- $\sigma$ è l'ambiente, una catena di frame che associa identificatori a **celle**;
- $\sigma(x)$ è il contenuto della cella di $x$, $\sigma\langle x := v\rangle$ è l'ambiente in cui
  quella stessa cella contiene $v$, e $\sigma[x \mapsto \mathit{cella}]$ estende il frame corrente;
- $\mathrm{push}(\sigma)$ e $\mathrm{pop}(\sigma)$ entrano ed escono da un blocco;
- $\iota_\tau(v)$ è la conversione implicita in scrittura verso il tipo $\tau$;
- $\langle c,\sigma\rangle \Downarrow \sigma'$ per i comandi, $\langle e,\sigma\rangle \Downarrow \langle v,\sigma'\rangle$
  per le espressioni — che in CPS possono avere effetti collaterali, a causa di `++`, delle chiamate
  e del forzamento delle celle pigre.

**Sequenza e assegnamento.**

$$\textsc{Seq}\quad\frac{\langle c_1,\sigma\rangle \Downarrow \sigma' \qquad \langle c_2,\sigma'\rangle \Downarrow \sigma''}{\langle c_1 ; c_2,\ \sigma\rangle \Downarrow \sigma''}$$

$$\textsc{Assign}\quad\frac{\langle e,\sigma\rangle \Downarrow \langle v,\sigma'\rangle \qquad \tau = \mathrm{tipo}(x)}{\langle x = e,\ \sigma\rangle \Downarrow \sigma'\langle x := \iota_\tau(v)\rangle}$$

**Blocchi.** È qui che vive la visibilità riservata: il frame aperto all'ingresso viene scartato
all'uscita, quindi le dichiarazioni interne non sopravvivono al blocco, mentre le modifiche alle
celle esterne sì — perché quelle celle appartengono a frame che non vengono scartati.

$$\textsc{Block}\quad\frac{\langle c,\ \mathrm{push}(\sigma)\rangle \Downarrow \sigma'}{\langle \texttt{:}\ c\ \texttt{end},\ \sigma\rangle \Downarrow \mathrm{pop}(\sigma')}$$

**Iterazione.**

$$\textsc{While}_{\bot}\quad\frac{\langle e,\sigma\rangle \Downarrow \langle \mathbf{false},\sigma'\rangle}{\langle \texttt{while}\,(e)\ \texttt{do:}\ c\ \texttt{end},\ \sigma\rangle \Downarrow \sigma'}$$

$$\textsc{While}_{\top}\quad\frac{\langle e,\sigma\rangle \Downarrow \langle \mathbf{true},\sigma'\rangle \qquad \langle \texttt{:}\ c\ \texttt{end},\sigma'\rangle \Downarrow \sigma'' \qquad \langle \texttt{while}\,(e)\ \texttt{do:}\ c\ \texttt{end},\sigma''\rangle \Downarrow \sigma'''}{\langle \texttt{while}\,(e)\ \texttt{do:}\ c\ \texttt{end},\ \sigma\rangle \Downarrow \sigma'''}$$

**Corto circuito.** La congiunzione ha due regole, e in una di esse $e_2$ semplicemente non compare
fra le premesse: è questo che significa non valutarla.

$$\textsc{And}_{\bot}\quad\frac{\langle e_1,\sigma\rangle \Downarrow \langle \mathbf{false},\sigma'\rangle}{\langle e_1 \;\texttt{\&\&}\; e_2,\ \sigma\rangle \Downarrow \langle \mathbf{false},\sigma'\rangle}$$

$$\textsc{And}_{\top}\quad\frac{\langle e_1,\sigma\rangle \Downarrow \langle \mathbf{true},\sigma'\rangle \qquad \langle e_2,\sigma'\rangle \Downarrow \langle v,\sigma''\rangle}{\langle e_1 \;\texttt{\&\&}\; e_2,\ \sigma\rangle \Downarrow \langle v,\sigma''\rangle}$$

**Crementi.** La differenza fra forma prefissa e postfissa sta tutta in quale dei due valori compare
nella conclusione.

$$\textsc{PostInc}\quad\frac{\sigma(x) = v}{\langle x\texttt{++},\ \sigma\rangle \Downarrow \langle v,\ \sigma\langle x := v+1\rangle\rangle} \qquad\quad \textsc{PreInc}\quad\frac{\sigma(x) = v}{\langle \texttt{++}x,\ \sigma\rangle \Downarrow \langle v+1,\ \sigma\langle x := v+1\rangle\rangle}$$

**Chiamata di funzione.** Data la dichiarazione $\tau_0\; f(\pi_1\,\tau_1\,x_1, \dots, \pi_n\,\tau_n\,x_n)\,\{c\}$,
dove ogni $\pi_i$ è vuoto oppure `ref`:

$$\textsc{Call}\quad\frac{\langle e_i,\sigma_{i-1}\rangle \Downarrow \langle v_i,\sigma_i\rangle \quad (i = 1..n) \qquad \varphi = \big[\,x_i \mapsto \mathrm{bind}(\pi_i,\, e_i,\, v_i,\, \sigma_n)\,\big]_{i=1..n} \qquad \langle c,\ \varphi\rangle \Downarrow \mathbf{ret}(v)}{\langle f(e_1,\dots,e_n),\ \sigma_0\rangle \Downarrow \langle \iota_{\tau_0}(v),\ \sigma_n\rangle}$$

$$\mathrm{bind}(\pi, e, v, \sigma) \;=\; \begin{cases} \mathrm{cella}(\tau,\, \iota_\tau(v)) & \pi \text{ vuoto (per valore): cella nuova} \\[4pt] \sigma\text{-cella di } e & \pi = \texttt{ref} \text{ (per riferimento): la stessa cella} \end{cases}$$

Due dettagli si leggono direttamente dalla regola. Gli argomenti sono valutati **nell'ambiente del
chiamante** $\sigma$, in ordine da sinistra a destra. Il corpo invece è valutato in $\varphi$, che è
un ambiente **radice**, costruito da zero e privo di collegamento con $\sigma$: da qui la chiusura
delle funzioni rispetto allo stato globale.

Il `return` è modellato come esito $\mathbf{ret}(v)$ che attraversa i comandi annidati.

**Errori a tempo d'esecuzione.** Un errore è un esito $\mathbf{err}(m)$ che si propaga verso l'alto
attraverso ogni costrutto, finché non incontra un `try`.

$$\textsc{DivZero}\quad\frac{\langle e_1,\sigma\rangle \Downarrow \langle v_1,\sigma'\rangle \qquad \langle e_2,\sigma'\rangle \Downarrow \langle 0,\sigma''\rangle}{\langle e_1 / e_2,\ \sigma\rangle \Downarrow \mathbf{err}(\texttt{"divisione per zero"})}$$

$$\textsc{Try}_{ok}\quad\frac{\langle \texttt{:}\ c_1\ \texttt{catch}\,(x):\ c_2\ \texttt{end},\sigma\rangle \Downarrow \sigma'}{\langle \texttt{try:}\ c_1\ \texttt{catch}\,(x):\ c_2\ \texttt{end},\ \sigma\rangle \Downarrow \sigma'}$$

$$\textsc{Try}_{err}\quad\frac{\langle c_1,\sigma\rangle \Downarrow \mathbf{err}(m) \qquad \langle c_2,\ \mathrm{push}(\sigma)[\,x \mapsto \mathrm{cella}(\texttt{string},\, m)\,]\rangle \Downarrow \sigma'}{\langle \texttt{try:}\ c_1\ \texttt{catch}\,(x):\ c_2\ \texttt{end},\ \sigma\rangle \Downarrow \mathrm{pop}(\sigma')}$$

Si noti che nella seconda regola l'ambiente da cui riparte il gestore è $\sigma$, quello di *prima*
del blocco protetto: le dichiarazioni fatte nel `try` prima dell'errore non sono visibili al `catch`.

**Valutazione pigra.** Sono le tre regole che definiscono la funzionalità. Un valore sospeso si scrive
$\mathrm{susp}(e,\rho)$, dove $\rho$ è l'ambiente catturato, e $\sigma\!\restriction_{V}$ è
l'istantanea di $\sigma$ ristretta ai nomi in $V$.

$$\textsc{LazyDecl}\quad\frac{\rho \;=\; \sigma\!\restriction_{\mathrm{fv}(e)}}{\langle \texttt{lazy}\ \tau\ x = e,\ \sigma\rangle \Downarrow \sigma\big[\,x \mapsto \mathrm{cella}(\tau,\ \mathrm{susp}(e,\rho))\,\big]}$$

$$\textsc{Var}\quad\frac{\sigma(x) = v \qquad v \neq \mathrm{susp}(\_,\_)}{\langle x,\ \sigma\rangle \Downarrow \langle v,\ \sigma\rangle} \qquad\quad \textsc{Force}\quad\frac{\sigma(x) = \mathrm{susp}(e,\rho) \qquad \langle e,\ \rho\rangle \Downarrow \langle v,\ \rho'\rangle}{\langle x,\ \sigma\rangle \Downarrow \langle v,\ \sigma\langle x := v\rangle\rangle}$$

Tre conseguenze, tutte visibili nelle regole:

1. **La cattura è per valore.** In `LazyDecl` l'istantanea $\rho$ è presa al momento della
   dichiarazione, quindi in `y = 4; lazy int x = y + 3; y = 0; print x` la stampa dà `7`: la modifica
   successiva di `y` non tocca $\rho$.
2. **Il thunk è autocontenuto.** Poiché in `Force` l'espressione è valutata in $\rho$ e non in
   $\sigma$, un valore sospeso può sopravvivere allo scope in cui è nato senza riferirsi a variabili
   che nel frattempo sono sparite.
3. **La memoizzazione è nella conclusione di `Force`**: l'ambiente restituito contiene $v$ al posto
   della sospensione, quindi il secondo uso di $x$ ricade su `Var` e non ricalcola nulla.

L'istantanea è **superficiale**: se una cella catturata è a sua volta sospesa, se ne copia la
sospensione anziché forzarla. La pigrizia si propaga quindi lungo le catene di dipendenze, e
`lazy int a = costoso(); lazy int b = a + 1;` non calcola nulla finché non si usa `b`.

Per gli array l'istantanea fotografa il **riferimento**, perché per un array il valore *è* un
riferimento. È coerente con il resto del linguaggio — anche `b = a` fra array crea un alias — ma è
l'unico punto in cui una modifica successiva alla cattura risulta osservabile al forzamento.

---

## Implementazione

L'interprete è scritto in Java 25 e usa il pattern **visitor** generato da ANTLR (`-visitor`, senza
listener). L'elaborazione attraversa quattro fasi nettamente separate, orchestrate da `MainCPS`:

```
sorgente  →  lexer + parser  →  raccolta firme  →  type system  →  interprete
             CPS.g4            FunctionTable      CPSTypeSystem   CPSInterpreter
```

### I due visitor

`CPSTypeSystem extends CPSBaseVisitor<Type>` e `CPSInterpreter extends CPSBaseVisitor<Value>`
percorrono lo stesso albero con lo stesso schema, restituendo l'uno un tipo e l'altro un valore. La
separazione paga due volte: l'interprete non ripete alcun controllo di tipo — può assumere che ogni
operando sia del tipo giusto e limitarsi a un cast — e un programma mal tipato non produce output
parziale prima di fallire.

Le classi `Type`/`ExpType`/`ComType`/`VoidType` e `Value`/`ExpValue<T>`/`NumValue<T>` mantengono le
due gerarchie parallele, con `ArrayType` e `ArrayValue` a chiudere il quadro.

### La catena di scope

Il cuore dell'implementazione è `Scope<T>`, un frame di dichiarazioni più un puntatore al frame che
lo racchiude. È parametrica perché serve a entrambi i visitor: il type system la istanzia con
`ExpType`, l'interprete con `Cell`.

La distinzione fondamentale è fra le due operazioni di ricerca:

- `lookup` **risale la catena** e si ferma al primo frame che contiene il nome — ed è questo che
  realizza lo shadowing;
- `declaredHere` **guarda solo il frame corrente** — ed è questo che rende la ridichiarazione un
  errore solo all'interno dello stesso blocco.

Poiché i due visitor entrano ed escono dai blocchi negli stessi punti, un nome risolto dal type system
denota a runtime esattamente la stessa dichiarazione.

### `Cell`, un'astrazione per tre problemi

`Cell` è un contenitore per un valore modificabile. Introdurre questo livello di indirezione risolve
in un colpo solo tre requisiti che sembravano indipendenti:

- il **passaggio per riferimento** consiste semplicemente nel condividere la cella con il chiamante;
- un **array** è un vettore di celle, e questo rende assegnabile il singolo elemento (`a[i] = v`,
  `a[i]++`);
- la **valutazione pigra** mette nella cella un `Thunk` invece di un valore, e `Cell.get()` lo forza
  in modo trasparente per chi legge.

### Difficoltà tecniche incontrate

**Ricorsione mutua.** Se le firme delle funzioni si raccogliessero mentre si controllano i corpi, la
prima di due funzioni che si chiamano a vicenda non troverebbe la seconda. La soluzione è la passata
preliminare di `FunctionTable.collect`, che popola la tabella delle firme prima che il type system
inizi. Il programma `smoke` con `even`/`odd` è il caso di prova.

**Uscita anticipata con il pattern visitor.** Un visitor restituisce un valore per ogni nodo e non ha
modo di interrompere una visita a metà, ma `return` deve saltare fuori da blocchi e cicli annidati.
La soluzione idiomatica è un'eccezione di controllo, `ReturnSignal`, costruita con stack trace
disabilitato perché non è un errore ma un salto. Lo stesso meccanismo, con `CPSRuntimeError`,
implementa la propagazione degli errori fino al `try` più vicino.

**Allineamento dell'ambiente in presenza di salti.** Con `return` ed errori che attraversano i
blocchi, un'uscita anticipata rischia di lasciare `scope` puntato a un frame interno. Ogni ingresso in
un blocco è quindi racchiuso in un `try/finally` che ripristina l'ambiente qualunque cosa accada
dentro — è il motivo per cui, dopo un errore intercettato, l'esecuzione riprende nell'ambiente giusto.

**Interpolazione senza lexer mode.** La soluzione idiomatica in ANTLR sarebbe un *mode* dedicato,
attivato da `${` e chiuso dalla graffa corrispondente. Gestire correttamente le graffe annidate
richiederebbe un contatore di profondità tramite **azioni lessicali**, cioè codice Java scritto dentro
il `.g4`, che legherebbe la grammatica al linguaggio di implementazione.

Si è preferito tenere la grammatica pulita: il lexer emette un unico token `STRING` e la scomposizione
avviene in `StringInterpolation`, che scandisce il testo tenendo conto degli escape, individua la
graffa di chiusura contando l'annidamento, e ricompila ogni frammento con un parser CPS usa e getta.
Il risultato è messo in cache sul testo del token, altrimenti una stringa interpolata dentro un ciclo
verrebbe riparsata a ogni iterazione. I due costi di questa scelta sono dichiarati: le posizioni degli
errori dentro un frammento sono relative al frammento, e non si possono annidare stringhe fra doppi
apici dentro `${...}`.

**Le espressioni nascoste nei token.** Conseguenza del punto precedente: le espressioni interpolate
non sono nodi dell'albero, quindi ogni visita che percorre un'espressione deve espanderle
esplicitamente. Vale per il type system — altrimenti `"${nonDichiarata}"` passerebbe il controllo
statico — e per `FreeVariables`, altrimenti la `x` di `lazy string s = "${x}"` sfuggirebbe alla
cattura.

**Cattura per valore senza forzare le catene.** La prima versione della cattura fotografava i
*valori* delle variabili libere, ma questo forzava sul posto ogni cella pigra fra di esse, rendendo
`lazy int b = a + 1;` un forzamento immediato di `a`. La soluzione è `Cell.snapshot()`: la copia è
indipendente dall'originale rispetto agli assegnamenti futuri, ma se il contenuto è ancora sospeso le
due celle **condividono lo stesso `Thunk`** — così la pigrizia si propaga e la memoizzazione resta
unica.

**Aritmetica intera davvero intera.** Calcolare tutto in `double` e riconvertire, come è naturale
fare in una prima versione, perde precisione sugli interi grandi e soprattutto nasconde la divisione
per zero dietro a un `Infinity` silenzioso. In CPS, se entrambi gli operandi sono `IntValue` il conto
si fa fra `int`, e la divisione per zero è un controllo esplicito.

**Cicli e ricorsione senza esaurire la pila.** `visitWhile` è un ciclo Java, non una chiamata
ricorsiva: la profondità della pila non deve dipendere dal numero di iterazioni. Per la ricorsione
*del programma interpretato*, che la pila la consuma per forza, un contatore `MAX_CALL_DEPTH` la
interrompe a 1000 chiamate annidate trasformandola in un errore CPS catturabile, invece di lasciarla
degenerare in uno `StackOverflowError` della JVM, che non lo sarebbe.

**Errori di sintassi silenziosi.** Il listener di default di ANTLR stampa l'errore e prosegue con il
recupero, consegnando al type system un albero rattoppato. `CPSErrorListener` lo sostituisce su lexer
e parser e interrompe subito l'analisi.

### Mappa dei sorgenti

| File | Ruolo |
|---|---|
| `MainCPS.java` | orchestrazione delle quattro fasi, codici di uscita |
| `CPSTypeSystem.java` | controllo statico dei tipi e delle dichiarazioni |
| `CPSInterpreter.java` | valutazione |
| `env/Scope.java` | catena di scope, parametrica |
| `env/Cell.java` | cella modificabile: riferimenti, elementi di array, sospensioni |
| `env/FunctionTable.java` | passata preliminare e firme delle funzioni |
| `type/` | gerarchia dei tipi, sottotipaggio, conversioni, valori di default |
| `value/` | gerarchia dei valori |
| `lazyeval/Thunk.java` | computazione sospesa con memoizzazione |
| `lazyeval/FreeVariables.java` | variabili libere di un'espressione |
| `interpolation/StringInterpolation.java` | scomposizione e ricompilazione dei letterali stringa |
| `error/` | errori statici, errori runtime, segnale di `return`, listener di sintassi |

---

## Programmi di Test

I nove programmi in `programs/` si eseguono con

```
java -jar target/CPS-1.0-SNAPSHOT-jar-with-dependencies.jar programs/<nome>.cps
```

### `hello.cps`

Il programma minimo.

```
Hello, CPS!
```

### `fattoriale.cps`

Fattoriale ricorsivo e iterativo a confronto. Mostra funzioni con valore di ritorno, ricorsione,
ciclo, assegnamento composto e stampe composte con `+`.

```
0! = 1  (iterativo: 1)
1! = 1  (iterativo: 1)
2! = 2  (iterativo: 2)
3! = 6  (iterativo: 6)
4! = 24  (iterativo: 24)
5! = 120  (iterativo: 120)
6! = 720  (iterativo: 720)
7! = 5040  (iterativo: 5040)
8! = 40320  (iterativo: 40320)
9! = 362880  (iterativo: 362880)
10! = 3628800  (iterativo: 3628800)
```

### `fibonacci.cps`

Ricorsione pura e ricorsione con tabella di memoizzazione. La tabella è un `array int` passato alle
chiamate annidate: essendo un riferimento, quella riempita in profondità è la stessa che vede il
chiamante — verificato dall'ultima riga, che la interroga dopo il ritorno.

```
fib(0) = 0
fib(1) = 1
fib(2) = 1
fib(3) = 2
fib(4) = 3
fib(5) = 5
fib(6) = 8
fib(7) = 13
fib(8) = 21
fib(9) = 34
fib(10) = 55
fib(11) = 89
fib(12) = 144
fib(13) = 233
fib(14) = 377
fib(15) = 610
fib(40) con memoizzazione = 102334155
la tabella e' stata riempita dalle chiamate annidate: fib(20) = 6765
```

### `primi.cps`

Crivello di Eratostene: array di `bool` con dimensione decisa a runtime, cicli annidati, funzione che
restituisce un array, accumulo su stringa con `+=`.

```
ci sono 25 numeri primi fino a 100:
2 3 5 7 11 13 17 19 23 29 31 37 41 43 47 53 59 61 67 71 73 79 83 89 97 
```

### `bubblesort.cps`

Ordinamento sul posto. Mette a confronto le due modalità di passaggio: `scambia` riceve due `int` con
`ref`, `bubbleSort` riceve un array, che è già un riferimento. Le ultime righe sono la controprova
che senza `ref` la funzione lavorerebbe su una copia — e infatti `scambia(x, y)` con `ref` scambia
davvero.

```
prima: [5, 2, 9, 1, 5, 6, 0, 3]
dopo:  [0, 1, 2, 3, 5, 5, 6, 9]
x=1 y=2
dopo scambia(x, y): x=2 y=1
```

### `matrice.cps`

Prodotto di matrici, con array multidimensionali sia in forma letterale sia allocati con
`array array int c[righe][colonne]`.

```
A =
  [1, 2, 3]
  [4, 5, 6]
B =
  [7, 8]
  [9, 10]
  [11, 12]
A x B =
  [58, 64]
  [139, 154]
```

### `lazy.cps`

Le cinque proprietà della valutazione pigra, una per sezione: cattura per valore (`x` vale `7` benché
`y` sia stata azzerata), espressione mai forzata (`1 / 0` dichiarato senza errore), calcolo al primo
uso e una volta sola (il messaggio di calcolo compare una volta per due usi), propagazione della
pigrizia lungo una catena, e cattura per valore anche nelle espressioni lazy di tipo `string`.

```
y valeva 4 alla dichiarazione e ora vale 0, ma x vale 7

dichiarato 'lazy int mai = 1 / 0' senza alcun errore

quadrato e' dichiarata ma non ancora calcolata
  ...calcolo costoso su 7
primo uso:   49
secondo uso: 49
il messaggio di calcolo e' comparso una sola volta

a e b dichiarate, ancora niente di calcolato
  ...calcolo costoso su 3
forzo b: 10

etichetta valeva 1
```

### `zucchero.cps`

Incrementi, assegnamenti composti e operatore ternario, seguiti da esempi di stampa con
concatenazione esplicita.

```
i vale 5
i++ vale 5, e adesso i vale 6
++i vale 7, e adesso i vale 7
i-- vale 7, e adesso i vale 6
--i vale 5, e adesso i vale 5
array dopo i crementi: [11, 21]

n += 5  ->  15
n -= 3  ->  12
n *= 2  ->  24
n /= 4  ->  6
n %= 4  ->  2
su real la divisione non tronca:  d /= 4  ->  2.5
anche la concatenazione:  s += "PS"  ->  CPS

voto 27: non ottimo
il massimo fra 7 e 12 e' 12
massimo di [3, 8, 1, 9, 4] = 9

Totale: 37.5 euro per 3 pezzi
Confronto: 3 pezzi sono M
Espressione annidata: 1
```

### `errori.cps`

Gestione programmatica degli errori: errori sollevati dal linguaggio e dal programma, `try` annidati
con rilancio, e ripresa dell'esecuzione dopo la gestione.

```
1) catturato: divisione per zero
2) catturato: indice 10 fuori dai limiti dell'array (lunghezza 3)
3) catturato: dividi: il divisore non puo' essere zero
4) catturato: dimensione di array negativa: -1
5) catturato: ricorsione troppo profonda in 'infinita' (oltre 1000 chiamate annidate)
6) gestore interno: guasto nel blocco interno
6) gestore esterno: rilanciato verso l'esterno
7) il programma prosegue e termina regolarmente
```

### Casi di errore

I programmi qui sopra terminano tutti con codice `0`. Gli errori che *devono* essere rifiutati sono
stati verificati separatamente; una selezione, con il messaggio prodotto:

| Programma | Esito |
|---|---|
| `print pippo` | *statico* — variabile 'pippo' non dichiarata @1:6 |
| `int x = 1; int x = 2` | *statico* — variabile 'x' gia' dichiarata in questo blocco @1:11 |
| `bool x = 3.5` | *statico* — 'x' e' di tipo bool e non puo' essere inizializzata con real @1:0 |
| `if (1): nop end` | *statico* — atteso bool, trovato int @1:4 |
| `function f(int a) -> int: if (a > 0): return 1; end end` | *statico* — la funzione 'f' di tipo int puo' terminare senza return |
| `function f(ref int a) -> void: end f(3)` | *statico* — parametro per riferimento: serve una variabile o un elemento di array |
| `array int a = [1]; array real d = a` | *statico* — 'd' e' di tipo array real e non puo' essere inizializzata con array int |
| `print (int) "ciao"` | *statico* — non esiste conversione da string a int @1:6 |
| `int x = ;` | *sintassi* — mismatched input ';' @1:8 |
| `print 1 / 0` | *runtime non gestito* — divisione per zero |

Gli errori statici sono segnalati con riga e colonna e impediscono l'esecuzione dell'intero programma;
quelli a runtime, se non intercettati, terminano l'esecuzione con codice `5`.
