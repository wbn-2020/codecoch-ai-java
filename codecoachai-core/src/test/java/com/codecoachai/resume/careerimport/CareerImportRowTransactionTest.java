package com.codecoachai.resume.careerimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codecoachai.resume.careerimport.CareerImportModels.ImportRowView;
import com.codecoachai.resume.careerimport.entity.CareerImportRow;
import com.codecoachai.resume.mapper.careerimport.CareerImportRowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;

@ExtendWith(MockitoExtension.class)
class CareerImportRowTransactionTest {

    @Mock
    private CareerImportRowMapper rowMapper;

    private CareerImportRowTransaction rowTransaction;

    @BeforeEach
    void setUp() {
        rowTransaction = new CareerImportRowTransaction(
                rowMapper, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void executeOwnsRequiresNewRollbackForExceptionBoundary() throws Exception {
        Method method = CareerImportRowTransaction.class.getMethod(
                "execute",
                Long.class,
                Long.class,
                CareerImportRowTransaction.RowOperation.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        TransactionAttribute attribute = new AnnotationTransactionAttributeSource()
                .getTransactionAttribute(method, CareerImportRowTransaction.class);

        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                attribute.getPropagationBehavior());
    }

    @Test
    void auditFailureEscapesTheSameRowOperation() {
        ImportRowView view = new ImportRowView();
        view.setRowNumber(2);
        view.setDisposition("SUCCESS");
        view.setRaw(Map.of("job_title", "Backend Engineer"));
        when(rowMapper.insert(any(CareerImportRow.class)))
                .thenThrow(new IllegalStateException("audit insert failed"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> rowTransaction.execute(10L, 30L, () -> view));

        assertEquals("audit insert failed", failure.getMessage());
        verify(rowMapper).insert(any(CareerImportRow.class));
    }
}
