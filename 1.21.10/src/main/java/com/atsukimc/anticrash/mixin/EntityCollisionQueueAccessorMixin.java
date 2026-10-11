package com.atsukimc.anticrash.mixin;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Entity.class)
public interface EntityCollisionQueueAccessorMixin {
    @Accessor("queuedCollisionChecks") java.util.ArrayDeque<?> anticrash$collisionChecks();
}
