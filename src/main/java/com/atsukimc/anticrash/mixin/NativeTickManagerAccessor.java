//? if (>=1.20.3 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.20.3 && <=1.21.11) {
/^import net.minecraft.world.TickRateManager;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.TickRateManager;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.20.3 && <=1.21.11) {
/^@Mixin(TickRateManager.class)
public interface NativeTickManagerAccessor {
    @Accessor("runGameElements")
    void anticrash$setShouldTick(boolean value);
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(TickRateManager.class)
public interface NativeTickManagerAccessor {
    @Accessor("runGameElements")
    void anticrash$setShouldTick(boolean value);
^///?}

}
*///?}
