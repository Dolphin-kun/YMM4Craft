package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TempWorlds {
    public static final String TEMP_DIR = "ymm4craft-tmp";

    private TempWorlds() {
    }

    public static Path tempRoot(Path gameDir) {
        return gameDir.resolve(TEMP_DIR);
    }

    public static void cleanupLeftovers(Path gameDir) {
        Path root = tempRoot(gameDir);
        try {
            deleteRecursively(root);
        } catch (IOException e) {
            Ymm4Craft.LOGGER.warn("一時ワールドの削除に失敗しました: {}", root, e);
        }
    }

    public static List<String> listWorlds(Path savesDir) throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(savesDir)) return names;
        try (Stream<Path> children = Files.list(savesDir)) {
            for (Path p : (Iterable<Path>) children::iterator) {
                String name = p.getFileName().toString();
                if (name.startsWith(".")) continue;
                if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(p.resolve("level.dat"))) {
                    names.add(name);
                }
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static void copyToTemp(Path savesDir, Path gameDir, String worldName) throws IOException {
        if (!listWorlds(savesDir).contains(worldName)) {
            throw new IOException("ワールドが見つかりません: " + worldName);
        }
        Path source = savesDir.resolve(worldName).normalize();
        Path root = tempRoot(gameDir);
        Path target = root.resolve(worldName).normalize();
        if (!source.getParent().equals(savesDir.normalize()) || !target.getParent().equals(root.normalize())) {
            throw new IOException("ワールド名が不正です: " + worldName);
        }

        deleteRecursively(root);
        Files.createDirectories(target);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || file.getFileName().toString().equals("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Files.copy(file, target.resolve(source.relativize(file).toString()));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        }
    }
}
