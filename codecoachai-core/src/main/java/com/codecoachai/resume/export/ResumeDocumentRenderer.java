package com.codecoachai.resume.export;

import java.io.IOException;
import java.io.OutputStream;

public interface ResumeDocumentRenderer {
    String format();

    void render(AtsResumeDocument document, OutputStream output) throws IOException;
}
