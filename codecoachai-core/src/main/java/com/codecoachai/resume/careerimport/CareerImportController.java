package com.codecoachai.resume.careerimport;

import com.codecoachai.common.core.domain.Result;
import com.codecoachai.common.core.enums.ErrorCode;
import com.codecoachai.common.core.exception.BusinessException;
import com.codecoachai.common.security.util.SecurityAssert;
import com.codecoachai.common.web.log.OperationLog;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportPreview;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/career-imports")
public class CareerImportController {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final CareerImportService careerImportService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportPreview> previewCsv(@RequestPart("file") MultipartFile file,
                                            @RequestParam String timezone,
                                            @RequestParam(required = false) String mapping) throws IOException {
        SecurityAssert.requireLoginUserId();
        validateFileSize(file);
        return Result.success(careerImportService.previewCsv(
                file.getOriginalFilename(), file.getBytes(), timezone, parseMapping(mapping)));
    }

    @OperationLog(module = "career-import", action = "IMPORT_CSV",
            description = "Import application CRM rows with partial success", logArgs = false, logResponse = false)
    @PostMapping(value = "/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importCsv(@RequestPart("file") MultipartFile file,
                                          @RequestParam String timezone,
                                          @RequestParam(required = false, defaultValue = "SKIP")
                                          String duplicatePolicy,
                                          @RequestParam(required = false) String mapping) throws IOException {
        SecurityAssert.requireLoginUserId();
        validateFileSize(file);
        return Result.success(careerImportService.importCsv(
                file.getOriginalFilename(), file.getBytes(), timezone, duplicatePolicy, parseMapping(mapping)));
    }

    @PostMapping(value = "/ics/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportPreview> previewIcs(@RequestPart("file") MultipartFile file,
                                            @RequestParam String timezone) throws IOException {
        SecurityAssert.requireLoginUserId();
        validateFileSize(file);
        return Result.success(careerImportService.previewIcs(
                file.getOriginalFilename(), file.getBytes(), timezone));
    }

    @OperationLog(module = "career-import", action = "IMPORT_ICS",
            description = "Import CRM calendar ICS events", logArgs = false, logResponse = false)
    @PostMapping(value = "/ics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImportResult> importIcs(@RequestPart("file") MultipartFile file,
                                          @RequestParam String timezone) throws IOException {
        SecurityAssert.requireLoginUserId();
        validateFileSize(file);
        return Result.success(careerImportService.importIcs(
                file.getOriginalFilename(), file.getBytes(), timezone));
    }

    @GetMapping("/{batchId}/errors.csv")
    public void exportErrors(@PathVariable Long batchId, HttpServletResponse response) throws IOException {
        SecurityAssert.requireLoginUserId();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment;filename=career-import-" + batchId + "-errors.csv");
        response.getOutputStream().write(careerImportService.exportErrorRowsCsv(batchId));
    }

    private void validateFileSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Import file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Import file cannot exceed 2 MB");
        }
    }

    private Map<String, String> parseMapping(String mapping) {
        if (mapping == null || mapping.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(mapping, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "mapping must be a JSON object");
        }
    }
}
