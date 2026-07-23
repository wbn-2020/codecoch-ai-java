package com.codecoachai.resume.export;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AtsResumeDocument {
    private String name;
    private String headline;
    private String contact;
    private Style style = new Style();
    private final List<Section> sections = new ArrayList<>();

    @Data
    public static class Style {
        private float marginPt = 42f;
        private float nameFontPt = 18f;
        private float headlineFontPt = 11f;
        private float contactFontPt = 9f;
        private float headingFontPt = 11f;
        private float bodyFontPt = 10f;
        private float lineSpacing = 1.08f;
        private String fontFamily = "Arial";
    }

    @Data
    public static class Section {
        private final String heading;
        private final List<String> lines;
    }
}
