// Fibonacci in due modi: ricorsione pura e ricorsione con tabella di memoizzazione.
// Mostra come un array passato a una funzione sia condiviso, non copiato: la tabella riempita
// dalle chiamate annidate e' la stessa che vede il chiamante.

function fibIngenuo(int n) -> int:
    if (n < 2):
        return n;
    end
    return fibIngenuo(n - 1) + fibIngenuo(n - 2);
end

// La tabella viene modificata sul posto: gli array sono riferimenti.
function fibMemo(int n, array int tabella) -> int:
    if (n < 2):
        return n;
    end
    if (tabella[n] != 0):
        return tabella[n];
    end

    tabella[n] = fibMemo(n - 1, tabella) + fibMemo(n - 2, tabella);
    return tabella[n];
end

int i = 0;
while (i <= 15) do:
    print "fib(" + i + ") = " + fibIngenuo(i);
    i++;
end

array int tabella[41];
print "fib(40) con memoizzazione = " + fibMemo(40, tabella);
print "la tabella e' stata riempita dalle chiamate annidate: fib(20) = " + tabella[20];
