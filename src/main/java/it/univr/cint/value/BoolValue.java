package it.univr.cint.value;

public final class BoolValue extends ExpValue<Boolean> {

    public static final BoolValue TRUE = new BoolValue(true);
    public static final BoolValue FALSE = new BoolValue(false);

    public BoolValue(boolean value) {
        super(value);
    }

    public static BoolValue of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public boolean isTrue() {
        return toValue();
    }
}
