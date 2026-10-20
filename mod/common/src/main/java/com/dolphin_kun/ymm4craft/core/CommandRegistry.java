package com.dolphin_kun.ymm4craft.core;

import com.google.gson.JsonElement;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class CommandRegistry {
    @FunctionalInterface
    public interface Handler {
        CompletableFuture<JsonElement> handle(CommandParams params) throws CommandException;
    }

    private static final ConcurrentLinkedQueue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private static final Pattern NAME = Pattern.compile("[a-z]+\\.[a-zA-Z]+");

    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();

    public void register(String type, Handler handler) {
        if (!NAME.matcher(type).matches()) throw new IllegalArgumentException("コマンド名の形式が不正です: " + type);
        if (handlers.putIfAbsent(type, handler) != null) throw new IllegalStateException("登録済みです: " + type);
    }

    public Handler find(String type) {
        return handlers.get(type);
    }

    public Set<String> types() {
        return Set.copyOf(handlers.keySet());
    }

    public static <T> CompletableFuture<T> unwrapFailure(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && !(cause instanceof CommandException)) cause = cause.getCause();
        return CompletableFuture.failedFuture(cause);
    }

    public static <T> CompletableFuture<T> onMainThread(Supplier<T> work) {
        CompletableFuture<T> future = new CompletableFuture<>();
        pending.add(() -> {
            try {
                future.complete(work.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static void runPending() {
        for (Runnable task; (task = pending.poll()) != null; ) task.run();
    }

}
