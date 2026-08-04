package com.codecoachai.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalFeignClientInvocationContractTest {

    private static final Pattern RESULT_METHOD =
            Pattern.compile("public\\s+Result<");
    private static final Pattern MAPPED_RESULT_METHOD = Pattern.compile(
            "public\\s+Result<[^\\{]+\\{\\s*return\\s+resultMapper\\.invoke\\(",
            Pattern.DOTALL);
    private static final Pattern CONTROLLER_DEPENDENCY = Pattern.compile(
            "private\\s+final\\s+([\\w.]+Controller)\\s+\\w+\\s*;");

    @Test
    void everyResultReturningLocalFeignMethodUsesTheBusinessFailureBoundary() throws IOException {
        List<Path> clients = localFeignClients();
        assertFalse(clients.isEmpty());

        for (Path client : clients) {
            String source = Files.readString(client);
            assertEquals(
                    count(RESULT_METHOD, source),
                    count(MAPPED_RESULT_METHOD, source),
                    client.getFileName() + " has a Result method outside LocalResultMapper.invoke");
        }
    }

    @Test
    void onlyReviewedControllerCompatibilityBridgesRemain() throws IOException {
        Set<String> actual = new LinkedHashSet<>();
        for (Path client : localFeignClients()) {
            Matcher matcher = CONTROLLER_DEPENDENCY.matcher(Files.readString(client));
            while (matcher.find()) {
                actual.add(client.getFileName() + ":" + matcher.group(1));
            }
        }

        assertEquals(Set.of(
                "LocalFileFeignClient.java:InnerFileController",
                "LocalInterviewEvidenceFeignClient.java:InnerInterviewReportController",
                "LocalTaskInterviewFeignClient.java:InnerInterviewReportController",
                "LocalTaskQuestionFeignClient.java:InnerQuestionController",
                "LocalTaskResumeFeignClient.java:InnerResumeAnalysisController"), actual);
    }

    private static List<Path> localFeignClients() throws IOException {
        Path localPackage = findModuleRoot()
                .resolve("src/main/java/com/codecoachai/core/local");
        try (var files = Files.list(localPackage)) {
            return files
                    .filter(path -> path.getFileName().toString().matches("Local.*FeignClient\\.java"))
                    .sorted()
                    .toList();
        }
    }

    private static int count(Pattern pattern, String source) {
        int count = 0;
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Path findModuleRoot() {
        Path candidate = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("src/main/java/com/codecoachai/core/local"))) {
                return candidate;
            }
            Path coreModule = candidate.resolve("codecoachai-core");
            if (Files.isRegularFile(coreModule.resolve("pom.xml"))
                    && Files.isDirectory(coreModule.resolve("src/main/java/com/codecoachai/core/local"))) {
                return coreModule;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate codecoachai-core module root");
    }
}
