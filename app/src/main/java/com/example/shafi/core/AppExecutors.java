package com.example.shafi.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    public static final ExecutorService IO = Executors.newFixedThreadPool(4);

    private AppExecutors() {
    }
}
