// Fattoriale nelle due versioni, ricorsiva e iterativa.
// Mostra: funzioni con valore di ritorno, ricorsione, ciclo, assegnamento composto, interpolazione.

function fattorialeRicorsivo(int n) -> int:
    if (n <= 1):
        return 1;
    end
    return n * fattorialeRicorsivo(n - 1);
end

function fattorialeIterativo(int n) -> int:
    int risultato = 1;
    int i = 2;
    while (i <= n) do:
        risultato *= i;
        i++;
    end
    return risultato;
end

int n = 0;
while (n <= 10) do:
    print n + "! = " + fattorialeRicorsivo(n) + "  (iterativo: " + fattorialeIterativo(n) + ")";
    n++;
end
