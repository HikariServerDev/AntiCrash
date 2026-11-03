//? if (>=1.21 && <=26.3) {
/*package com.atsukimc.anticrash.runtime;

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.server.level.ChunkHolder;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.server.level.ChunkHolder;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.level.chunk.LevelChunk;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.chunk.ImposterProtoChunk;
^///?}

//? if (>=1.21 && <=1.21.11) {
/^import net.minecraft.world.level.chunk.ImposterProtoChunk;
^///?}
//? if (>=26.1 && <=26.3) {
/^import net.minecraft.world.level.chunk.LevelChunk;
^///?}


/^* Read-only, non-loading view. ChunkHolder#getWorldChunk exposes ticking, not all loaded FULL. ^/
public final class VersionChunkView {
    private VersionChunkView() {}


//? if (>=1.21 && <=1.21.11) {
/^public static LevelChunk worldChunk(ChunkHolder holder)
^///?}
//? if (>=26.1 && <=26.3) {
/^public static LevelChunk worldChunk(ChunkHolder holder)
^///?}
{

//? if (>=1.21 && <=1.21.11) {
/^var latest = holder.getLatestChunk();
^///?}
//? if (>=26.1 && <=26.3) {
/^var latest = holder.getLatestChunk();
^///?}


//? if (>=1.21 && <=1.21.11) {
/^if (latest instanceof ImposterProtoChunk wrapper) latest = wrapper.getWrapped();
^///?}
//? if (>=26.1 && <=26.3) {
/^if (latest instanceof ImposterProtoChunk wrapper) latest = wrapper.getWrapped();
^///?}


//? if (>=1.21 && <=1.21.11) {
/^return latest instanceof LevelChunk chunk ? chunk : null;
^///?}
//? if (>=26.1 && <=26.3) {
/^return latest instanceof LevelChunk chunk ? chunk : null;
^///?}

    }
}
*///?}
