package com.bankaudi.baw.document.api;

import com.bankaudi.baw.document.engine.PlaceholderExtractor;
import com.bankaudi.baw.document.engine.PlaceholderReplacer;
import com.bankaudi.baw.document.docx.DocxTemplateSanitizer;
import com.bankaudi.baw.document.engine.TableRowRepeater;
import com.bankaudi.baw.document.font.ArabicRunDecorator;
import com.bankaudi.baw.document.font.BundledFontRegistry;
import com.bankaudi.baw.document.font.FontNameRewriter;
import com.bankaudi.baw.document.json.FlatMappingJsonParser;
import com.bankaudi.baw.document.json.TableMappingJsonParser;
import com.bankaudi.baw.document.pdf.PdfConverter;
import com.bankaudi.baw.document.validation.TemplateValidator;
import com.bankaudi.baw.document.validation.ValidationReport;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DocumentGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentGenerator.class);
    private static final DocumentGenerator INSTANCE = new DocumentGenerator();

    private final FlatMappingJsonParser jsonParser = new FlatMappingJsonParser();
    private final TableMappingJsonParser tableMappingJsonParser = new TableMappingJsonParser();
    private final PlaceholderExtractor placeholderExtractor = new PlaceholderExtractor();
    private final PlaceholderReplacer placeholderReplacer = new PlaceholderReplacer();
    private final TableRowRepeater tableRowRepeater = new TableRowRepeater();
    private final TemplateValidator templateValidator = new TemplateValidator();
    private final DocxTemplateSanitizer docxTemplateSanitizer = new DocxTemplateSanitizer();
    private final BundledFontRegistry fontRegistry = new BundledFontRegistry();
    private final ArabicRunDecorator arabicRunDecorator = new ArabicRunDecorator();
    private final FontNameRewriter fontNameRewriter = new FontNameRewriter();
    private final PdfConverter pdfConverter = new PdfConverter();

    public static DocumentGenerator getInstance() {
        return INSTANCE;
    }

    public byte[] generatePdf(byte[] docxTemplateBytes, String flatMappingJson)
            throws DocumentGenerationException {
        long startedAt = System.currentTimeMillis();
        WordprocessingMLPackage wordPackage = loadTemplate(docxTemplateBytes);
        Map<String, String> values = jsonParser.parse(flatMappingJson);
        Map<String, java.util.List<Map<String, String>>> tables = tableMappingJsonParser.parse(flatMappingJson);

        ValidationReport report = templateValidator.validate(wordPackage);
        logValidation(report);
        logMappingWarnings(report.getPlaceholders(), allSuppliedKeys(values, tables));

        int repeatedRows = tableRowRepeater.repeat(wordPackage, tables);
        LOG.info("Repeated {} table rows", repeatedRows);

        int replacements = placeholderReplacer.replace(wordPackage, values);
        LOG.info("Replaced {} placeholders", replacements);

        arabicRunDecorator.decorate(wordPackage);
        fontNameRewriter.rewrite(wordPackage);
        fontRegistry.configure(wordPackage);

        byte[] pdf = pdfConverter.convert(wordPackage);
        LOG.info("Generated PDF in {} ms, {} bytes", System.currentTimeMillis() - startedAt, pdf.length);
        return pdf;
    }

    public byte[] generatePdf(String templateId, String flatMappingJson)
            throws DocumentGenerationException {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new DocumentGenerationException("DOC-001", "Template ID is empty");
        }
        String path = "/templates/" + templateId + ".docx";
        try (InputStream inputStream = DocumentGenerator.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new DocumentGenerationException("DOC-001", "Bundled template not found: " + templateId);
            }
            return generatePdf(readAllBytes(inputStream), flatMappingJson);
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentGenerationException("DOC-001", "Failed to read bundled template: " + templateId, e);
        }
    }

    public ValidationReport validateTemplate(byte[] docxTemplateBytes) throws DocumentGenerationException {
        return templateValidator.validate(loadTemplate(docxTemplateBytes));
    }

    public Set<String> extractPlaceholders(byte[] docxTemplateBytes) throws DocumentGenerationException {
        return placeholderExtractor.extract(loadTemplate(docxTemplateBytes));
    }

    private WordprocessingMLPackage loadTemplate(byte[] docxTemplateBytes) throws DocumentGenerationException {
        if (docxTemplateBytes == null || docxTemplateBytes.length == 0) {
            throw new DocumentGenerationException("DOC-001", "DOCX template bytes are empty");
        }
        byte[] sanitizedTemplateBytes = docxTemplateSanitizer.sanitize(docxTemplateBytes);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(sanitizedTemplateBytes)) {
            return WordprocessingMLPackage.load(inputStream);
        } catch (Exception e) {
            throw new DocumentGenerationException("DOC-002", "Failed to load DOCX template", e);
        }
    }

    private void logValidation(ValidationReport report) {
        for (String warning : report.getWarnings()) {
            LOG.warn(warning);
        }
        for (String error : report.getErrors()) {
            LOG.error(error);
        }
    }

    private void logMappingWarnings(Set<String> placeholders, Set<String> jsonKeys) {
        Set<String> missing = new LinkedHashSet<>(placeholders);
        missing.removeAll(jsonKeys);
        for (String key : missing) {
            LOG.warn("Missing JSON value for placeholder {}; replacing with empty text", key);
        }

        Set<String> unused = new LinkedHashSet<>(jsonKeys);
        unused.removeAll(placeholders);
        for (String key : unused) {
            LOG.warn("JSON key {} is not used by the template", key);
        }
    }

    private Set<String> allSuppliedKeys(Map<String, String> values, Map<String, List<Map<String, String>>> tables) {
        Set<String> suppliedKeys = new LinkedHashSet<>(values.keySet());
        for (Map.Entry<String, List<Map<String, String>>> table : tables.entrySet()) {
            for (Map<String, String> row : table.getValue()) {
                for (String field : row.keySet()) {
                    suppliedKeys.add(table.getKey() + "[]." + field);
                }
            }
        }
        return suppliedKeys;
    }

    private byte[] readAllBytes(InputStream inputStream) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int read;
        try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
