package com.cards.api.service.ai;

import com.cards.api.exception.domain.UnsupportedFileTypeException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class FileParserService {

    private static final int MAX_TEXT_LENGTH = 100_000;

    public String parse(String filename, byte[] content) {
        var lower = filename.toLowerCase();

        if (lower.endsWith(".txt")) {
            return parseTxt(content);
        } else if (lower.endsWith(".pdf")) {
            return parsePdf(content);
        } else {
            var ext = lower.contains(".") ? lower.substring(lower.lastIndexOf('.')) : "(none)";
            throw new UnsupportedFileTypeException(ext);
        }
    }

    private String parseTxt(byte[] content) {
        var text = new String(content, StandardCharsets.UTF_8);
        return validateAndTruncate(text);
    }

    private String parsePdf(byte[] content) {
        try (var pdf = Loader.loadPDF(content)) {
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var text = new String(stripper.getText(pdf).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            return validateAndTruncate(text);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse PDF: " + e.getMessage(), e);
        }
    }

    private String validateAndTruncate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("The file is empty or contains no extractable text.");
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            text = text.substring(0, MAX_TEXT_LENGTH);
        }

        return text.strip();
    }
}
