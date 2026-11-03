//? if (>=26.1 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.*;

import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/^* Reconstructs tracker values only, never replaces Vanilla's final map or its objects. ^/


/^* 26.3 separates fluid-type contacts from tag-based currents. Preserve both domains and order. ^/
public final class FluidInteractionSnapshot {
    private FluidInteractionSnapshot() {}


//? if (>=26.1 && <=26.2) {
/^public record State(
^///?}
//? if 26.3 {
/^public enum Kind {
        FLUID,
        CURRENT
    }
^///?}



//? if 26.3 {
/^public record State(
            Kind kind,
^///?}
String tag, double height, boolean eyes, Vec3 current, int count) {

//? if 26.3 {
/^public State(String tag, double height, boolean eyes, Vec3 current, int count) {
            this(Kind.CURRENT, tag, height, eyes, current, count);
        }
^///?}


        public State {
            net.minecraft.resources.Identifier.parse(tag);

//? if (>=26.1 && <=26.2) {
/^if (!Double.isFinite(height)
                    || height < 0
                    || current == null
                    || !Double.isFinite(current.x)
                    || !Double.isFinite(current.y)
                    || !Double.isFinite(current.z)
                    || count < 0)

^///?}
//? if 26.3 {
/^if (kind == null
                    || !Double.isFinite(height)
                    || height < 0
                    || current == null
                    || !Double.isFinite(current.x)
                    || !Double.isFinite(current.y)
                    || !Double.isFinite(current.z)
                    || count < 0)

^///?}
throw new IllegalArgumentException("Invalid fluid interaction state");
        }

        public CompoundTag write() {
            var n = new CompoundTag();

//? if 26.3 {
/^n.putString("Kind", kind.name());
^///?}

            n.putString("Tag", tag);
            n.putDouble("Height", height);
            n.putBoolean("Eyes", eyes);
            n.store("Current", Vec3.CODEC, current);
            n.putInt("Count", count);
            return n;
        }

        public static State read(CompoundTag n) {

//? if (>=26.1 && <=26.2) {
/^return new State(
                    n.getString("Tag").orElseThrow(),
                    n.getDouble("Height").orElseThrow(),
                    n.getBoolean("Eyes").orElseThrow(),
                    n.read("Current", Vec3.CODEC).orElseThrow(),
                    n.getInt("Count").orElseThrow());
^///?}
//? if 26.3 {
/^return new State(
                    Kind.valueOf(n.getString("Kind").orElseThrow()),
                    n.getString("Tag").orElseThrow(),
                    n.getDouble("Height").orElseThrow(),
                    n.getBoolean("Eyes").orElseThrow(),
                    n.read("Current", Vec3.CODEC).orElseThrow(),
                    n.getInt("Count").orElseThrow());
^///?}

        }
    //? if (>=26.1 && <=26.2) {

    /^}
    ^///?}



//? if (>=26.1 && <=26.2) {
/^private static Map<String, FluidTrackerAccessor> trackers(Entity entity) {
        var source =
                ((FluidInteractionAccessor) ((EntityFluidAccessor) entity).anticrash$fluids())
                        .anticrash$trackers();
^///?}


//? if (>=26.1 && <=26.2) {
/^Map<String, FluidTrackerAccessor> result = new TreeMap<>();
^///?}


//? if (>=26.1 && <=26.2) {
/^source.forEach((tag, tracker) -> {
            if (result.put(tag.location().toString(), (FluidTrackerAccessor) tracker) != null)
                throw new IllegalStateException("Duplicate fluid tag");
        });
^///?}


//? if (>=26.1 && <=26.2) {
/^return result;
^///?}

    }

    public static ListTag capture(Entity entity) {

//? if 26.3 {
/^var interaction = ((EntityFluidAccessor) entity).anticrash$fluids();
^///?}


//? if 26.3 {
/^var access = (FluidInteractionAccessor) interaction;
^///?}

        var n = new ListTag();

//? if (>=26.1 && <=26.2) {
/^trackers(entity)
                .forEach((tag, t) ->
                        n.add(
                                new State(
                                                tag,
                                                t.anticrash$height(),
                                                t.anticrash$eyes(),
                                                t.anticrash$current(),
                                                t.anticrash$count())
                                        .write()));
^///?}
//? if 26.3 {
/^for (Object tracker : access.anticrash$trackers()) {
            var t = (FluidTrackerAccessor) tracker;
^///?}


//? if 26.3 {
/^n.add(
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
^///?}


//? if 26.3 {
/^access.anticrash$currents()
                .forEach((tag, value) -> {
                    var c = (FluidCurrentAccessor) value;
^///?}


//? if 26.3 {
/^n.add(
                            new State(
                                            Kind.CURRENT,
                                            tag.location().toString(),
                                            c.anticrash$height(),
                                            false,
                                            c.anticrash$current(),
                                            c.anticrash$count())
                                    .write());
                });
^///?}

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

//? if (>=26.1 && <=26.2) {
/^if (!ids.add(state.tag()))
^///?}
//? if 26.3 {
/^if (!ids.add(state.kind() + ":" + state.tag()))

^///?}
throw new IllegalArgumentException("Duplicate fluid entry");

//? if 26.3 {
/^if (state.kind() == Kind.FLUID
                    && (!state.current().equals(Vec3.ZERO) || state.count() != 0))
                throw new IllegalArgumentException(
                        "A fluid tracker cannot own a current accumulator");
^///?}


//? if 26.3 {
/^if (state.kind() == Kind.CURRENT && state.eyes())
                throw new IllegalArgumentException("A tag current cannot own eye contact");
^///?}

            result.add(state);
        }
        // getCurrentAccumulatorFor chooses the first matching tag. Sorting changes semantics.

//? if 26.3 {
/^return List.copyOf(result);
    }
^///?}



//? if 26.3 {
/^static void validateDomains(
            List<State> expected, List<String> existingFluids, List<String> currentTags) {
        var fluids = expected.stream().filter(s -> s.kind() == Kind.FLUID).map(State::tag).toList();
^///?}


//? if (>=26.1 && <=26.2) {
/^result.sort(Comparator.comparing(State::tag));
^///?}
//? if 26.3 {
/^var currents =
                expected.stream().filter(s -> s.kind() == Kind.CURRENT).map(State::tag).toList();
^///?}


//? if (>=26.1 && <=26.2) {
/^return result;
^///?}
//? if 26.3 {
/^if (!currents.equals(currentTags)
                || existingFluids.size() > fluids.size()
                || !fluids.subList(0, existingFluids.size()).equals(existingFluids))
            throw new IllegalStateException("Fluid interaction domain/order differs");
    }
^///?}


    /^*
     * Native private return type is not source-accessible. Invoke the audited Vanilla factory,
     * rather than allocate/replace its final tracker collection or serialize Java objects.
     ^/

//? if 26.3 {
/^static Object createTracker(
            net.minecraft.world.entity.EntityFluidInteraction interaction,
            net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid> fluid) {
        try {
            return TrackerFactory.METHOD.invoke(interaction, fluid);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native fluid tracker creation failed", error);
        }
    }
^///?}



//? if 26.3 {
/^private static final class TrackerFactory {
        static final java.lang.reflect.Method METHOD = method();
^///?}



//? if 26.3 {
/^private static java.lang.reflect.Method method() {
            try {
                var m =
                        net.minecraft.world.entity.EntityFluidInteraction.class.getDeclaredMethod(
                                "getOrCreateTrackerFor", net.minecraft.core.Holder.class);
^///?}


//? if 26.3 {
/^m.setAccessible(true);
^///?}


//? if 26.3 {
/^return m;
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported fluid tracker factory", error);
            }
        }
^///?}

    }

    public static void restore(Entity entity, ListTag n) {
        var expected = decode(n);

//? if (>=26.1 && <=26.2) {
/^var live = trackers(entity);
^///?}
//? if 26.3 {
/^var interaction = ((EntityFluidAccessor) entity).anticrash$fluids();
^///?}


//? if (>=26.1 && <=26.2) {
/^if (!live.keySet().equals(new HashSet<>(expected.stream().map(State::tag).toList())))
            throw new IllegalStateException("Fluid tracker domain differs");
^///?}
//? if 26.3 {
/^var access = (FluidInteractionAccessor) interaction;
^///?}


//? if 26.3 {
/^var currents = new LinkedHashMap<String, FluidCurrentAccessor>();
^///?}


//? if 26.3 {
/^access.anticrash$currents()
                .forEach((tag, value) ->
                        currents.put(tag.location().toString(), (FluidCurrentAccessor) value));
^///?}


//? if 26.3 {
/^var existing =
                access.anticrash$trackers().stream()
                        .map(t ->
                                ((FluidTrackerAccessor) t)
                                        .anticrash$type()
                                        .unwrapKey()
                                        .orElseThrow()
                                        .identifier()
                                        .toString())
                        .toList();
^///?}


//? if 26.3 {
/^validateDomains(expected, existing, List.copyOf(currents.keySet()));
^///?}


//? if 26.3 {
/^var holders =
                new HashMap<
                        String,
                        net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>>();
^///?}


//? if 26.3 {
/^for (var s : expected)
            if (s.kind() == Kind.FLUID) {
                var key =
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.FLUID,
                                net.minecraft.resources.Identifier.parse(s.tag()));
^///?}


//? if 26.3 {
/^holders.put(
                        s.tag(),
                        net.minecraft.core.registries.BuiltInRegistries.FLUID
                                .get(key)
                                .orElseThrow());
            }
^///?}

        // All entries have been validated before any value changes.
        for (var s : expected) {

//? if (>=26.1 && <=26.2) {
/^var t = live.get(s.tag());
^///?}


//? if 26.3 {
/^if (s.kind() == Kind.FLUID) {
                var t = (FluidTrackerAccessor) createTracker(interaction, holders.get(s.tag()));
^///?}

                t.anticrash$height(s.height());

                t.anticrash$eyes(s.eyes());

//? if (>=26.1 && <=26.2) {
/^t.anticrash$current(s.current());
^///?}


//? if (>=26.1 && <=26.2) {
/^t.anticrash$count(s.count());
^///?}
            //? if 26.3 {

            /^} else {
                var c = currents.get(s.tag());
            ^///?}


//? if 26.3 {
/^c.anticrash$height(s.height());
^///?}


//? if 26.3 {
/^c.anticrash$current(s.current());
^///?}


//? if 26.3 {
/^c.anticrash$count(s.count());
            }
^///?}

        }
    }
}
*///?}
