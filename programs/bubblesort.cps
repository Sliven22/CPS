// Ordinamento a bolle sul posto.
// Mostra le due modalita' di passaggio dei parametri che convivono in CPS:
//   - 'scambia' riceve due int per RIFERIMENTO, con il modificatore 'ref';
//   - 'bubbleSort' riceve un array, che e' gia' un riferimento per natura.
// In entrambi i casi le modifiche fatte dentro la funzione si vedono fuori.

function scambia(ref int a, ref int b) -> void:
    int temporaneo = a;
    a = b;
    b = temporaneo;
end

function bubbleSort(array int v) -> void:
    int n = len(v);
    int i = 0;
    while (i < n - 1) do:
        int j = 0;
        while (j < n - 1 - i) do:
            if (v[j] > v[j + 1]):
                scambia(v[j], v[j + 1]);
            end
            j++;
        end
        i++;
    end
end

array int v = [5, 2, 9, 1, 5, 6, 0, 3];

print "prima: " + v;
bubbleSort(v);
print "dopo:  " + v;

// Controprova sul passaggio per valore: senza 'ref' la funzione lavora su una copia.
int x = 1;
int y = 2;
print "x=" + x + " y=" + y;
scambia(x, y);
print "dopo scambia(x, y): x=" + x + " y=" + y;
