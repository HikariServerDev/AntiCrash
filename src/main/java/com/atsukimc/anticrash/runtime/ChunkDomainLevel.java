package com.atsukimc.anticrash.runtime;

/** Stable checkpoint names, independent of Minecraft's renamed chunk-readiness enum constants. */
public final class ChunkDomainLevel {
    private ChunkDomainLevel() {}

    public static String canonical(String nativeName) {
        if (nativeName == null) throw new IllegalArgumentException("Missing chunk level type");
        return switch (nativeName) {
            case "INACCESSIBLE", "ENTITY_TICKING", "BORDER", "TICKING" -> nativeName;
            case "FULL" -> "BORDER";
            case "BLOCK_TICKING" -> "TICKING";
            default ->
                    throw new IllegalArgumentException("Unknown chunk level type: " + nativeName);
        };
    }
}
