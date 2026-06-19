package com.codecoachai.search.controller;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private ElasticsearchClient esClient;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(esClient);
    }

    @Test
    void questionSearchCapsDeepOffsetAndDisablesFuzzyForShortKeywords() throws IOException {
        when(esClient.search(any(SearchRequest.class), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("stop after request capture"));

        assertThrows(RuntimeException.class, () -> controller.searchQuestions("AI", 100, 100, null, null));

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(esClient).search(requestCaptor.capture(), eq(JsonNode.class));
        SearchRequest request = requestCaptor.getValue();
        assertTrue(request.from() <= 1900);
        assertNull(request.query().bool().must().get(0).multiMatch().fuzziness());
    }
}
