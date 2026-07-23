package com.codecoachai.resume.careerimport;

import com.codecoachai.resume.careerimport.CareerImportModels.ImportRowView;
import com.codecoachai.resume.careerimport.entity.CareerImportRow;
import com.codecoachai.resume.mapper.careerimport.CareerImportRowMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareerImportRowTransaction {

    private final CareerImportRowMapper rowMapper;
    private final ObjectMapper objectMapper;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public ImportRowView execute(
            Long userId,
            Long batchId,
            RowOperation operation) {
        ImportRowView view = operation.execute();
        persist(userId, batchId, view);
        return view;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public void persistFailure(Long userId, Long batchId, ImportRowView view) {
        persist(userId, batchId, view);
    }

    private void persist(Long userId, Long batchId, ImportRowView view) {
        CareerImportRow row = new CareerImportRow();
        row.setUserId(userId);
        row.setBatchId(batchId);
        row.setRowNumber(view.getRowNumber());
        row.setDisposition(view.getDisposition());
        row.setRawDataJson(writeJson(view.getRaw()));
        row.setErrorCode(view.getErrorCode());
        row.setErrorMessage(truncate(view.getErrorMessage(), 1000));
        row.setDuplicateCandidatesJson(writeJson(view.getDuplicateCandidates()));
        row.setApplicationId(view.getApplicationId());
        row.setCalendarEventId(view.getCalendarEventId());
        rowMapper.insert(row);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Import row audit data is not serializable", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    @FunctionalInterface
    public interface RowOperation {
        ImportRowView execute();
    }
}
