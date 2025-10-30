package com.ke.bella.batch.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class FileUtilsTest {

    private Path tempDir;
    private Path testFile;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("fileutils-test");
        testFile = tempDir.resolve("test.txt");
    }

    @After
    public void tearDown() throws IOException {
        if(tempDir != null && Files.exists(tempDir)) {
            FileUtils.removeAll(tempDir);
        }
    }

    @Test
    public void testProcessLines_BasicProcessing() throws IOException {
        // Create test file
        List<String> lines = Arrays.asList("line1", "line2", "line3", "line4", "line5");
        Files.write(testFile, lines, StandardCharsets.UTF_8);

        List<String> processedLines = new ArrayList<>();
        FileUtils.processLines(testFile.toString(), 0, processedLines::add);

        assertEquals(lines, processedLines);
    }

    @Test
    public void testProcessLines_SkipLines() throws IOException {
        // Create test file
        List<String> lines = Arrays.asList("skip1", "skip2", "process1", "process2", "process3");
        Files.write(testFile, lines, StandardCharsets.UTF_8);

        List<String> processedLines = new ArrayList<>();
        FileUtils.processLines(testFile.toString(), 2, processedLines::add);

        List<String> expected = Arrays.asList("process1", "process2", "process3");
        assertEquals(expected, processedLines);
    }

    @Test
    public void testProcessLines_EmptyFile() throws IOException {
        // Create empty file
        Files.createFile(testFile);

        AtomicInteger processedCount = new AtomicInteger(0);
        FileUtils.processLines(testFile.toString(), 0, line -> processedCount.incrementAndGet());

        assertEquals(0, processedCount.get());
    }

    @Test
    public void testProcessLines_SkipAllLines() throws IOException {
        List<String> lines = Arrays.asList("line1", "line2", "line3");
        Files.write(testFile, lines, StandardCharsets.UTF_8);

        AtomicInteger processedCount = new AtomicInteger(0);
        FileUtils.processLines(testFile.toString(), 5, line -> processedCount.incrementAndGet());

        assertEquals(0, processedCount.get());
    }

    @Test
    public void testProcessLines_LineProcessor() throws IOException {
        List<String> lines = Arrays.asList("apple", "banana", "cherry");
        Files.write(testFile, lines, StandardCharsets.UTF_8);

        AtomicReference<String> longestLine = new AtomicReference<>();
        FileUtils.processLines(testFile.toString(), 0, line -> {
            if(longestLine.get() == null || line.length() > longestLine.get().length()) {
                longestLine.set(line);
            }
        });

        assertEquals("banana", longestLine.get());
    }

    @Test
    public void testMergeFiles_Success() throws IOException {
        // Create test files
        Path file1 = tempDir.resolve("output_1.jsonl");
        Path file2 = tempDir.resolve("output_2.jsonl");
        Path file3 = tempDir.resolve("other.txt");

        Files.write(file1, Arrays.asList("line1", "line2"), StandardCharsets.UTF_8);
        Files.write(file2, Arrays.asList("line3", "line4"), StandardCharsets.UTF_8);
        Files.write(file3, Arrays.asList("ignored"), StandardCharsets.UTF_8);

        Path mergedFile = tempDir.resolve("merged.jsonl");
        Path result = FileUtils.mergeFiles(tempDir, "output_*.jsonl", mergedFile);

        assertNotNull(result);
        assertEquals(mergedFile, result);
        assertTrue(Files.exists(mergedFile));

        List<String> mergedContent = Files.readAllLines(mergedFile, StandardCharsets.UTF_8);
        List<String> expected = Arrays.asList("line1", "line2", "line3", "line4");
        assertEquals(expected, mergedContent);
    }

    @Test
    public void testMergeFiles_NoMatchingFiles() throws IOException {
        Path mergedFile = tempDir.resolve("merged.jsonl");
        Path result = FileUtils.mergeFiles(tempDir, "nonexistent_*.txt", mergedFile);

        assertNull(result);
        assertFalse(Files.exists(mergedFile));
    }

    @Test
    public void testMergeFiles_EmptyMatchingFiles() throws IOException {
        // Create empty files
        Path file1 = tempDir.resolve("empty_1.txt");
        Path file2 = tempDir.resolve("empty_2.txt");
        Files.createFile(file1);
        Files.createFile(file2);

        Path mergedFile = tempDir.resolve("merged.txt");
        Path result = FileUtils.mergeFiles(tempDir, "empty_*.txt", mergedFile);

        assertNull(result); // Should return null for empty merged file
    }

    @Test
    public void testMergeFiles_CreatesParentDirectories() throws IOException {
        Path file1 = tempDir.resolve("test_1.txt");
        Files.write(file1, Arrays.asList("content"), StandardCharsets.UTF_8);

        Path nestedDir = tempDir.resolve("nested").resolve("deep");
        Path mergedFile = nestedDir.resolve("merged.txt");

        Path result = FileUtils.mergeFiles(tempDir, "test_*.txt", mergedFile);

        assertNotNull(result);
        assertTrue(Files.exists(mergedFile));
        assertTrue(Files.exists(nestedDir));
    }

    @Test
    public void testRemoveAll_Directory() throws IOException {
        // Create nested directory structure
        Path subDir = tempDir.resolve("subdir");
        Path subFile = subDir.resolve("file.txt");
        Files.createDirectories(subDir);
        Files.write(subFile, Arrays.asList("content"), StandardCharsets.UTF_8);

        assertTrue(Files.exists(tempDir));
        assertTrue(Files.exists(subDir));
        assertTrue(Files.exists(subFile));

        FileUtils.removeAll(tempDir);

        assertFalse(Files.exists(tempDir));
        assertFalse(Files.exists(subDir));
        assertFalse(Files.exists(subFile));

        // Reset tempDir to null to prevent tearDown from trying to delete it again
        tempDir = null;
    }

    @Test
    public void testRemoveAll_NonExistentDirectory() {
        Path nonExistent = Paths.get("/tmp/nonexistent-" + System.currentTimeMillis());

        // Should not throw exception
        FileUtils.removeAll(nonExistent);
        FileUtils.removeAll(null);
    }

    @Test
    public void testRemoveAll_SingleFile() throws IOException {
        Files.write(testFile, Arrays.asList("content"), StandardCharsets.UTF_8);
        assertTrue(Files.exists(testFile));

        FileUtils.removeAll(testFile);

        assertFalse(Files.exists(testFile));
    }

    @Test
    public void testFileWriter_WriteToFile() throws IOException {
        List<String> contents = Arrays.asList("line1", "line2", "line3");
        String filePath = tempDir.resolve("writer-test.txt").toString();

        FileUtils.FileWriter.writeToFile(filePath, contents);

        Path writtenFile = Paths.get(filePath);
        assertTrue(Files.exists(writtenFile));

        List<String> readContent = Files.readAllLines(writtenFile, StandardCharsets.UTF_8);
        assertEquals(contents, readContent);
    }

    @Test
    public void testFileWriter_AppendToFile() throws IOException {
        String filePath = tempDir.resolve("append-test.txt").toString();

        List<String> firstBatch = Arrays.asList("line1", "line2");
        List<String> secondBatch = Arrays.asList("line3", "line4");

        FileUtils.FileWriter.writeToFile(filePath, firstBatch);
        FileUtils.FileWriter.writeToFile(filePath, secondBatch);

        Path writtenFile = Paths.get(filePath);
        List<String> readContent = Files.readAllLines(writtenFile, StandardCharsets.UTF_8);

        List<String> expected = Arrays.asList("line1", "line2", "line3", "line4");
        assertEquals(expected, readContent);
    }

    @Test
    public void testFileWriter_NullContents() {
        String filePath = tempDir.resolve("null-test.txt").toString();

        // Should not throw exception and should not create file
        FileUtils.FileWriter.writeToFile(filePath, null);

        assertFalse(Files.exists(Paths.get(filePath)));
    }

    @Test
    public void testFileWriter_EmptyContents() {
        String filePath = tempDir.resolve("empty-test.txt").toString();

        // Should not throw exception and should not create file
        FileUtils.FileWriter.writeToFile(filePath, new ArrayList<>());

        assertFalse(Files.exists(Paths.get(filePath)));
    }

    @Test
    public void testFileWriter_FilterNullLines() throws IOException {
        List<String> contents = Arrays.asList("line1", null, "line2", null, "line3");
        String filePath = tempDir.resolve("filter-test.txt").toString();

        FileUtils.FileWriter.writeToFile(filePath, contents);

        Path writtenFile = Paths.get(filePath);
        List<String> readContent = Files.readAllLines(writtenFile, StandardCharsets.UTF_8);

        List<String> expected = Arrays.asList("line1", "line2", "line3");
        assertEquals(expected, readContent);
    }

    @Test
    public void testFileWriter_CreatesParentDirectories() throws IOException {
        Path nestedDir = tempDir.resolve("nested").resolve("deep");
        String filePath = nestedDir.resolve("nested-file.txt").toString();

        List<String> contents = Arrays.asList("nested content");
        FileUtils.FileWriter.writeToFile(filePath, contents);

        Path writtenFile = Paths.get(filePath);
        assertTrue(Files.exists(writtenFile));
        assertTrue(Files.exists(nestedDir));

        List<String> readContent = Files.readAllLines(writtenFile, StandardCharsets.UTF_8);
        assertEquals(contents, readContent);
    }

    @Test
    public void testFileWriter_ConcurrentAccess() throws InterruptedException {
        String filePath = tempDir.resolve("concurrent-test.txt").toString();
        int threadCount = 10;
        int linesPerThread = 10;

        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                List<String> contents = new ArrayList<>();
                for (int j = 0; j < linesPerThread; j++) {
                    contents.add("Thread-" + threadId + "-Line-" + j);
                }
                FileUtils.FileWriter.writeToFile(filePath, contents);
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify file exists and has expected number of lines
        Path writtenFile = Paths.get(filePath);
        assertTrue(Files.exists(writtenFile));

        try {
            List<String> readContent = Files.readAllLines(writtenFile, StandardCharsets.UTF_8);
            assertEquals(threadCount * linesPerThread, readContent.size());
        } catch (IOException e) {
            fail("Failed to read concurrent file: " + e.getMessage());
        }
    }
}
