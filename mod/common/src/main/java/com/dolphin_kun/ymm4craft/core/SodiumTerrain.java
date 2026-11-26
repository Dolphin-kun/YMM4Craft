package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

final class SodiumTerrain {
    private static final String RENDERER = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer";

    private static Boolean present;
    private static boolean broken;
    private static Method instance;
    private static Method visibleChunks;
    private static Field manager;
    private static Field builder;
    private static Method scheduledJobs;
    private static Method busyThreads;
    private static Method needsUpdate;
    private static Field pendingTask;
    private static Field buildResults;
    private static Field taskLists;
    private static Field importantTasks;
    private static Field[] submittedTasks;

    private SodiumTerrain() {
    }

    static boolean present() {
        if (present == null) {
            try {
                Class.forName(RENDERER, false, SodiumTerrain.class.getClassLoader());
                present = true;
            } catch (ClassNotFoundException | LinkageError e) {
                present = false;
            }
        }
        return present;
    }

    static TerrainState current() {
        if (broken) return null;
        try {
            if (instance == null) bind();
            Object renderer = instance.invoke(null);
            if (renderer == null) return new TerrainState(true, -1);
            Object sections = manager.get(renderer);
            if (sections == null) return new TerrainState(true, -1);
            Object chunkBuilder = builder.get(sections);
            boolean busy = (int) scheduledJobs.invoke(chunkBuilder) > 0
                    || (int) busyThreads.invoke(chunkBuilder) > 0
                    || (boolean) needsUpdate.invoke(sections)
                    || pendingTask.get(sections) != null
                    || !((Collection<?>) buildResults.get(sections)).isEmpty()
                    || notEmpty(taskLists.get(sections));
            for (Object tasks : ((Map<?, ?>) importantTasks.get(sections)).values()) busy |= notEmpty(tasks);
            for (Field f : submittedTasks) busy |= f.getInt(sections) > 0;
            return new TerrainState(busy, (int) visibleChunks.invoke(renderer));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            Ymm4Craft.LOGGER.warn("Sodium の描画の状態を読めないので、時間で描き終わりを判断します", e);
            return null;
        }
    }

    private static boolean notEmpty(Object collection) throws ReflectiveOperationException {
        if (collection == null) return false;
        if (collection instanceof Collection<?> c) return !c.isEmpty();
        return !(boolean) collection.getClass().getMethod("isEmpty").invoke(collection);
    }

    private static void bind() throws ReflectiveOperationException {
        Class<?> rendererClass = Class.forName(RENDERER);
        Method instanceMethod = rendererClass.getMethod("instanceNullable");
        Method visible = rendererClass.getMethod("getVisibleChunkCount");
        Field managerField = accessible(rendererClass.getDeclaredField("renderSectionManager"));
        Class<?> managerClass = managerField.getType();
        Field builderField = accessible(managerClass.getDeclaredField("builder"));
        Class<?> builderClass = builderField.getType();
        scheduledJobs = builderClass.getMethod("getScheduledJobCount");
        busyThreads = builderClass.getMethod("getBusyThreadCount");
        needsUpdate = managerClass.getMethod("needsUpdate");
        pendingTask = accessible(managerClass.getDeclaredField("pendingTask"));
        buildResults = accessible(managerClass.getDeclaredField("buildResults"));
        taskLists = accessible(managerClass.getDeclaredField("taskLists"));
        importantTasks = accessible(managerClass.getDeclaredField("importantTasks"));
        submittedTasks = new Field[]{
                accessible(managerClass.getDeclaredField("thisFrameBlockingTasks")),
                accessible(managerClass.getDeclaredField("nextFrameBlockingTasks")),
                accessible(managerClass.getDeclaredField("deferredTasks")),
        };
        manager = managerField;
        builder = builderField;
        visibleChunks = visible;
        instance = instanceMethod;
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }
}
