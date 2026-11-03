//? if (>=1.21 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.entity.item.PrimedTnt;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.entity.item.PrimedTnt;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.21 && <=1.21.11) {
/^@Mixin(PrimedTnt.class)
public interface TntRuntimeAccessorMixin {
    @Accessor("usedPortal")
    boolean anticrash$teleported();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(PrimedTnt.class)
public interface TntRuntimeAccessorMixin {
    @Accessor("usedPortal")
    boolean anticrash$teleported();
^///?}



//? if (>=1.21 && <=1.21.11) {
/^@Accessor("usedPortal")
    void anticrash$setTeleported(boolean value);
^///?}
//? if (>=26.1 && <=26.3) {
/^@Accessor("usedPortal")
    void anticrash$setTeleported(boolean value);
^///?}

}
*///?}
