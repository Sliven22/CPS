// Prodotto di matrici.
// Mostra gli array multidimensionali, che in CPS non sono un caso speciale: 'array array int' e'
// semplicemente un array i cui elementi sono array di int.

function prodotto(array array int a, array array int b) -> array array int:
    int righe = len(a);
    int interne = len(b);
    int colonne = len(b[0]);

    array array int c[righe][colonne];

    int i = 0;
    while (i < righe) do:
        int j = 0;
        while (j < colonne) do:
            int somma = 0;
            int k = 0;
            while (k < interne) do:
                somma += a[i][k] * b[k][j];
                k++;
            end
            c[i][j] = somma;
            j++;
        end
        i++;
    end

    return c;
end

function stampa(string nome, array array int m) -> void:
    print nome + " =";
    int i = 0;
    while (i < len(m)) do:
        print "  " + m[i];
        i++;
    end
end

array array int a = [[1, 2, 3], [4, 5, 6]];
array array int b = [[7, 8], [9, 10], [11, 12]];

stampa("A", a);
stampa("B", b);
stampa("A x B", prodotto(a, b))
