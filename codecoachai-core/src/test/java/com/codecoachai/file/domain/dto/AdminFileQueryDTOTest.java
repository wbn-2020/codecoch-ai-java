package com.codecoachai.file.domain.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AdminFileQueryDTOTest {

    @Test
    void effectivePageSizeClampsOversizedRequestsToLocalMaximum() {
        AdminFileQueryDTO query = new AdminFileQueryDTO();
        query.setPageSize(10_000L);

        assertEquals(AdminFileQueryDTO.MAX_PAGE_SIZE, query.effectivePageSize());
    }

    @Test
    void effectivePageSizeFallsBackWhenClientSendsInvalidValue() {
        AdminFileQueryDTO query = new AdminFileQueryDTO();
        query.setPageSize(0L);

        assertEquals(AdminFileQueryDTO.DEFAULT_PAGE_SIZE, query.effectivePageSize());
    }
}
