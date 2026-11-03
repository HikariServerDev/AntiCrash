package com.atsukimc.anticrash.integration.carpet;

import java.util.Arrays;

/**
 * Exact public Carpet factory layouts, verified against official release bytecode. Scalar: Carpet
 * 1.4.69 (1.18.2). Vector: 1.4.141 (1.20.6) through 26.3; callback: 1.4.118 (1.20.2). Unknown forks
 * must not receive guessed coordinates, rotation, or boolean arguments.
 */
public enum FakePlayerFactoryContract {
    SCALAR,
    VECTOR,
    VECTOR_WITH_FAILURE_HANDLER;

    public static FakePlayerFactoryContract resolve(
            Class<?>[] signature,
            Class<?> server,
            Class<?> vector,
            Class<?> dimension,
            Class<?> mode) {
        if (Arrays.equals(
                signature,
                new Class<?>[] {
                    String.class,
                    server,
                    double.class,
                    double.class,
                    double.class,
                    double.class,
                    double.class,
                    dimension,
                    mode,
                    boolean.class
                })) return SCALAR;
        if (Arrays.equals(
                signature,
                new Class<?>[] {
                    String.class,
                    server,
                    vector,
                    double.class,
                    double.class,
                    dimension,
                    mode,
                    boolean.class
                })) return VECTOR;
        if (Arrays.equals(
                signature,
                new Class<?>[] {
                    String.class,
                    server,
                    vector,
                    double.class,
                    double.class,
                    dimension,
                    mode,
                    boolean.class,
                    Runnable.class
                })) return VECTOR_WITH_FAILURE_HANDLER;
        return null;
    }

    public Object[] withFailureHandler(Object[] arguments) {
        if (this != VECTOR_WITH_FAILURE_HANDLER) return arguments;
        Object[] extended = Arrays.copyOf(arguments, arguments.length + 1);
        extended[arguments.length] = PROFILE_REJECTED;
        return extended;
    }

    private static final Runnable PROFILE_REJECTED =
            () -> {
                throw new IllegalStateException("Carpet rejected the FakePlayer profile");
            };
}
