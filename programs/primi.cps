// Crivello di Eratostene.
// Mostra: array di bool a dimensione decisa a runtime, cicli annidati, funzione che restituisce
// un array, accumulo su stringa con assegnamento composto.

function crivello(int limite) -> array bool:
    array bool composto[limite + 1];

    int p = 2;
    while (p * p <= limite) do:
        if (!composto[p]):
            int multiplo = p * p;
            while (multiplo <= limite) do:
                composto[multiplo] = true;
                multiplo += p;
            end
        end
        p++;
    end

    return composto;
end

int limite = 100;
array bool composto = crivello(limite);

string elenco = "";
int quanti = 0;
int n = 2;
while (n <= limite) do:
    if (!composto[n]):
        elenco += toStr(n) + " ";
        quanti++;
    end
    n++;
end

print "ci sono " + quanti + " numeri primi fino a " + limite + ":";
print elenco;
