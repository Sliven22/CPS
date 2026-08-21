// Gestione programmatica degli errori a tempo d'esecuzione.
// Gli errori sollevati dal linguaggio e quelli sollevati dal programma con 'throw' sono la stessa
// cosa e si intercettano allo stesso modo: 'try: ... catch (nome): ... end', dove 'nome' e' una
// variabile string visibile solo nel blocco di gestione e contiene il messaggio.

function dividi(real a, real b) -> real:
    if (b == 0.0):
        throw "dividi: il divisore non puo' essere zero";
    end
    return a / b;
end

function infinita(int n) -> int:
    return infinita(n + 1);
end

// 1. divisione per zero
try:
    int x = 10 / 0;
    print "questa riga non viene mai raggiunta";
catch (errore):
    print "1) catturato: " + errore;
end

// 2. indice fuori dai limiti
array int v = [1, 2, 3];
try:
    print v[10];
catch (errore):
    print "2) catturato: " + errore;
end

// 3. errore sollevato dal programma
try:
    print dividi(1.0, 0.0);
catch (errore):
    print "3) catturato: " + errore;
end

// 4. dimensione di array negativa
try:
    array int cattivo[0 - 1];
    print "mai";
catch (errore):
    print "4) catturato: " + errore;
end

// 5. ricorsione senza caso base: intercettata come errore del linguaggio,
//    non come crash della macchina virtuale
try:
    print infinita(0);
catch (errore):
    print "5) catturato: " + errore;
end

// 6. i try si annidano, e un gestore puo' rilanciare
try:
    try:
        throw "guasto nel blocco interno";
    catch (errore):
        print "6) gestore interno: " + errore;
        throw "rilanciato verso l'esterno";
    end
catch (errore):
    print "6) gestore esterno: " + errore;
end

// 7. dopo la gestione l'esecuzione riprende normalmente
print "7) il programma prosegue e termina regolarmente";
