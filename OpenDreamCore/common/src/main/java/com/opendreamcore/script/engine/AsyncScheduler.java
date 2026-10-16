package com.opendreamcore.script.engine;

/**
 * Host-owned, non-blocking scheduler used by the DreamLang continuation
 * interpreter.  Implementations MUST invoke continuations on the host's
 * script/main thread and MUST NOT run an interpreter worker thread.
 */
@FunctionalInterface
public interface AsyncScheduler {
    /** Schedule a continuation after at least {@code delayMs} milliseconds. */
    void schedule(long delayMs, Runnable continuation);
}
