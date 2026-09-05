package com.cards.api.unit.ai;

import com.cards.api.exception.domain.UnsupportedFileTypeException;
import com.cards.api.service.ai.FileParserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileParserService")
class FileParserServiceTest {

    private FileParserService service;

    @BeforeEach
    void setUp() {
        service = new FileParserService();
    }

    @Nested
    @DisplayName("parse .txt files")
    class TxtFiles {

        @Test
        @DisplayName("should extract text from .txt file")
        void shouldExtractText() {
            var content = "Hello, this is a test file.";
            var result = service.parse("test.txt", content.getBytes(StandardCharsets.UTF_8));

            assertThat(result).isEqualTo("Hello, this is a test file.");
        }

        @Test
        @DisplayName("should handle UTF-8 characters")
        void shouldHandleUtf8() {
            var content = "Español français 中文";
            var result = service.parse("notes.txt", content.getBytes(StandardCharsets.UTF_8));

            assertThat(result).isEqualTo("Español français 中文");
        }

        @Test
        @DisplayName("should strip leading and trailing whitespace")
        void shouldStripWhitespace() {
            var content = "  some text  ";
            var result = service.parse("file.txt", content.getBytes(StandardCharsets.UTF_8));

            assertThat(result).isEqualTo("some text");
        }

        @Test
        @DisplayName("should throw when text is blank")
        void shouldThrowWhenBlank() {
            var content = "   ";
            assertThatThrownBy(() -> service.parse("empty.txt", content.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
        }
    }

    @Nested
    @DisplayName("parse .pdf files")
    class PdfFiles {

        @Test
        @DisplayName("should extract text from .pdf file")
        void shouldExtractText() throws Exception {
            var pdfBytes = createSimplePdf("Hello PDF World!");

            var result = service.parse("test.pdf", pdfBytes);

            assertThat(result).contains("Hello PDF World!");
        }

        @Test
        @DisplayName("should extract multiple lines from PDF")
        void shouldExtractMultipleLines() throws Exception {
            var pdfBytes = createMultilinePdf("Line 1", "Line 2", "Line 3");

            var result = service.parse("doc.pdf", pdfBytes);

            assertThat(result).contains("Line 1");
            assertThat(result).contains("Line 2");
            assertThat(result).contains("Line 3");
        }
    }

    @Nested
    @DisplayName("parse unsupported files")
    class UnsupportedFiles {

        @Test
        @DisplayName("should throw UnsupportedFileTypeException for .docx")
        void shouldThrowForDocx() {
            assertThatThrownBy(() -> service.parse("file.docx", new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining(".docx");
        }

        @Test
        @DisplayName("should throw UnsupportedFileTypeException for unknown extension")
        void shouldThrowForUnknown() {
            assertThatThrownBy(() -> service.parse("file.xyz", new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining(".xyz");
        }

        @Test
        @DisplayName("should throw UnsupportedFileTypeException for no extension")
        void shouldThrowForNoExtension() {
            assertThatThrownBy(() -> service.parse("file", new byte[]{1, 2, 3}))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("(none)");
        }
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (var contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }

            var baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createMultilinePdf(String... lines) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (var contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.setLeading(14.5f);
                contentStream.newLineAtOffset(50, 700);
                for (var line : lines) {
                    contentStream.showText(line);
                    contentStream.newLine();
                }
                contentStream.endText();
            }

            var baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }
}
