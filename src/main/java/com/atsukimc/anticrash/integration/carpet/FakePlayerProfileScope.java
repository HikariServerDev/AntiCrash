package com.atsukimc.anticrash.integration.carpet;

import com.mojang.authlib.GameProfile;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Supplies the checkpoint identity to Carpet's name-only creation API.
 *
 * <p>Carpet 1.4.69 createFake calls server.getProfileCache().get(name), then passes that profile to
 * the FakePlayer constructor and PlayerList.placeNewPlayer. Looking up today's cache can select a
 * different (online/offline) UUID from the checkpoint. The GameProfileCache mixin consults this
 * scope only for this cache, this name, and this thread. No cache maps or global authentication
 * settings are changed; Vanilla handles registration of the created player.
 */
public final class FakePlayerProfileScope implements AutoCloseable {
    private static final ThreadLocal<FakePlayerProfileScope> ACTIVE = new ThreadLocal<>();

    private final Object userCache;
    private final GameProfile profile;
    private final Object server;
    private final String name;
    private final Thread owner;
    private final FakePlayerProfileScope previous;
    private boolean closed;

    private FakePlayerProfileScope(Object userCache, Object server, UUID uuid, String name) {
        this.userCache =
                Objects.requireNonNull(userCache, "FakePlayer profile cache is unavailable");
        Objects.requireNonNull(uuid, "FakePlayer checkpoint UUID is missing");
        if (name == null || !name.matches("[\\x21-\\x7E]{1,16}")) {
            throw new IllegalArgumentException("Invalid FakePlayer checkpoint name");
        }
        this.profile = new GameProfile(uuid, name);
        this.server = server;
        this.name = name;
        this.owner = Thread.currentThread();
        this.previous = ACTIVE.get();
        ACTIVE.set(this);
    }

    public static FakePlayerProfileScope open(Object userCache, UUID uuid, String name) {
        return open(userCache, null, uuid, name);
    }

    public static FakePlayerProfileScope open(Object cache, Object server, UUID uuid, String name) {
        return new FakePlayerProfileScope(cache, server, uuid, name);
    }

    /** Only the Carpet factory invoked inside this scope may use the saved profile. */
    public static GameProfile creationProfile(Object server) {
        FakePlayerProfileScope scope = ACTIVE.get();
        return scope != null && server != null && scope.server == server ? scope.profile : null;
    }

    public static GameProfile creationProfileForName(String name) {
        FakePlayerProfileScope scope = ACTIVE.get();
        return scope != null && scope.server != null && scope.name.equalsIgnoreCase(name)
                ? scope.profile
                : null;
    }

    private static boolean savedCompletion(CompletableFuture<?> future) {
        FakePlayerProfileScope scope = ACTIVE.get();
        if (scope == null
                || scope.server == null
                || !future.isDone()
                || future.isCompletedExceptionally()
                || future.isCancelled()) return false;
        Object value = future.getNow(null);
        return value == scope.profile
                || value instanceof Optional<?> optional && optional.orElse(null) == scope.profile;
    }

    public static <T> CompletableFuture<Void> acceptCreation(
            CompletableFuture<T> future, Consumer<? super T> action) {
        return savedCompletion(future)
                ? acceptCreation(future, action, Runnable::run)
                : future.thenAccept(action);
    }

    public static <T> CompletableFuture<Void> acceptCreation(
            CompletableFuture<T> future, Consumer<? super T> action, Executor executor) {
        if (savedCompletion(future)) {
            var completed = future.thenAccept(action);
            completed.getNow(null); // Already completed, never wait for network/server work.
            return completed;
        }
        return future.thenAcceptAsync(action, executor);
    }

    /**
     * Modern Carpet normally schedules creation after fetching a profile. A restored profile needs
     * no network IO: finish ONLY that already-completed, identical profile on its owning server
     * thread, so validation/apply cannot race player registration. Unrelated/incomplete futures
     * retain Carpet's executor. No join, waiting, global executor or cache replacement.
     */
    public static <T> CompletableFuture<T> completeCreation(
            CompletableFuture<T> future,
            BiConsumer<? super T, ? super Throwable> action,
            Executor executor) {
        if (savedCompletion(future)) {
            var completed = future.whenComplete(action);
            completed.getNow(null); // Already done: propagate factory errors, never wait.
            return completed;
        }
        return future.whenCompleteAsync(action, executor);
    }

    /** Null leaves the normal GameProfileCache lookup completely unchanged. */
    public static GameProfile lookup(Object userCache, String name) {
        FakePlayerProfileScope scope = ACTIVE.get();
        return scope != null && scope.userCache == userCache && scope.name.equalsIgnoreCase(name)
                ? scope.profile
                : null;
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("FakePlayer profile scope closed on another thread");
        }
        if (closed) return;
        if (ACTIVE.get() != this) {
            throw new IllegalStateException("FakePlayer profile scopes closed out of order");
        }
        if (previous == null) ACTIVE.remove();
        else ACTIVE.set(previous);
        closed = true;
    }
}
