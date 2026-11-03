//? if (>=1.20.5 && <=26.3) {
/*package com.atsukimc.anticrash.mixin;

//? if (>=1.20.5 && <=1.21.10) {
/^import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
^///?}
//? if 1.21.11 {
/^import net.minecraft.world.level.chunk.storage.IOWorker;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.chunk.storage.IOWorker;
^///?}

//? if (>=1.20.5 && <=1.21.10) {
/^import net.minecraft.world.level.chunk.storage.IOWorker;
^///?}
//? if 1.21.11 {
/^import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
^///?}


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if (>=1.20.5 && <=1.21.10) {
/^@Mixin(SimpleRegionStorage.class)
public interface ChunkPosKeyedStorageAccessor {

^///?}
//? if 1.21.11 {
/^@Mixin(SimpleRegionStorage.class)
public interface ChunkPosKeyedStorageAccessor {

^///?}
//? if (>=1.20.5 && <=1.21.11) {
/^@Accessor("worker")
    IOWorker anticrash$getWorker();
^///?}
//? if (>=26.1 && <=26.3) {
/^@Mixin(SimpleRegionStorage.class)
public interface ChunkPosKeyedStorageAccessor {
    @Accessor("worker")
    IOWorker anticrash$getWorker();
^///?}

}
*///?}
