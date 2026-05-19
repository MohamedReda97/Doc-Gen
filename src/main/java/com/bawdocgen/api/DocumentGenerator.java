package com.bawdocgen.api;

import com.bawdocgen.docx.DocxPackageProcessor;
import com.bawdocgen.docx.DocxTemplateRepository;
import com.bawdocgen.json.FlatMappingJsonParser;
import com.bawdocgen.json.TableMappingJsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class DocumentGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentGenerator.class);
    private static final DocumentGenerator INSTANCE = new DocumentGenerator();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();

    private final FlatMappingJsonParser flatMappingJsonParser = new FlatMappingJsonParser();
    private final TableMappingJsonParser tableMappingJsonParser = new TableMappingJsonParser();
    private final DocxTemplateRepository templateRepository = new DocxTemplateRepository();
    private final DocxPackageProcessor docxPackageProcessor = new DocxPackageProcessor();

    public static DocumentGenerator getInstance() {
        return INSTANCE;
    }

    public byte[] generateDocx(String templateId, String jsonPayload) throws DocumentGenerationException {
        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        long startedAt = System.currentTimeMillis();
        validateTemplateId(templateId);

        LOG.info("BAW-DOC-TIMING request={} template={} stage=generateDocx-start jsonChars={}",
                requestId, templateId, stringLength(jsonPayload));

        long templateStartedAt = System.currentTimeMillis();
        byte[] templateBytes = templateRepository.getTemplate(templateId);
        logStage(requestId, "read-bundled-template", templateStartedAt,
                "template=" + templateId + " bytes=" + byteLength(templateBytes));

        byte[] docx = generateDocx(requestId, templateId, templateBytes, jsonPayload, startedAt);
        LOG.info("BAW-DOC-TIMING request={} template={} stage=generateDocx-complete durationMs={} docxBytes={}",
                requestId, templateId, System.currentTimeMillis() - startedAt, byteLength(docx));
        return docx;
    }

    public byte[] generateDocx(byte[] docxTemplateBytes, String jsonPayload) throws DocumentGenerationException {
        long requestId = REQUEST_SEQUENCE.incrementAndGet();
        long startedAt = System.currentTimeMillis();
        LOG.info("BAW-DOC-TIMING request={} template=direct-template-bytes stage=generateDocx-start inputBytes={} jsonChars={}",
                requestId, byteLength(docxTemplateBytes), stringLength(jsonPayload));
        return generateDocx(requestId, "direct-template-bytes", docxTemplateBytes, jsonPayload, startedAt);
    }

    public Set<String> extractPlaceholders(byte[] docxTemplateBytes) throws DocumentGenerationException {
        validateTemplateBytes(docxTemplateBytes);
        return docxPackageProcessor.extractPlaceholders(docxTemplateBytes);
    }

    private byte[] generateDocx(long requestId, String templateLabel, byte[] docxTemplateBytes,
                                String jsonPayload, long startedAt) throws DocumentGenerationException {
        validateTemplateBytes(docxTemplateBytes);

        long flatStartedAt = System.currentTimeMillis();
        Map<String, String> values = flatMappingJsonParser.parse(jsonPayload);
        logStage(requestId, "parse-flat-mapping", flatStartedAt, "keys=" + values.size());

        long tableStartedAt = System.currentTimeMillis();
        Map<String, List<Map<String, String>>> tables = tableMappingJsonParser.parse(jsonPayload);
        logStage(requestId, "parse-table-mapping", tableStartedAt, "tables=" + tables.size());

        long renderStartedAt = System.currentTimeMillis();
        byte[] docx = docxPackageProcessor.generate(docxTemplateBytes, values, tables);
        logStage(requestId, "render-docx", renderStartedAt, "docxBytes=" + byteLength(docx));

        LOG.info("BAW-DOC-TIMING request={} template={} stage=generateDocx-total durationMs={} inputBytes={} docxBytes={}",
                requestId, templateLabel, System.currentTimeMillis() - startedAt,
                byteLength(docxTemplateBytes), byteLength(docx));
        return docx;
    }

    private void validateTemplateId(String templateId) throws DocumentGenerationException {
        if (templateId == null || templateId.trim().isEmpty()) {
            throw new DocumentGenerationException("DOC-001", "Template name is empty");
        }
    }

    private void validateTemplateBytes(byte[] docxTemplateBytes) throws DocumentGenerationException {
        if (docxTemplateBytes == null || docxTemplateBytes.length == 0) {
            throw new DocumentGenerationException("DOC-001", "DOCX template bytes are empty");
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
}
