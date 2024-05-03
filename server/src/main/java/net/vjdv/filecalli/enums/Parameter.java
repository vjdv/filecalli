package net.vjdv.filecalli.enums;

public enum Parameter {

    DATA_VERSION(1);

    private final int value;

    Parameter(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

}
