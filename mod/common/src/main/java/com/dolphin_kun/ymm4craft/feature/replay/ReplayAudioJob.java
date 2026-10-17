package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

record ReplayAudioJob(int after, int last, double from, double[][] camera, String listenerName, Path out, boolean exclusive,
                      CompletableFuture<JsonObject> future, long startedNanos) {
}
