package com.example.intern.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PdfProcessingService {

    @Value("${file.result-dir}")
    private String resultDir;

    // 1. READ: Extract text from Teacher's PDF to send to ML model
    public String extractTextFromPdf(String filePath) {
        File file = new File(filePath);
        // Note: Loader.loadPDF is the correct syntax for PDFBox 3.x
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PDF file", e);
        }
    }

    // 2. WRITE: Generate Student Result PDF
    public String generateResultPdf(String studentName, int totalMarks) {
        File directory = new File(resultDir);
        if (!directory.exists()) directory.mkdirs();

        String fileName = "Result_" + UUID.randomUUID() + ".pdf";
        String filePath = Paths.get(resultDir, fileName).toString();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                contentStream.beginText();
                contentStream.setFont(fontBold, 18);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Official Exam Result");

                contentStream.newLineAtOffset(0, -40);
                contentStream.setFont(fontRegular, 12);
                contentStream.showText("Student Name: " + studentName);

                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Total Marks Obtained: " + totalMarks);
                contentStream.endText();
            }
            document.save(filePath);
            return filePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate Result PDF", e);
        }
    }
}