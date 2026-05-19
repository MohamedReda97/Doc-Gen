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
import java.util.concurrent.atomic.AtomicLong;

public class DocumentGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentGenerator.class);
    private static final DocumentGenerator INSTANCE = new DocumentGenerator();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

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
        return generatePdf("direct-template-bytes", docxTemplateBytes, flatMappingJson);
    }

      


    
    private byte[] generatePdf(String templateLabel, byte[] docxTemplateBytes, String flatMappingJson)
            throws DocumentGenerationException {
        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        long startedAt = System.currentTimeMillis();
        LOG.info("BAW-DOC-TIMING request={} template={} stage=generatePdf-start inputBytes={} jsonChars={}",
                requestId, templateLabel, byteLength(docxTemplateBytes), stringLength(flatMappingJson));

        WordprocessingMLPackage wordPackage = loadTemplate(requestId, docxTemplateBytes);

        long jsonStartedAt = System.currentTimeMillis();
        Map<String, String> values = jsonParser.parse(flatMappingJson);
        logStage(requestId, "parse-flat-mapping", jsonStartedAt, "keys=" + values.size());

        long tableJsonStartedAt = System.currentTimeMillis();
        Map<String, java.util.List<Map<String, String>>> tables = tableMappingJsonParser.parse(flatMappingJson);
        logStage(requestId, "parse-table-mapping", tableJsonStartedAt, "tables=" + tables.size());

        long validationStartedAt = System.currentTimeMillis();
        ValidationReport report = templateValidator.validate(wordPackage);
        logStage(requestId, "validate-template", validationStartedAt,
                "placeholders=" + report.getPlaceholders().size()
                        + " warnings=" + report.getWarnings().size()
                        + " errors=" + report.getErrors().size());

        long warningStartedAt = System.currentTimeMillis();
        logValidation(report);
        Set<String> suppliedKeys = allSuppliedKeys(values, tables);
        logMappingWarnings(report.getPlaceholders(), suppliedKeys);
        logStage(requestId, "log-mapping-warnings", warningStartedAt, "suppliedKeys=" + suppliedKeys.size());

        long tableRepeatStartedAt = System.currentTimeMillis();
        int repeatedRows = tableRowRepeater.repeat(wordPackage, tables);
        LOG.info("Repeated {} table rows", repeatedRows);
        logStage(requestId, "repeat-table-rows", tableRepeatStartedAt, "rows=" + repeatedRows);

        long placeholderStartedAt = System.currentTimeMillis();
        int replacements = placeholderReplacer.replace(wordPackage, values);
        LOG.info("Replaced {} placeholders", replacements);
        logStage(requestId, "replace-placeholders", placeholderStartedAt, "replacements=" + replacements);

        long arabicStartedAt = System.currentTimeMillis();
        arabicRunDecorator.decorate(wordPackage);
        logStage(requestId, "decorate-arabic-runs", arabicStartedAt, null);

        long fontRewriteStartedAt = System.currentTimeMillis();
        fontNameRewriter.rewrite(wordPackage);
        logStage(requestId, "rewrite-font-names", fontRewriteStartedAt, null);

        long fontConfigureStartedAt = System.currentTimeMillis();
        fontRegistry.configure(requestId, wordPackage);
        logStage(requestId, "configure-fonts", fontConfigureStartedAt, null);

        long conversionStartedAt = System.currentTimeMillis();
        byte[] pdf = pdfConverter.convert(requestId, wordPackage);
        logStage(requestId, "convert-pdf", conversionStartedAt, "pdfBytes=" + byteLength(pdf));

        LOG.info("Generated PDF in {} ms, {} bytes", System.currentTimeMillis() - startedAt, pdf.length);
        logStage(requestId, "generatePdf-complete", startedAt, "pdfBytes=" + byteLength(pdf));
        return pdf;
    }

    public byte[] generatePdf(String templateId, String flatMappingJson)
            throws DocumentGenerationException {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new DocumentGenerationException("DOC-001", "Template ID is empty");
        }
        String path = "/templates/" + templateId + ".docx";
        long startedAt = System.currentTimeMillis();
        LOG.info("BAW-DOC-TIMING template={} stage=read-bundled-template-start path={}", templateId, path);
        try (InputStream inputStream = DocumentGenerator.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new DocumentGenerationException("DOC-001", "Bundled template not found: " + templateId);
            }
            byte[] templateBytes = readAllBytes(inputStream);
            LOG.info("BAW-DOC-TIMING template={} stage=read-bundled-template-complete durationMs={} bytes={}",
                    templateId, System.currentTimeMillis() - startedAt, byteLength(templateBytes));
            return generatePdf(templateId, templateBytes, flatMappingJson);
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

    private WordprocessingMLPackage loadTemplate(long requestId, byte[] docxTemplateBytes) throws DocumentGenerationException {
        if (docxTemplateBytes == null || docxTemplateBytes.length == 0) {
            throw new DocumentGenerationException("DOC-001", "DOCX template bytes are empty");
        }
        long sanitizeStartedAt = System.currentTimeMillis();
        byte[] sanitizedTemplateBytes = docxTemplateSanitizer.sanitize(docxTemplateBytes);
        logStage(requestId, "sanitize-template", sanitizeStartedAt,
                "inputBytes=" + byteLength(docxTemplateBytes) + " sanitizedBytes=" + byteLength(sanitizedTemplateBytes));

        long loadStartedAt = System.currentTimeMillis();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(sanitizedTemplateBytes)) {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(inputStream);
            logStage(requestId, "load-wordprocessing-package", loadStartedAt, null);
            return wordPackage;
        } catch (Exception e) {
            logStage(requestId, "load-wordprocessing-package-failed", loadStartedAt, e.getClass().getSimpleName());
            throw new DocumentGenerationException("DOC-002", "Failed to load DOCX template", e);
        }
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

    private void logStage(long requestId, String stage, long startedAt, String detail) {
        long durationMs = System.currentTimeMillis() - startedAt;
        if (detail == null || detail.isEmpty()) {
            LOG.info("BAW-DOC-TIMING request={} stage={} durationMs={}", requestId, stage, durationMs);
        } else {
            LOG.info("BAW-DOC-TIMING request={} stage={} durationMs={} {}", requestId, stage, durationMs, detail);
        }
    }

    private int byteLength(byte[] bytes) {
        return bytes == null ? 0 : bytes.length;
    }

    private int stringLength(String value) {
        return value == null ? 0 : value.length();
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
