package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.*;

import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** 26.3 separates fluid-type contacts from tag-based currents. Preserve both domains and order. */
public final class FluidInteractionSnapshot {
    private FluidInteractionSnapshot() {}

    public enum Kind {
        FLUID,
        CURRENT
    }

    public record State(
            Kind kind, String tag, double height, boolean eyes, Vec3 current, int count) {
        public State(String tag, double height, boolean eyes, Vec3 current, int count) {
            this(Kind.CURRENT, tag, height, eyes, current, count);
        }

        public State {
            net.minecraft.resources.Identifier.parse(tag);
            if (kind == null
                    || !Double.isFinite(height)
                    || height < 0
                    || current == null
                    || !Double.isFinite(current.x)
                    || !Double.isFinite(current.y)
                    || !Double.isFinite(current.z)
                    || count < 0)
                throw new IllegalArgumentException("Invalid fluid interaction state");
        }

        public CompoundTag write() {
            var n = new CompoundTag();
            n.putString("Kind", kind.name());
            n.putString("Tag", tag);
            n.putDouble("Height", height);
            n.putBoolean("Eyes", eyes);
            n.store("Current", Vec3.CODEC, current);
            n.putInt("Count", count);
            return n;
        }

        public static State read(CompoundTag n) {
            return new State(
                    Kind.valueOf(n.getString("Kind").orElseThrow()),
                    n.getString("Tag").orElseThrow(),
                    n.getDouble("Height").orElseThrow(),
                    n.getBoolean("Eyes").orElseThrow(),
                    n.read("Current", Vec3.CODEC).orElseThrow(),
                    n.getInt("Count").orElseThrow());
        }
    }

    public static ListTag capture(Entity entity) {
        var interaction = ((EntityFluidAccessor) entity).anticrash$fluids();
        var access = (FluidInteractionAccessor) interaction;
        var n = new ListTag();
        for (Object tracker : access.anticrash$trackers()) {
            var t = (FluidTrackerAccessor) tracker;
            n.add(
                    new State(
                                    Kind.FLUID,
                                    t.anticrash$type()
                                            .unwrapKey()
                                            .orElseThrow()
                                            .identifier()
                                            .toString(),
                                    t.anticrash$height(),
                                    t.anticrash$eyes(),
                                    Vec3.ZERO,
                                    0)
                            .write());
        }
        access.anticrash$currents()
                .forEach((tag, value) -> {
                    var c = (FluidCurrentAccessor) value;
                    n.add(
                            new State(
                                            Kind.CURRENT,
                                            tag.location().toString(),
                                            c.anticrash$height(),
                                            false,
                                            c.anticrash$current(),
                                            c.anticrash$count())
                                    .write());
                });
        return n;
    }

    public static List<State> decode(ListTag n) {
        if (n.size() > 4096) throw new IllegalArgumentException("Too many fluid trackers");
        List<State> result = new ArrayList<>(n.size());
        Set<String> ids = new HashSet<>();
        for (Tag entry : n) {
            if (!(entry instanceof CompoundTag compound))
                throw new IllegalArgumentException("Invalid fluid entry");
            var state = State.read(compound);
            if (!ids.add(state.kind() + ":" + state.tag()))
                throw new IllegalArgumentException("Duplicate fluid entry");
            if (state.kind() == Kind.FLUID
                    && (!state.current().equals(Vec3.ZERO) || state.count() != 0))
                throw new IllegalArgumentException(
                        "A fluid tracker cannot own a current accumulator");
            if (state.kind() == Kind.CURRENT && state.eyes())
                throw new IllegalArgumentException("A tag current cannot own eye contact");
            result.add(state);
        }
        // getCurrentAccumulatorFor chooses the first matching tag. Sorting changes semantics.
        return List.copyOf(result);
    }

    static void validateDomains(
            List<State> expected, List<String> existingFluids, List<String> currentTags) {
        var fluids = expected.stream().filter(s -> s.kind() == Kind.FLUID).map(State::tag).toList();
        var currents =
                expected.stream().filter(s -> s.kind() == Kind.CURRENT).map(State::tag).toList();
        if (!currents.equals(currentTags)
                || existingFluids.size() > fluids.size()
                || !fluids.subList(0, existingFluids.size()).equals(existingFluids))
            throw new IllegalStateException("Fluid interaction domain/order differs");
    }

    /**
     * Native private return type is not source-accessible. Invoke the audited Vanilla factory,
     * rather than allocate/replace its final tracker collection or serialize Java objects.
     */
    static Object createTracker(
            net.minecraft.world.entity.EntityFluidInteraction interaction,
            net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid> fluid) {
        try {
            return TrackerFactory.METHOD.invoke(interaction, fluid);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native fluid tracker creation failed", error);
        }
    }

    private static final class TrackerFactory {
        static final java.lang.reflect.Method METHOD = method();

        private static java.lang.reflect.Method method() {
            try {
                var m =
                        net.minecraft.world.entity.EntityFluidInteraction.class.getDeclaredMethod(
                                "getOrCreateTrackerFor", net.minecraft.core.Holder.class);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported fluid tracker factory", error);
            }
        }
    }

    public static void restore(Entity entity, ListTag n) {
        var expected = decode(n);
        var interaction = ((EntityFluidAccessor) entity).anticrash$fluids();
        var access = (FluidInteractionAccessor) interaction;
        var currents = new LinkedHashMap<String, FluidCurrentAccessor>();
        access.anticrash$currents()
                .forEach((tag, value) ->
                        currents.put(tag.location().toString(), (FluidCurrentAccessor) value));
        var existing =
                access.anticrash$trackers().stream()
                        .map(t ->
                                ((FluidTrackerAccessor) t)
                                        .anticrash$type()
                                        .unwrapKey()
                                        .orElseThrow()
                                        .identifier()
                                        .toString())
                        .toList();
        validateDomains(expected, existing, List.copyOf(currents.keySet()));
        var holders =
                new HashMap<
                        String,
                        net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>>();
        for (var s : expected)
            if (s.kind() == Kind.FLUID) {
                var key =
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.FLUID,
                                net.minecraft.resources.Identifier.parse(s.tag()));
                holders.put(
                        s.tag(),
                        net.minecraft.core.registries.BuiltInRegistries.FLUID
                                .get(key)
                                .orElseThrow());
            }
        // All entries have been validated before any value changes.
        for (var s : expected) {
            if (s.kind() == Kind.FLUID) {
                var t = (FluidTrackerAccessor) createTracker(interaction, holders.get(s.tag()));
                t.anticrash$height(s.height());
                t.anticrash$eyes(s.eyes());
            } else {
                var c = currents.get(s.tag());
                c.anticrash$height(s.height());
                c.anticrash$current(s.current());
                c.anticrash$count(s.count());
            }
        }
    }
}
