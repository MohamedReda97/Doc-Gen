package com.bankaudi.baw.document;

import com.bankaudi.baw.document.api.DocumentGenerator;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.Tbl;
import org.docx4j.wml.Tc;
import org.docx4j.wml.Text;
import org.docx4j.wml.Tr;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfGenerationIntegrationTest {
    @Test
    void generatesPdfFromBundledSampleTemplate() throws Exception {
        String json = resourceString("/Personal_Loan_Application_form_variables.json");

        byte[] pdf = DocumentGenerator.getInstance().generatePdf("personal-loan-application", json);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Karim"));
            assertTrue(text.contains("Nasser"));
        }
    }

    @Test
    void generatesPdfFromPledgeLetterTemplate() throws Exception {
        byte[] template = resourceBytes("/Pledge_Letter_FRESH_USD.docx");
        String json = resourceString("/Pledge_Letter_FRESH_USD_variables.json");

        byte[] pdf = DocumentGenerator.getInstance().generatePdf(template, json);

        assertTrue(pdf.length > 1000);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF"));
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("CIF-458291"));
            assertTrue(text.contains("Ahmed Mohamed Nasser"));
        }
    }

    @Test
    void generatesPdfFromTemplateWithDynamicTableRows() throws Exception {
        String json = "{"
                + "\"flat_mapping\":{\"DATE\":\"17/05/2026\"},"
                + "\"tables\":{\"customers\":["
                + "{\"cif_number\":\"CIF-1\",\"customer_full_name\":\"First Customer\"},"
                + "{\"cif_number\":\"CIF-2\",\"customer_full_name\":\"Second Customer\"}"
                + "]}}";

        byte[] pdf = DocumentGenerator.getInstance().generatePdf(dynamicTableTemplate(), json);

        try (PDDocument document = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("17/05/2026"));
            assertTrue(text.contains("CIF-1"));
            assertTrue(text.contains("First Customer"));
            assertTrue(text.contains("CIF-2"));
            assertTrue(text.contains("Second Customer"));
        }
    }

    private String resourceString(String path) throws Exception {
        return new String(resourceBytes(path), java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] resourceBytes(String path) throws Exception {
        try (InputStream inputStream = getClass().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Missing test resource " + path);
            }
            return inputStream.readAllBytes();
        }
    }

    private byte[] dynamicTableTemplate() throws Exception {
        ObjectFactory factory = new ObjectFactory();
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        wordPackage.getMainDocumentPart().addParagraphOfText("Date: {{DATE}}");

        Tbl table = factory.createTbl();
        table.getContent().add(row(factory, "CIF", "Customer name"));
        table.getContent().add(row(factory, "{{customers[].cif_number}}", "{{customers[].customer_full_name}}"));
        wordPackage.getMainDocumentPart().addObject(table);

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            wordPackage.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private Tr row(ObjectFactory factory, String... values) {
        Tr row = factory.createTr();
        for (String value : values) {
            Tc cell = factory.createTc();
            P paragraph = factory.createP();
            org.docx4j.wml.R run = factory.createR();
            Text text = factory.createText();
            text.setValue(value);
            run.getContent().add(text);
            paragraph.getContent().add(run);
            cell.getContent().add(paragraph);
            row.getContent().add(cell);
        }
        return row;
    }
}
