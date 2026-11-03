package com.atsukimc.anticrash.mixin;

import com.atsukimc.anticrash.runtime.RuntimeExtensionNbt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerEntityManager;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.entity.PersistentEntitySectionManager;
*///?}

import org.spongepowered.asm.mixin.Mixin;

/** Entities saved because their section is being unloaded keep Vanilla's data only. */
//? if (>=1.18 && <=1.21.11) {
@Mixin(ServerEntityManager.class)
public abstract class ServerEntityManagerUnloadMixin {
    @WrapMethod(method = "unload(J)Z")
    private boolean anticrash$scopeUnload(long chunkPos, Operation<Boolean> original)
                //?}
                //? if (>=26.1 && <=26.3) {
                /*@Mixin(PersistentEntitySectionManager.class)
                public abstract class ServerEntityManagerUnloadMixin {
                    @WrapMethod(method = "processChunkUnload(J)Z")
                    private boolean anticrash$scopeUnload(long chunkPos, Operation<Boolean> original)
*///?}
            {
        RuntimeExtensionNbt.entityUnload(true);
        try {
            return original.call(chunkPos);
        } finally {
            RuntimeExtensionNbt.entityUnload(false);
        }
    }
}
