package com.atsukimc.anticrash.integration.carpet;

import com.atsukimc.anticrash.player.InventorySnapshot;
import com.atsukimc.anticrash.util.AtomicFiles;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class FakePlayerSnapshot {
    public int formatVersion = 1;
    public String uuid, name, dimension;
    public double x, y, z;
    public float yaw, pitch;
    public String gamemode;
    public boolean flying, sneaking, sprinting;
    public float forward, strafing;
    public InventorySnapshot inventory;
    public float health;
    public int hunger;
    public float saturation, exhaustion;
    public final List<String> effectsNbt = new ArrayList<>();
    public JsonObject actionPackState;
    public Integer itemUseCooldown;
    public Integer blockBreakingProgress;
    public String currentBlock;
    public String vehicleUuid;

    /**
     * The vehicle the FakePlayer rides, with its other passengers, as NBT taken at the cut. Vanilla
     * does not write an entity ridden by a player into its chunk: it lives only in that player's
     * data file, which is outside the checkpoint and is whatever the last player save left. Null
     * when not riding, and in snapshots written before the field existed.
     */
    public String rootVehicleNbt;

    /**
     * Player-side state the ActionPack does not hold. A FakePlayer drawing a bow, eating or holding
     * a shield up has been doing so for some ticks; without these it starts over. All null in
     * snapshots written before they existed.
     */
    public String activeHand;

    public Integer itemUseTimeLeft;

    /** Ticks since the last attack; decides the strength of the next hit. */
    public Integer lastAttackedTicks;

    /**
     * Item id and remaining ticks of every running item cooldown (ender pearl, shield, chorus
     * fruit).
     */
    public List<ItemCooldown> itemCooldowns;

    public static final class ItemCooldown {
        public String item;
        public int remainingTicks;
    }

    /** Stable semantic representation used by the equivalence report, not by restore itself. */
    public String canonical() {
        return String.join(
                        "|",
                        value(uuid),
                        value(name),
                        value(dimension),
                        Double.toString(x),
                        Double.toString(y),
                        Double.toString(z),
                        Float.toString(yaw),
                        Float.toString(pitch),
                        value(gamemode),
                        Boolean.toString(flying),
                        Boolean.toString(sneaking),
                        Boolean.toString(sprinting),
                        Float.toString(forward),
                        Float.toString(strafing),
                        inventory == null ? "null" : AtomicFiles.GSON.toJson(inventory),
                        Float.toString(health),
                        Integer.toString(hunger),
                        Float.toString(saturation),
                        Float.toString(exhaustion),
                        effectsNbt == null ? "null" : effectsNbt.toString(),
                        actionPackState == null
                                ? "null"
                                : withoutNullMembers(actionPackState).toString(),
                        String.valueOf(itemUseCooldown),
                        String.valueOf(blockBreakingProgress),
                        value(currentBlock),
                        value(vehicleUuid))
                + runtimeCanonical();
    }

    /** Item use, attack cooldown and item cooldowns only; null when the snapshot predates them. */
    public String useRuntimeCanonical() {
        String runtime = runtimeCanonical();
        return runtime.isEmpty() ? null : value(uuid) + runtime;
    }

    /** Empty for a snapshot without the newer fields, so its fingerprint is what it always was. */
    private String runtimeCanonical() {
        if (activeHand == null
                && itemUseTimeLeft == null
                && lastAttackedTicks == null
                && itemCooldowns == null) {
            return "";
        }
        StringBuilder cooldowns = new StringBuilder();
        if (itemCooldowns != null) {
            List<String> rows = new ArrayList<>();
            for (ItemCooldown cooldown : itemCooldowns) {
                if (cooldown != null) rows.add(cooldown.item + "=" + cooldown.remainingTicks);
            }
            java.util.Collections.sort(rows);
            cooldowns.append(rows);
        }
        return "|"
                + value(activeHand)
                + "|"
                + itemUseTimeLeft
                + "|"
                + lastAttackedTicks
                + "|"
                + cooldowns;
    }

    /**
     * The action pack as it reads back from fakeplayers.json. A live capture holds explicit null
     * members (for example {@code currentBlock} while nothing is being mined), but Gson omits null
     * object members when the file is written, so the canonical form must omit them too. Without
     * this the fingerprint taken at capture never matched the one recomputed from the file, and
     * every checkpoint with a FakePlayer was refused at restore.
     */
    static com.google.gson.JsonElement withoutNullMembers(com.google.gson.JsonElement element) {
        if (element == null || element.isJsonNull() || element.isJsonPrimitive()) return element;
        if (element.isJsonArray()) {
            com.google.gson.JsonArray copy = new com.google.gson.JsonArray();
            for (com.google.gson.JsonElement child : element.getAsJsonArray())
                copy.add(withoutNullMembers(child));
            return copy;
        }
        com.google.gson.JsonObject copy = new com.google.gson.JsonObject();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry :
                element.getAsJsonObject().entrySet()) {
            if (entry.getValue() == null || entry.getValue().isJsonNull()) continue;
            copy.add(entry.getKey(), withoutNullMembers(entry.getValue()));
        }
        return copy;
    }

    private static String value(String value) {
        return value == null ? "null" : value;
    }
}
