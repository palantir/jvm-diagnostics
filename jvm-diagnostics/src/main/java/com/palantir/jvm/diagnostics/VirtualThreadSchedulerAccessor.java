package com.palantir.jvm.diagnostics;

public interface VirtualThreadSchedulerAccessor {
    int getMountedVirtualThreadCount();

    int getParallelism();

    int getPoolSize();

    long getQueuedVirtualThreadCount();

    void setParallelism(int size);
}
