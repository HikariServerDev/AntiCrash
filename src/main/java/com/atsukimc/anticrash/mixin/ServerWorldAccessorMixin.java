package com.atsukimc.anticrash.mixin;

import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.entity.Entity;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.server.level.ServerLevel;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.BlockEvent;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.entity.Entity;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerEntityManager;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.BlockEventData;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.server.world.ServerWorld;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.entity.EntityTickList;
*///?}

//? if (>=1.18 && <=1.21.11) {
import net.minecraft.world.EntityList;
//?}
//? if (>=26.1 && <=26.3) {
/*import net.minecraft.world.level.entity.PersistentEntitySectionManager;
*///?}

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

//? if (>=1.18 && <=1.21.11) {
@Mixin(ServerWorld.class)
public interface ServerWorldAccessorMixin {
    @Accessor("syncedBlockEventQueue")
    ObjectLinkedOpenHashSet<BlockEvent> anticrash$getSyncedBlockEventQueue();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Mixin(ServerLevel.class)
    public interface ServerWorldAccessorMixin {
        @Accessor("blockEvents")
        ObjectLinkedOpenHashSet<BlockEventData> anticrash$getSyncedBlockEventQueue();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("blockEventQueue")
    List<BlockEvent> anticrash$getBlockEventQueue();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("blockEventsToReschedule")
        List<BlockEventData> anticrash$getBlockEventQueue();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("entityManager")
    ServerEntityManager<Entity> anticrash$getEntityManager();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("entityManager")
        PersistentEntitySectionManager<Entity> anticrash$getEntityManager();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("entityList")
    EntityList anticrash$getEntityList();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("entityTickList")
        EntityTickList anticrash$getEntityList();
*///?}

    /** Ticks this dimension has had neither a player nor a forced chunk; entities stop at 300. */

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("idleTimeout")
    int anticrash$getIdleTimeout();

    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("emptyTime")
        int anticrash$getIdleTimeout();
*///?}

    //? if (>=1.18 && <=1.21.11) {
    @Accessor("idleTimeout")
    void anticrash$setIdleTimeout(int ticks);
    //?}
    //? if (>=26.1 && <=26.3) {
    /*@Accessor("emptyTime")
        void anticrash$setIdleTimeout(int ticks);
*///?}

}
