//? if (>=1.21.2 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.21.2 && <=1.21.11) {
/^import net.minecraft.world.entity.Entity;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.Entity;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityCollisionQueueAccessorMixin {

//? if (>=1.21.2 && <=1.21.5) {
/^@Accessor("movementThisTick")
    java.util.List<?> anticrash$collisionChecks();
^///?}
//? if (>=1.21.6 && <=1.21.11) {
/^@Accessor("movementThisTick")
    java.util.ArrayDeque<?> anticrash$collisionChecks();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("movementThisTick")
    java.util.ArrayDeque<?> anticrash$collisionChecks();
^///?}

}
*///?}
