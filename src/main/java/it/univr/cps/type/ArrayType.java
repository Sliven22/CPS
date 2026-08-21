package it.univr.cps.type;

import java.util.Objects;

/**
 * Tipo array, definito per ricorsione sul tipo degli elementi: {@code array array int} e' semplicemente
 * {@code ArrayType(ArrayType(INT))}. Il supporto agli array multidimensionali arriva quindi
 * gratuitamente, senza casi speciali.
 * <p>
 * Gli array sono <em>invarianti</em>: {@code array real} non accetta un {@code array int}, benché
 * {@code real} accetti {@code int}. Ammettere la covarianza renderebbe il type system insicuro,
 * perché gli array sono modificabili: attraverso un riferimento {@code array real} si potrebbe
 * scrivere un {@code real} dentro un array che in realtà contiene {@code int}.
 */
public final class ArrayType implements ExpType {

    private final ExpType elementType;

    public ArrayType(ExpType elementType) {
        this.elementType = elementType;
    }

    public ExpType getElementType() {
        return elementType;
    }

    @Override
    public String getName() {
        return "array " + elementType.getName();
    }

    @Override
    public boolean accepts(Type source) {
        return equals(source); // invarianza
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArrayType other)) return false;
        return elementType.equals(other.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ArrayType.class, elementType);
    }

    @Override
    public String toString() {
        return getName();
    }
}
