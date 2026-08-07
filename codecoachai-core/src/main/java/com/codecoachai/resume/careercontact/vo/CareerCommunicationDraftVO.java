package com.codecoachai.resume.careercontact.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CareerCommunicationDraftVO {
    private String subject;
    private String body;
    private List<String> factsUsed = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private String confidence;
    private String fallback;
}
