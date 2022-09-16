package com.autoroute.telegram.db;

public enum State {

    CREATED(0),
    SENT_LOCATION(10),
    SENT_DISTANCE(20),
    //    GOT_FIRST_PART(30),
    GOT_ALL_ROUTES(40);

    private static final State[] VALUES = values();

    private final int index;

    State(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public static State byIndex(int index) {
        for (State value : VALUES) {
            if (value.index == index) {
                return value;
            }
        }
        throw new IllegalArgumentException("index is not correct: " + index);
    }
}
