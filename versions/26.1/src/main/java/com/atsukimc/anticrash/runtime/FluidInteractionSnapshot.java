package com.atsukimc.anticrash.runtime;

import com.atsukimc.anticrash.mixin.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Reconstructs tracker values only, never replaces Vanilla's final map or its objects. */
public final class FluidInteractionSnapshot {
    private FluidInteractionSnapshot() { }
    public record State(String tag, double height, boolean eyes, Vec3 current, int count) {
        public State {
            net.minecraft.resources.Identifier.parse(tag);
            if (!Double.isFinite(height) || height < 0 || current == null
                    || !Double.isFinite(current.x) || !Double.isFinite(current.y)
                    || !Double.isFinite(current.z) || count < 0)
                throw new IllegalArgumentException("Invalid fluid interaction state");
        }
        public CompoundTag write() {
            var n = new CompoundTag();
            n.putString("Tag",tag); n.putDouble("Height",height); n.putBoolean("Eyes",eyes);
            n.store("Current",Vec3.CODEC,current); n.putInt("Count",count);
            return n;
        }
        public static State read(CompoundTag n) {
            return new State(n.getString("Tag").orElseThrow(),n.getDouble("Height").orElseThrow(),
                    n.getBoolean("Eyes").orElseThrow(),n.read("Current",Vec3.CODEC).orElseThrow(),
                    n.getInt("Count").orElseThrow());
        }
    }
    private static Map<String,FluidTrackerAccessor> trackers(Entity entity) {
        var source=((FluidInteractionAccessor)((EntityFluidAccessor)entity).anticrash$fluids()).anticrash$trackers();
        Map<String,FluidTrackerAccessor> result=new TreeMap<>();
        source.forEach((tag,tracker)-> {
            if (result.put(tag.location().toString(),(FluidTrackerAccessor)tracker)!=null)
                throw new IllegalStateException("Duplicate fluid tag");
        });
        return result;
    }
    public static ListTag capture(Entity entity) {
        var n=new ListTag();
        trackers(entity).forEach((tag,t)->n.add(new State(tag,t.anticrash$height(),t.anticrash$eyes(),
                t.anticrash$current(),t.anticrash$count()).write()));
        return n;
    }
    public static List<State> decode(ListTag n) {
        if(n.size()>4096) throw new IllegalArgumentException("Too many fluid trackers");
        List<State> result=new ArrayList<>(n.size()); Set<String> ids=new HashSet<>();
        for(Tag entry:n) {
            if(!(entry instanceof CompoundTag compound)) throw new IllegalArgumentException("Invalid fluid entry");
            var state=State.read(compound);
            if(!ids.add(state.tag())) throw new IllegalArgumentException("Duplicate fluid entry");
            result.add(state);
        }
        result.sort(Comparator.comparing(State::tag)); return result;
    }
    public static void restore(Entity entity,ListTag n) {
        var expected=decode(n); var live=trackers(entity);
        if(!live.keySet().equals(new HashSet<>(expected.stream().map(State::tag).toList())))
            throw new IllegalStateException("Fluid tracker domain differs");
        // All entries have been validated before any value changes.
        for(var s:expected) {
            var t=live.get(s.tag()); t.anticrash$height(s.height()); t.anticrash$eyes(s.eyes());
            t.anticrash$current(s.current()); t.anticrash$count(s.count());
        }
    }
}
