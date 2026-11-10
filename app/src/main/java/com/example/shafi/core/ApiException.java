package com.example.shafi.core;

public class ApiException extends Exception {
    public final String code;
    public final int status;

    public ApiException(String message, String code, int status) {
        super(message);
        this.code = code == null ? "" : code;
        this.status = status;
    }
}
