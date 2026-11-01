package com.atsukimc.anticrash.legacy;

import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reads the exact Yarn build.21 JAR. No Minecraft class/world/server is instantiated. */
class LegacyBytecodeContractTest {
    private ClassNode read(String name) throws Exception {
        try(var jar=new ZipFile(System.getProperty("anticrash.legacy.gameJar"))) {
            try(var metadata=jar.getInputStream(jar.getEntry("version.json"))) {
                String version=new String(metadata.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"1\\.16\\.1\"").matcher(version).find(),"Wrong Minecraft JAR");
            }
            var entry=jar.getEntry(name+".class"); assertNotNull(entry,name);
            try(var stream=jar.getInputStream(entry)) {
                var node=new ClassNode(); new ClassReader(stream).accept(node,0); return node;
            }
        }
    }
    private MethodNode method(ClassNode owner,String name) {
        return owner.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();
    }
    private boolean invokes(MethodNode method,String owner,String name) {
        for(var insn:method.instructions) if(insn instanceof MethodInsnNode call
                && call.owner.equals(owner) && call.name.equals(name)) return true;
        return false;
    }
    @Test void schedulerHasWholeWorldQueuesAndInvariantPreservingInsertion() throws Exception {
        var owner=read("net/minecraft/server/world/ServerTickScheduler");
        var fields=new HashMap<String,String>(); owner.fields.forEach(f->fields.put(f.name,f.desc));
        assertEquals("Ljava/util/Set;",fields.get("scheduledTickActions"));
        assertEquals("Ljava/util/TreeSet;",fields.get("scheduledTickActionsInOrder"));
        assertEquals("Ljava/util/Queue;",fields.get("currentTickActions"));
        assertEquals("Ljava/util/List;",fields.get("consumedTickActions"));
        var insertion=method(owner,"addScheduledTick");
        assertTrue(invokes(insertion,"java/util/Set","add"));
        assertTrue(invokes(insertion,"java/util/TreeSet","add"));
    }
    @Test void evenSchedulerQueriesAllocateIdsAndCannotBeUsedForObservationalCapture() throws Exception {
        var scheduler=read("net/minecraft/server/world/ServerTickScheduler");
        for(String query:List.of("isScheduled","isTicking"))
            assertTrue(invokes(method(scheduler,query),"net/minecraft/world/ScheduledTick","<init>"));
        var tick=read("net/minecraft/world/ScheduledTick");
        assertTrue(tick.fields.stream().anyMatch(f->f.name.equals("idCounter") && f.desc.equals("J") && (f.access&8)!=0));
        assertTrue(tick.fields.stream().anyMatch(f->f.name.equals("id") && f.desc.equals("J")));
    }
    @Test void nativeTickNbtOmitsSequenceAndNarrowsTimeToInteger() throws Exception {
        var save=method(read("net/minecraft/server/world/ServerTickScheduler"),"serializeScheduledTicks");
        var keys=new HashSet<String>(); boolean narrowing=false;
        for(var instruction:save.instructions) {
            if(instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String s) keys.add(s);
            if(instruction.getOpcode()==org.objectweb.asm.Opcodes.L2I) narrowing=true;
        }
        assertEquals(Set.of("i","x","y","z","t","p"),keys);
        assertTrue(narrowing,"Exact capsule must preserve long time rather than reproduce native truncation");
    }
    @Test void oldWorldOwnsEntitiesAndBlockEntitiesWithoutModernEntityManager() throws Exception {
        var world=read("net/minecraft/server/world/ServerWorld");
        for(String field:List.of("entitiesById","entitiesByUuid","entitiesToLoad","syncedBlockEventQueue"))
            assertTrue(world.fields.stream().anyMatch(f->f.name.equals(field)),field);
        var base=read("net/minecraft/world/World");
        for(String field:List.of("blockEntities","tickingBlockEntities","pendingBlockEntities","lcgBlockSeed","random"))
            assertTrue(base.fields.stream().anyMatch(f->f.name.equals(field)),field);
        assertFalse(base.fields.stream().anyMatch(f->f.name.equals("tickOrder")));
        try(var jar=new ZipFile(System.getProperty("anticrash.legacy.gameJar"))) {
            assertNull(jar.getEntry("net/minecraft/server/world/ServerEntityManager.class"));
        }
    }
    @Test void publicSyncFutureRunsTasksButPrivateFutureCanBePolled() throws Exception {
        var cache=read("net/minecraft/server/world/ServerChunkManager");
        assertTrue(invokes(method(cache,"getChunkFutureSyncOnMainThread"),
                "net/minecraft/server/world/ServerChunkManager$MainThreadExecutor","runTasks"));
        assertFalse(invokes(method(cache,"getChunkFuture"),
                "net/minecraft/server/world/ServerChunkManager$MainThreadExecutor","runTasks"));
        var holder=read("net/minecraft/server/world/ChunkHolder");
        for(String name:List.of("getBorderFuture","getTickingFuture","getEntityTickingFuture"))
            assertTrue(method(holder,name).desc.endsWith("Ljava/util/concurrent/CompletableFuture;"));
    }
}
