package com.atsukimc.anticrash.logging;

/** Bounded exception diagnostics. Never builds a full printStackTrace buffer. */
public final class FailureDetails {
    private static final int LIMIT = 8192;

    private FailureDetails() {}

    public static String describe(Throwable failure) {
        if (failure == null) return "unknown failure";
        if (failure instanceof OutOfMemoryError)
            return "java.lang.OutOfMemoryError (details omitted)";
        StringBuilder result = new StringBuilder(512);
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 8 && result.length() < LIMIT; depth++) {
            if (depth > 0) result.append(" | caused by ");
            result.append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null)
                result.append(": ").append(message, 0, Math.min(message.length(), 512));
            if (current instanceof OutOfMemoryError) break;
            StackTraceElement[] stack = current.getStackTrace();
            for (int i = 0; i < Math.min(12, stack.length) && result.length() < LIMIT; i++) {
                StackTraceElement frame = stack[i];
                // Class/method/line identify the code without adding source filesystem paths.
                result.append(" | at ")
                        .append(frame.getClassName())
                        .append('.')
                        .append(frame.getMethodName())
                        .append(':')
                        .append(frame.getLineNumber());
            }
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        if (result.length() > LIMIT) result.setLength(LIMIT);
        String sanitized = result.toString().replace("\r", "\\r").replace("\n", "\\n");
        return sanitized.length() <= LIMIT ? sanitized : sanitized.substring(0, LIMIT);
    }
}
