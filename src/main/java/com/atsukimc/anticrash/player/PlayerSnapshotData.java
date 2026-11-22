package com.atsukimc.anticrash.player;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSnapshotData {
    public int formatVersion = 1;
    public String uuid;
    public String name;
    public String dimension;
    public double x, y, z;
    public float yaw, pitch;
    public String gamemode;
    public String abilitiesNbt;
    public InventorySnapshot inventory;
    public int experienceLevel;
    public float experienceProgress;
    public int experienceTotal;
    public String enderChestNbt;
    public float health, absorption;
    public int foodLevel;
    public float foodSaturation, foodExhaustion;
    public final List<String> effectsNbt = new ArrayList<>();
    public double velocityX, velocityY, velocityZ;

    //? if <=1.21.4 {
    /*public float fallDistance;
    *///?} else {
    public double fallDistance;
    //?}

    public int fireTicks, air;
    public boolean hasSpawnPoint;
    public String spawnDimension;
    public int spawnX, spawnY, spawnZ;
    public float spawnAngle;

    //? if >=1.21.9 {
    public float spawnPitch;
    //?}

    public boolean spawnForced;
}
