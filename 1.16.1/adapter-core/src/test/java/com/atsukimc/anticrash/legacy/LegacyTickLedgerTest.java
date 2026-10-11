package com.atsukimc.anticrash.legacy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.*;

class LegacyTickLedgerTest {
    private LegacyTickLedger.Entry entry(String dimension, LegacyTickLedger.Kind kind, int x,
                                         long time, int priority, long sequence) {
        return new LegacyTickLedger.Entry(dimension,kind,"minecraft:water",x,64,122,time,priority,sequence);
    }
    private LegacyTickLedger.Entry tick(long time,int priority,long sequence) {
        return entry("minecraft:overworld",LegacyTickLedger.Kind.BLOCK,(int)sequence,time,priority,sequence);
    }
    @Test void exactSerializationPreservesLongTimeSequenceAndGaps() throws Exception {
        var expected=new LegacyTickLedger.Snapshot(100000000000L,List.of(tick(10000000000L,0,4),tick(-10,-3,2)));
        var bytes=new ByteArrayOutputStream(); expected.write(new DataOutputStream(bytes));
        assertEquals(expected,LegacyTickLedger.Snapshot.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
    @Test void executionOrderIsTimeThenPriorityThenGlobalSequence() {
        var snapshot=new LegacyTickLedger.Snapshot(100,List.of(tick(20,0,2),tick(10,1,5),tick(10,-1,9),tick(10,1,4)));
        assertEquals(List.of(9L,4L,5L,2L),snapshot.schedulerOrder("minecraft:overworld",LegacyTickLedger.Kind.BLOCK)
                .stream().map(LegacyTickLedger.Entry::sequence).toList());
    }
    @Test void dimensionAndBlockFluidDomainsAreSeparate() {
        var a=entry("minecraft:overworld",LegacyTickLedger.Kind.BLOCK,1,0,0,1);
        var b=entry("minecraft:the_nether",LegacyTickLedger.Kind.BLOCK,1,0,0,2);
        var c=entry("minecraft:overworld",LegacyTickLedger.Kind.FLUID,1,0,0,3);
        var snapshot=new LegacyTickLedger.Snapshot(9,List.of(a,b,c));
        assertEquals(List.of(a),snapshot.schedulerOrder("minecraft:overworld",LegacyTickLedger.Kind.BLOCK));
        assertEquals(List.of(b),snapshot.schedulerOrder("minecraft:the_nether",LegacyTickLedger.Kind.BLOCK));
        assertEquals(List.of(c),snapshot.schedulerOrder("minecraft:overworld",LegacyTickLedger.Kind.FLUID));
    }
    @Test void duplicateSchedulerIdentityAndGlobalSequenceAreRejected() {
        var a=tick(0,0,1);
        var sameKey=entry(a.dimension(),a.kind(),a.x(),99,1,2);
        assertThrows(IllegalArgumentException.class,()->new LegacyTickLedger.Snapshot(3,List.of(a,sameKey)));
        var sameId=entry("minecraft:the_nether",a.kind(),55,0,0,1);
        assertThrows(IllegalArgumentException.class,()->new LegacyTickLedger.Snapshot(3,List.of(a,sameId)));
    }
    @Test void midTickQueuesFailClosed() {
        assertThrows(IllegalStateException.class,()->LegacyTickLedger.atBoundary(0,List.of(),1,0));
        assertThrows(IllegalStateException.class,()->LegacyTickLedger.atBoundary(0,List.of(),0,1));
        assertEquals(new LegacyTickLedger.Snapshot(0,List.of()),LegacyTickLedger.atBoundary(0,List.of(),0,0));
    }
    @Test void malformedCountIsRejectedBeforeAllocatingEntries() throws Exception {
        var bytes=new ByteArrayOutputStream(); var out=new DataOutputStream(bytes);
        new LegacyTickLedger.Snapshot(0,List.of()).write(out);
        var data=bytes.toByteArray(); java.nio.ByteBuffer.wrap(data).putInt(12,LegacyTickLedger.MAX_ENTRIES+1);
        assertThrows(IOException.class,()->LegacyTickLedger.Snapshot.read(new DataInputStream(new ByteArrayInputStream(data))));
    }
    @Test void inputIsImmutableAndReconstructionOrderIsGlobal() {
        var source=new ArrayList<>(List.of(tick(0,0,9),tick(0,0,2)));
        var cut=new LegacyTickLedger.Snapshot(11,source); source.clear();
        assertEquals(List.of(2L,9L),cut.entries().stream().map(LegacyTickLedger.Entry::sequence).toList());
        assertThrows(UnsupportedOperationException.class,()->cut.entries().clear());
    }
    @Test void streamingHashIsDeterministicButDetectsOrderTimeAndCounterChanges() {
        var a=tick(0,0,1); var b=tick(1,0,2);
        var cut=new LegacyTickLedger.Snapshot(5,List.of(a,b));
        assertEquals(cut.fingerprint(),new LegacyTickLedger.Snapshot(5,List.of(b,a)).fingerprint());
        assertNotEquals(cut.fingerprint(),new LegacyTickLedger.Snapshot(6,List.of(a,b)).fingerprint());
        assertNotEquals(cut.fingerprint(),new LegacyTickLedger.Snapshot(5,List.of(tick(2,0,1),b)).fingerprint());
        assertNotEquals(cut.fingerprint(),new LegacyTickLedger.Snapshot(5,List.of(tick(0,0,3),b)).fingerprint());
    }
    @Test void invalidPriorityCounterAndTruncatedPayloadFailClosed() throws Exception {
        assertThrows(IllegalArgumentException.class,()->tick(0,4,0));
        assertThrows(IllegalArgumentException.class,()->new LegacyTickLedger.Snapshot(1,List.of(tick(0,0,1))));
        var bytes=new ByteArrayOutputStream(); new LegacyTickLedger.Snapshot(5,List.of(tick(0,0,1))).write(new DataOutputStream(bytes));
        byte[] shortData=Arrays.copyOf(bytes.toByteArray(),bytes.size()-1);
        assertThrows(IOException.class,()->LegacyTickLedger.Snapshot.read(new DataInputStream(new ByteArrayInputStream(shortData))));
    }
}
