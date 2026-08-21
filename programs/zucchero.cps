// Zucchero sintattico e composizione delle stampe con concatenazione esplicita.

// ------------------------------------------------ 1. incremento e decremento, pre e post
// Semantica di Java: la forma postfissa restituisce il valore PRIMA della modifica,
// quella prefissa il valore DOPO. Le parti interpolate sono valutate da sinistra a destra.

int i = 5;
print "i vale " + i;
print "i++ vale " + i++ + ", e adesso i vale " + i;
print "++i vale " + ++i + ", e adesso i vale " + i;
print "i-- vale " + i-- + ", e adesso i vale " + i;
print "--i vale " + --i + ", e adesso i vale " + i;

// funzionano anche sugli elementi di un array
array int v = [10, 20];
v[0]++;
++v[1];
print "array dopo i crementi: " + v;
print "";

// ------------------------------------------------ 2. assegnamenti composti
// 'x op= e' e' definito come 'x = x op e', regole di conversione comprese.

int n = 10;
n += 5;  print "n += 5  ->  " + n;
n -= 3;  print "n -= 3  ->  " + n;
n *= 2;  print "n *= 2  ->  " + n;
n /= 4;  print "n /= 4  ->  " + n;
n %= 4;  print "n %= 4  ->  " + n;

real d = 10.0;
d /= 4;
print "su real la divisione non tronca:  d /= 4  ->  " + d;

string s = "C";
s += "PS";
print "anche la concatenazione:  s += \"PS\"  ->  " + s;
print "";

// ------------------------------------------------ 3. operatore ternario
// Associativo a destra, quindi si incatena senza parentesi.

int voto = 27;
print "voto " + voto + ": " + (voto >= 28 ? "ottimo" : "non ottimo");

int a = 7;
int b = 12;
print "il massimo fra " + a + " e " + b + " e' " + (a > b ? a : b);

// il ternario e' un'espressione, quindi si annida ovunque
array int numeri = [3, 8, 1, 9, 4];
int massimo = numeri[0];
int k = 1;
while (k < len(numeri)) do:
    massimo = numeri[k] > massimo ? numeri[k] : massimo;
    k++;
end
print "massimo di " + numeri + " = " + massimo;
print "";

// ------------------------------------------------ 4. stampe con concatenazione
// Le parti dinamiche si concatenano esplicitamente alla stringa, come nella sintassi CPS canonica.

real prezzo = 12.5;
int quantita = 3;
print "Totale: " + prezzo * (real) quantita + " euro per " + quantita + " pezzi";
print "Confronto: " + quantita + " pezzi sono " + (quantita > 2 ? 'M' : 'P');
print "Espressione annidata: " + numeri[quantita - 1];
