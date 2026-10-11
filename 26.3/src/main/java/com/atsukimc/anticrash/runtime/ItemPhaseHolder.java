package com.atsukimc.anticrash.runtime;

/** Implemented on ItemEntity (phase of 4) and ExperienceOrbEntity (phase of 40) by their mixins. Lives outside the mixin package: classes there cannot be loaded as ordinary classes. */
public interface ItemPhaseHolder {
    int anticrash$idPhase();

    void anticrash$setIdPhase(int phase);
}
