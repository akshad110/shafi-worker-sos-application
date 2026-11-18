package com.example.shafi.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Jobs this signed-in session has already applied for, so the button does not wait on a second request. */
public final class AppliedJobs {
    private static final Set<String> ids = Collections.synchronizedSet(new HashSet<>());

    private AppliedJobs() {
    }

    public static void mark(String jobId) {
        if (jobId != null && !jobId.isEmpty()) ids.add(jobId);
    }

    public static boolean contains(String jobId) {
        return jobId != null && ids.contains(jobId);
    }
}
