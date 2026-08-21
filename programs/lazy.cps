// Valutazione pigra.
// Quattro proprieta' della semantica di 'lazy', una per sezione.

function costoso(int n) -> int:
    print "  ...calcolo costoso su " + n;
    return n * n;
end

// 1. La cattura e' PER VALORE: il thunk fotografa le variabili libere alla dichiarazione.
//    Quello che cambia dopo non lo riguarda piu'.
int y = 4;
lazy int x = y + 3;
y = 0;
print "y valeva 4 alla dichiarazione e ora vale " + y + ", ma x vale " + x;
print "";

// 2. Un'espressione pigra mai usata non viene MAI calcolata: qui non c'e' nessuna divisione per zero.
lazy int mai = 1 / 0;
print "dichiarato 'lazy int mai = 1 / 0' senza alcun errore";
print "";

// 3. Il calcolo avviene al primo uso, e una volta sola: il risultato viene memoizzato.
lazy int quadrato = costoso(7);
print "quadrato e' dichiarata ma non ancora calcolata";
print "primo uso:   " + quadrato;
print "secondo uso: " + quadrato;
print "il messaggio di calcolo e' comparso una sola volta";
print "";

// 4. La pigrizia si propaga: catturare una variabile ancora sospesa non la forza,
//    le due celle condividono lo stesso thunk.
lazy int a = costoso(3);
lazy int b = a + 1;
print "a e b dichiarate, ancora niente di calcolato";
print "forzo b: " + b;
print "";

// 5. La cattura per valore vale anche per le espressioni lazy di tipo string.
int etichetta = 1;
lazy string messaggio = "etichetta valeva " + etichetta;
etichetta = 99;
print messaggio;
