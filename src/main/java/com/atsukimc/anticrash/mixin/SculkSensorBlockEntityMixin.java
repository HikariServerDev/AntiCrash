//? if <=1.18.2 {
/*package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.atsukimc.anticrash.util.VersionIds;

import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationListener;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/^*
 * A sculk sensor that has heard a vibration waits {@code delay} ticks before it activates. In
 * 1.18.2 Vanilla saves only "last_vibration_frequency"; the listener's pending event, distance and
 * delay are not saved (that came in 1.19), so a restart silently drops the signal. There is no
 * Vanilla value for these three fields, hence no second authority.
 ^/
@Mixin(SculkSensorBlockEntity.class)
public abstract class SculkSensorBlockEntityMixin {
    @Shadow @Final private VibrationListener listener;

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void anticrash$savePendingVibration(CompoundTag nbt, CallbackInfo callbackInfo) {
        if (!RuntimeExtensionNbt.savingLoadedChunk()) return;
        SculkSensorListenerAccessorMixin accessor = (SculkSensorListenerAccessorMixin) listener;
        Optional<GameEvent> event = accessor.anticrash$getEvent();
        RuntimeExtensionNbt.write(
                nbt,
                extension -> {
                    extension.remove("Vibration");
                    if (event == null || event.isEmpty()) return;
                    CompoundTag state = new CompoundTag();
                    state.putString("Event", VersionIds.id(VersionIds.gameEvents(), event.get()));
                    state.putInt("Distance", accessor.anticrash$getDistance());
                    state.putInt("Delay", accessor.anticrash$getDelay());
                    extension.put("Vibration", state);
                });
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void anticrash$loadPendingVibration(CompoundTag nbt, CallbackInfo callbackInfo) {
        CompoundTag extension = RuntimeExtensionNbt.read(nbt);
        if (extension == null || !extension.contains("Vibration", 10)) return;
        CompoundTag state = extension.getCompound("Vibration");
        var id = VersionIds.tryParse(state.getString("Event"));
        if (id == null || !VersionIds.gameEvents().containsKey(id)) return;
        SculkSensorListenerAccessorMixin accessor = (SculkSensorListenerAccessorMixin) listener;
        accessor.anticrash$setEvent(Optional.of(VersionIds.value(VersionIds.gameEvents(), id)));
        accessor.anticrash$setDistance(Math.max(0, state.getInt("Distance")));
        accessor.anticrash$setDelay(Math.max(0, state.getInt("Delay")));
    }
}
*///?}
