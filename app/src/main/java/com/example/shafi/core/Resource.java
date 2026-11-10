package com.example.shafi.core;

public class Resource<T> {
    public enum State { LOADING, SUCCESS, ERROR }

    public final State state;
    public final T data;
    public final String message;
    public final boolean offline;

    private Resource(State state, T data, String message, boolean offline) {
        this.state = state;
        this.data = data;
        this.message = message;
        this.offline = offline;
    }

    public static <T> Resource<T> loading() {
        return new Resource<>(State.LOADING, null, null, false);
    }

    public static <T> Resource<T> success(T data) {
        return new Resource<>(State.SUCCESS, data, null, false);
    }

    public static <T> Resource<T> offline(T data, String message) {
        return new Resource<>(State.SUCCESS, data, message, true);
    }

    public static <T> Resource<T> error(String message) {
        return new Resource<>(State.ERROR, null, message, false);
    }
}
