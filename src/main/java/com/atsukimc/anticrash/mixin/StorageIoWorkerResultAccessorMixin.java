package com.atsukimc.anticrash.mixin;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.nbt.NbtCompound;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.nbt.CompoundTag;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.18 && <=1.21.11) {
@Mixin(targets = "net.minecraft.world.storage.StorageIoWorker$Result")
public interface StorageIoWorkerResultAccessorMixin {
    @Accessor("nbt")
    NbtCompound anticrash$getNbt();
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(targets = "net.minecraft.world.level.chunk.storage.IOWorker$PendingStore")
    public interface StorageIoWorkerResultAccessorMixin {
        @Accessor("data")
        CompoundTag anticrash$getNbt();
*///?}

}
