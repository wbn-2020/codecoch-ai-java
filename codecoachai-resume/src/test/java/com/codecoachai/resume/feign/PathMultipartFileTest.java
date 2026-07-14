package com.codecoachai.resume.feign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathMultipartFileTest {

    @TempDir
    Path tempDir;

    @Test
    void getBytesAllowsContentExactlyAtConfiguredLimit() throws Exception {
        byte[] content = new byte[]{1, 2, 3, 4};
        Path path = Files.write(tempDir.resolve("exact.bin"), content);
        Object fileKey = attributes(path).fileKey();
        PathMultipartFile file = new PathMultipartFile(path, "resume.bin", "application/octet-stream", 4, fileKey);

        assertArrayEquals(content, file.getBytes());
    }

    @Test
    void getBytesRejectsFileThatGrowsPastConfiguredLimit() throws Exception {
        Path path = Files.write(tempDir.resolve("growing.bin"), new byte[]{1, 2, 3, 4});
        Object fileKey = attributes(path).fileKey();
        PathMultipartFile file = new PathMultipartFile(path, "resume.bin", "application/octet-stream", 4, fileKey);
        Files.write(path, new byte[]{5}, StandardOpenOption.APPEND);

        assertThrows(IOException.class, file::getBytes);
    }

    @Test
    void getBytesRejectsPathReplacementWhenFileKeyIsAvailable() throws Exception {
        Path path = Files.write(tempDir.resolve("replaced.bin"), new byte[]{1, 2, 3});
        Object originalFileKey = attributes(path).fileKey();
        PathMultipartFile file = new PathMultipartFile(
                path, "resume.bin", "application/octet-stream", 4, originalFileKey);
        Files.delete(path);
        Files.write(path, originalFileKey == null
                ? new byte[]{4, 5, 6, 7, 8}
                : new byte[]{4, 5, 6});
        Object replacementFileKey = attributes(path).fileKey();
        if (originalFileKey != null) {
            Assumptions.assumeTrue(
                    replacementFileKey != null && !originalFileKey.equals(replacementFileKey),
                    "Filesystem reused the original file key");
        }

        assertThrows(IOException.class, file::getBytes);
    }

    @Test
    void byteAndStreamReadsRejectSymbolicLinks() throws Exception {
        Path target = Files.write(tempDir.resolve("target.bin"), new byte[]{1});
        Path link = tempDir.resolve("link.bin");
        createSymbolicLinkOrSkip(link, target);
        PathMultipartFile file = new PathMultipartFile(
                link, "resume.bin", "application/octet-stream", 4, null);

        assertThrows(IOException.class, file::getBytes);
        assertThrows(IOException.class, file::getInputStream);
    }

    private BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | SecurityException ex) {
            Assumptions.abort("Symbolic links are not supported: " + ex.getClass().getSimpleName());
        } catch (IOException ex) {
            Assumptions.abort("Symbolic link creation is unavailable: " + ex.getClass().getSimpleName());
        }
    }
}
