package com.atsukimc.anticrash.integration.carpet;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.authlib.GameProfile;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FakePlayerProfileScopeTest {
    private static final UUID CHECKPOINT_ID =
            UUID.fromString("bf9cc43e-16d8-4013-a315-566ddd8ce475");
    private static final UUID OTHER_ID = UUID.fromString("35a55c21-64ef-3102-a685-03f0eddc1503");

    @Test
    void ordinaryLookupsAreNotOverridden() {
        assertNull(FakePlayerProfileScope.lookup(new Object(), "msbot"));
    }

    @Test
    void checkpointIdentityWinsWithoutEditingTheExistingProfile() {
        Object cache = new Object();
        GameProfile cached = new GameProfile(OTHER_ID, "msbot");
        try (var scope = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
            GameProfile selected = FakePlayerProfileScope.lookup(cache, "msbot");
            assertNotNull(selected);

            //? if <=1.21.8 {
            /*assertEquals(CHECKPOINT_ID, selected.getId());
            assertEquals("msbot", selected.getName());
            assertEquals(OTHER_ID, cached.getId());
            *///?} else {
            assertEquals(CHECKPOINT_ID, selected.id());
            assertEquals("msbot", selected.name());
            assertEquals(OTHER_ID, cached.id());
            //?}

        }
        assertNull(FakePlayerProfileScope.lookup(cache, "msbot"));
    }

    @Test
    void cacheAndNameMustBothMatch() {
        Object cache = new Object();
        try (var scope = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "MsBot")) {

            //? if <=1.21.8 {
            /*assertEquals("MsBot", FakePlayerProfileScope.lookup(cache, "msbot").getName());
            *///?} else {
            assertEquals("MsBot", FakePlayerProfileScope.lookup(cache, "msbot").name());
            //?}

            assertNull(FakePlayerProfileScope.lookup(new Object(), "MsBot"));
            assertNull(FakePlayerProfileScope.lookup(cache, "another_player"));
            assertNull(FakePlayerProfileScope.lookup(cache, null));
        }
    }

    @Test
    void networkAndWorkerThreadsCannotSeeTheOverride() throws Exception {
        Object cache = new Object();
        try (var scope = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
            assertNull(
                    CompletableFuture.supplyAsync(
                                    () -> FakePlayerProfileScope.lookup(cache, "msbot"))
                            .get(5, TimeUnit.SECONDS));

            //? if <=1.21.8 {
            /*assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").getId());
            *///?} else {
            assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").id());
            //?}

        }
    }

    @Test
    void factoryFailureCannotLeakAnIdentityIntoLaterLogins() {
        Object cache = new Object();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    try (var scope = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
                        throw new IllegalStateException("factory failed");
                    }
                });
        assertNull(FakePlayerProfileScope.lookup(cache, "msbot"));
    }

    @Test
    void nestedScopesRestoreTheOuterIdentity() {
        Object cache = new Object();
        try (var outer = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
            try (var inner = FakePlayerProfileScope.open(cache, OTHER_ID, "other_bot")) {
                assertNull(FakePlayerProfileScope.lookup(cache, "msbot"));

                //? if <=1.21.8 {
                /*assertEquals(OTHER_ID, FakePlayerProfileScope.lookup(cache, "other_bot").getId());
                *///?} else {
                assertEquals(OTHER_ID, FakePlayerProfileScope.lookup(cache, "other_bot").id());
                //?}

            }

            //? if <=1.21.8 {
            /*assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").getId());
            *///?} else {
            assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").id());
            //?}

        }
        assertNull(FakePlayerProfileScope.lookup(cache, "msbot"));
    }

    @Test
    void invalidScopesDoNotReplaceAnActiveScope() {
        Object cache = new Object();
        try (var outer = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
            assertThrows(
                    NullPointerException.class,
                    () -> FakePlayerProfileScope.open(null, OTHER_ID, "other"));
            assertThrows(
                    NullPointerException.class,
                    () -> FakePlayerProfileScope.open(cache, null, "other"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> FakePlayerProfileScope.open(cache, OTHER_ID, "bad name"));

            //? if <=1.21.8 {
            /*assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").getId());
            *///?} else {
            assertEquals(CHECKPOINT_ID, FakePlayerProfileScope.lookup(cache, "msbot").id());
            //?}

        }
    }

    @Test
    void scopesRejectCrossThreadAndOutOfOrderClose() throws Exception {
        Object cache = new Object();
        try (var outer = FakePlayerProfileScope.open(cache, CHECKPOINT_ID, "msbot")) {
            CompletableFuture.runAsync(
                            () -> assertThrows(IllegalStateException.class, outer::close))
                    .get(5, TimeUnit.SECONDS);
            try (var inner = FakePlayerProfileScope.open(cache, OTHER_ID, "other_bot")) {
                assertThrows(IllegalStateException.class, outer::close);

                //? if <=1.21.8 {
                /*assertEquals(OTHER_ID, FakePlayerProfileScope.lookup(cache, "other_bot").getId());
                *///?} else {
                assertEquals(OTHER_ID, FakePlayerProfileScope.lookup(cache, "other_bot").id());
                //?}

            }
            outer.close();
            outer.close();
            assertNull(FakePlayerProfileScope.lookup(cache, "msbot"));
        }
    }

    @Test
    void modernFactoryUsesOnlyItsOwnServerAndCompletesBeforeValidation() {
        Object cache = new Object(), server = new Object();
        List<Runnable> queued = new ArrayList<>();
        AtomicReference<GameProfile> created = new AtomicReference<>();
        try (var scope = FakePlayerProfileScope.open(cache, server, CHECKPOINT_ID, "msbot")) {
            GameProfile saved = FakePlayerProfileScope.creationProfile(server);
            assertNotNull(saved);
            assertNull(FakePlayerProfileScope.creationProfile(new Object()));
            FakePlayerProfileScope.completeCreation(
                    CompletableFuture.completedFuture(saved),
                    (profile, error) -> created.set(profile),
                    queued::add);
            assertSame(saved, created.get());
            assertTrue(queued.isEmpty());
        }
        assertNull(FakePlayerProfileScope.creationProfile(server));
    }

    @Test
    void ordinaryAndUnrelatedFactoryCompletionsKeepTheirExecutor() {
        List<Runnable> queued = new ArrayList<>();
        var called = new AtomicInteger();
        GameProfile ordinary = new GameProfile(OTHER_ID, "other");
        FakePlayerProfileScope.completeCreation(
                CompletableFuture.completedFuture(ordinary),
                (profile, error) -> called.incrementAndGet(),
                queued::add);
        try (var scope =
                FakePlayerProfileScope.open(new Object(), new Object(), CHECKPOINT_ID, "msbot")) {
            FakePlayerProfileScope.completeCreation(
                    CompletableFuture.completedFuture(ordinary),
                    (profile, error) -> called.incrementAndGet(),
                    queued::add);
        }
        assertEquals(0, called.get());
        assertEquals(2, queued.size());
        queued.forEach(Runnable::run);
        assertEquals(2, called.get());
    }

    @Test
    void incompleteAndFailedFuturesAreNeverWaitedOnOrRunInline() {
        Object server = new Object();
        List<Runnable> queued = new ArrayList<>();
        CompletableFuture<GameProfile> pending = new CompletableFuture<>();
        var called = new AtomicInteger();
        try (var scope =
                FakePlayerProfileScope.open(new Object(), server, CHECKPOINT_ID, "msbot")) {
            FakePlayerProfileScope.completeCreation(
                    pending, (profile, error) -> called.incrementAndGet(), queued::add);
            FakePlayerProfileScope.completeCreation(
                    CompletableFuture.failedFuture(new Exception("fetch failed")),
                    (profile, error) -> called.incrementAndGet(),
                    queued::add);
            assertEquals(0, called.get());
            assertFalse(pending.isDone());
            pending.complete(FakePlayerProfileScope.creationProfile(server));
        }
        assertEquals(2, queued.size());
        queued.forEach(Runnable::run);
        assertEquals(2, called.get());
    }

    @Test
    void restoredFactoryFailureIsPropagatedInsteadOfBeingReportedReady() {
        Object server = new Object();
        try (var scope =
                FakePlayerProfileScope.open(new Object(), server, CHECKPOINT_ID, "msbot")) {
            var saved = FakePlayerProfileScope.creationProfile(server);
            assertThrows(
                    CompletionException.class,
                    () ->
                            FakePlayerProfileScope.completeCreation(
                                    CompletableFuture.completedFuture(saved),
                                    (profile, error) -> {
                                        throw new IllegalStateException("registration failed");
                                    },
                                    task -> fail("Saved profile must not be queued")));
        }
        assertNull(FakePlayerProfileScope.creationProfile(server));
    }

    @Test
    void optionalProfilesPreserveIdentityAndFinishOnTheOwningThread() {
        Object server = new Object();
        var applied = new AtomicInteger();
        try (var scope =
                FakePlayerProfileScope.open(new Object(), server, CHECKPOINT_ID, "msbot")) {
            var saved = FakePlayerProfileScope.creationProfileForName("MSBOT");
            assertNotNull(saved);
            assertNull(FakePlayerProfileScope.creationProfileForName("another_player"));
            var future = CompletableFuture.completedFuture(Optional.of(saved));
            FakePlayerProfileScope.acceptCreation(
                    future,
                    profile -> {
                        assertSame(saved, profile.orElseThrow());
                        applied.incrementAndGet();
                    },
                    task -> fail("Saved profile must not be queued"));
            FakePlayerProfileScope.completeCreation(
                    future,
                    (profile, error) -> applied.incrementAndGet(),
                    task -> fail("Saved profile must not be queued"));
            FakePlayerProfileScope.acceptCreation(future, profile -> applied.incrementAndGet());
            assertEquals(3, applied.get());
        }
        assertNull(FakePlayerProfileScope.creationProfileForName("msbot"));
    }

    @Test
    void optionalUnrelatedProfilesAndFailedConsumersKeepSafetyBoundaries() {
        Object server = new Object();
        List<Runnable> queued = new ArrayList<>();
        var called = new AtomicInteger();
        try (var scope =
                FakePlayerProfileScope.open(new Object(), server, CHECKPOINT_ID, "msbot")) {
            FakePlayerProfileScope.acceptCreation(
                    CompletableFuture.completedFuture(Optional.empty()),
                    ignored -> called.incrementAndGet(),
                    queued::add);
            assertEquals(0, called.get());
            assertThrows(
                    CompletionException.class,
                    () ->
                            FakePlayerProfileScope.acceptCreation(
                                    CompletableFuture.completedFuture(
                                            Optional.of(
                                                    FakePlayerProfileScope.creationProfile(
                                                            server))),
                                    profile -> {
                                        throw new IllegalStateException("registration failed");
                                    },
                                    queued::add));
        }
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(1, called.get());
    }
}
