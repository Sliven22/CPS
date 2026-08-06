package it.univr.cint.env;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ambiente a catena: un frame di dichiarazioni più un puntatore al frame che lo racchiude.
 * Sostituisce la mappa piatta usata nelle esercitazioni, che non regge ne' l'annidamento dei blocchi
 * ne' i frame di chiamata delle funzioni.
 * <p>
 * La risoluzione di un nome risale la catena e si ferma al primo frame che lo contiene: è proprio
 * questa risalita a realizzare lo <b>shadowing</b>, perche' una dichiarazione in un blocco interno
 * nasconde quella omonima piu' esterna senza cancellarla. La dichiarazione, invece, guarda solo il
 * frame corrente, cosi' ridichiarare lo stesso nome nello stesso blocco resta un errore.
 * <p>
 * La classe è parametrica perche' i due visitor la usano con contenuti diversi: il type system
 * associa ai nomi un {@code ExpType}, l'interprete una {@link Cell}.
 */
public final class Scope<T> {

    private final Map<String, T> frame = new LinkedHashMap<>();
    private final Scope<T> parent;

    /** Crea uno scope radice, senza genitore. */
    public Scope() {
        this(null);
    }

    private Scope(Scope<T> parent) {
        this.parent = parent;
    }

    /** Entra in un blocco annidato: nuovo frame vuoto, questo scope come genitore. */
    public Scope<T> push() {
        return new Scope<>(this);
    }

    /** Esce dal blocco corrente, scartandone le dichiarazioni. Null se questo è lo scope radice. */
    public Scope<T> pop() {
        return parent;
    }

    /** true se il nome è dichiarato proprio in questo frame, senza risalire la catena. */
    public boolean declaredHere(String id) {
        return frame.containsKey(id);
    }

    /** true se il nome è visibile da qui, in questo frame o in uno di quelli che lo racchiudono. */
    public boolean contains(String id) {
        return lookup(id) != null;
    }

    /** Risolve il nome risalendo la catena; null se non è visibile. */
    public T lookup(String id) {
        for (Scope<T> scope = this; scope != null; scope = scope.parent) {
            T found = scope.frame.get(id);
            if (found != null) return found;
        }
        return null;
    }

    /** Dichiara il nome nel frame corrente, eventualmente facendo ombra a un omonimo piu' esterno. */
    public void declare(String id, T content) {
        frame.put(id, content);
    }

    /**
     * Rimpiazza il contenuto associato al nome nel frame in cui e' dichiarato.
     * Serve al type system; l'interprete non ne ha bisogno, perche' modifica la {@link Cell} in loco.
     *
     * @return false se il nome non e' visibile da qui
     */
    public boolean replace(String id, T content) {
        for (Scope<T> scope = this; scope != null; scope = scope.parent) {
            if (scope.frame.containsKey(id)) {
                scope.frame.put(id, content);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{ ");
        for (Map.Entry<String, T> entry : frame.entrySet())
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(" ");
        sb.append("}");
        if (parent != null) sb.append(" -> ").append(parent);
        return sb.toString();
    }
}
