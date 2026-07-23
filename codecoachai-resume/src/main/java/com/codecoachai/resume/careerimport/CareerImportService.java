package com.codecoachai.resume.careerimport;

import com.codecoachai.resume.careerimport.CareerImportModels.ImportPreview;
import com.codecoachai.resume.careerimport.CareerImportModels.ImportResult;
import java.util.Map;

public interface CareerImportService {

    default ImportPreview previewCsv(String filename, byte[] content, String timezone) {
        return previewCsv(filename, content, timezone, Map.of());
    }

    ImportPreview previewCsv(String filename, byte[] content, String timezone, Map<String, String> mapping);

    default ImportResult importCsv(String filename, byte[] content, String timezone, String duplicatePolicy) {
        return importCsv(filename, content, timezone, duplicatePolicy, Map.of());
    }

    ImportResult importCsv(String filename, byte[] content, String timezone, String duplicatePolicy,
                           Map<String, String> mapping);

    ImportPreview previewIcs(String filename, byte[] content, String timezone);

    ImportResult importIcs(String filename, byte[] content, String timezone);

    byte[] exportErrorRowsCsv(Long batchId);
}
