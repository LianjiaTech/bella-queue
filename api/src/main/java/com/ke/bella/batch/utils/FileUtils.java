package com.ke.bella.batch.utils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("all")
public class FileUtils {

    @SneakyThrows
    public static void processLines(String fileName, long skipLines, Consumer<String> lineProcessor) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(fileName), StandardCharsets.UTF_8)) {
            String line;
            long currentLine = 0;

            while ((line = reader.readLine()) != null) {
                currentLine++;
                if(currentLine <= skipLines) {
                    continue;
                }
                lineProcessor.accept(line);
            }
        }
    }

    @SneakyThrows
    public static Path mergeFiles(Path dir, String pattern, Path mergedFile) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        try (var paths = Files.walk(dir, 1)) {
            List<Path> matchedFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matcher.matches(path.getFileName()))
                    .sorted()
                    .collect(Collectors.toList());

            if(matchedFiles.isEmpty()) {
                return null;
            }

            Files.createDirectories(mergedFile.getParent());

            try (var output = Files.newOutputStream(mergedFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Path file : matchedFiles) {
                    try (var input = Files.newInputStream(file)) {
                        input.transferTo(output);
                    }
                }
            }
        }

        return Files.size(mergedFile) > 0 ? mergedFile : null;
    }

    @SneakyThrows
    public static void removeAll(Path dir) {
        if(dir == null || !Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static class FileWriter {

        private static final ConcurrentHashMap<String, Object> fileLocks = new ConcurrentHashMap<>();

        @SneakyThrows
        public static void writeToFile(String filePath, List<String> contents) {
            if(contents == null || contents.isEmpty()) {
                return;
            }

            Object lock = fileLocks.computeIfAbsent(filePath, k -> new Object());
            synchronized(lock) {
                Path path = Paths.get(filePath);
                Files.createDirectories(path.getParent());

                List<String> filteredContents = contents.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                Files.write(path, filteredContents, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
    }
}
