package com.atsukimc.anticrash.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code BrewTime} counts down every tick without marking the chunk for saving. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandBlockEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private static void anticrash$markBrewTimeDirty(World world, BlockPos pos, BlockState state,
                                                    BrewingStandBlockEntity stand, CallbackInfo callbackInfo) {
        if (((BrewingStandBlockEntityTimerAccessor) stand).anticrash$getBrewTime() > 0) world.markDirty(pos);
    }

    @org.spongepowered.asm.mixin.Shadow private int brewTime;
    @org.spongepowered.asm.mixin.Shadow private net.minecraft.item.Item itemBrewing;

    /**
     * Vanilla does not save which ingredient the running brew started with. After a load it is
     * null, the first tick sees "ingredient changed" and resets the brew to zero, so a brew in
     * progress starts over (up to 400 ticks late).
     */
    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void anticrash$saveBrewingItem(net.minecraft.nbt.NbtCompound nbt, CallbackInfo callbackInfo) {
        if (!com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.savingLoadedChunk()) return;
        com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.write(nbt, extension -> {
            extension.remove("ItemBrewing");
            if (brewTime > 0 && itemBrewing != null) {
                extension.putString("ItemBrewing", net.minecraft.util.registry.Registry.ITEM.getId(itemBrewing).toString());
            }
        });
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void anticrash$loadBrewingItem(net.minecraft.nbt.NbtCompound nbt, CallbackInfo callbackInfo) {
        net.minecraft.nbt.NbtCompound extension = com.atsukimc.anticrash.runtime.RuntimeExtensionNbt.read(nbt);
        if (extension == null || !extension.contains("ItemBrewing", 8) || brewTime <= 0) return;
        net.minecraft.util.Identifier id = net.minecraft.util.Identifier.tryParse(extension.getString("ItemBrewing"));
        if (id != null && net.minecraft.util.registry.Registry.ITEM.containsId(id)) {
            itemBrewing = net.minecraft.util.registry.Registry.ITEM.get(id);
        }
    }
}
