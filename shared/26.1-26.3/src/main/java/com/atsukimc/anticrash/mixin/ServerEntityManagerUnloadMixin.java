package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import org.spongepowered.asm.mixin.Mixin;

/** Entities saved because their section is being unloaded keep Vanilla's data only. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class ServerEntityManagerUnloadMixin {
    @WrapMethod(method = "processChunkUnload(J)Z")
    private boolean anticrash$scopeUnload(long chunkPos, Operation<Boolean> original) {
        RuntimeExtensionNbt.entityUnload(true);
        try {
            return original.call(chunkPos);
        } finally {
            RuntimeExtensionNbt.entityUnload(false);
        }
    }
}
