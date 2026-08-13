package com.codecoachai.resume.domain.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.Data;

@Data
public class ApplyResumeOptimizeResultDTO {

    private String applyMode;

    private List<String> selectedFields;

    private List<Integer> selectedSuggestionIndexes;

    private JsonNode fieldPatches;

    private Boolean applyAll;
}
