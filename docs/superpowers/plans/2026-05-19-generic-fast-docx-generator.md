# Generic Fast DOCX Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the current customer-specific DOCX-to-PDF generator into a generic, fast, standalone DOCX generator for IBM BAW.

**Architecture:** Replace the PDF/docx4j rendering pipeline with a DOCX ZIP/XML transformation pipeline. Keep a BAW-safe public service that accepts `templateName` and `jsonPayload` as `String` inputs, returns the generated DOCX as Base64 `String`, and can handle many concurrent calls. Remove customer-specific package names, Maven coordinates, shaded relocation names, docs, and class names.

**Tech Stack:** Java 8 bytecode, IBM BAW/WebSphere, Maven Shade standalone JAR, Jackson for JSON, JDK ZIP/XML APIs for DOCX manipulation, JUnit 5.

---

## Key Design Decisions

1. **Output format is DOCX only.**
   - Remove PDF conversion, Apache FOP, PDFBox test dependency, PDF Base64 APIs, font registration, and PDF-specific documentation.

2. **Avoid docx4j in the generation hot path.**
   - The BAW logs show `WordprocessingMLPackage.load(...)` and JAXB context initialization taking 14-30 minutes under BAW `ManagedAssetClassLoader`.
   - For DOCX output, we can transform the DOCX as a ZIP package and update XML entries directly.
   - This avoids docx4j/JAXB/FOP startup costs during normal generation.

3. **Keep a standalone shaded JAR.**
   - Jackson remains shaded.
   - The final JAR should be much smaller because docx4j/FOP/PDF dependencies are removed.

4. **Support concurrency.**
   - Template bytes are cached as immutable byte arrays.
   - Each call creates its own output ZIP stream and local XML documents.
   - No global mutable generation state.

5. **Generic naming.**
   - Replace `com.legacy.customer.document` with `com.bawdocgen`.
   - Replace `BawDocumentService` with `DocxGenerationService`.
   - Replace `generatePdfBase64` with `generateDocxBase64`.
   - Replace Maven `groupId` with `com.bawdocgen`.
   - Keep the artifact name generic: `baw-docx-generator`.

---

## File Structure

- Modify `pom.xml`
  - New Maven coordinates and remove PDF/docx4j dependencies.
- Move Java packages:
  - From `src/main/java/com/legacycustomer/baw/document/**`
  - To `src/main/java/com/bawdocgen/**`
- Create `src/main/java/com/bawdocgen/api/DocxGenerationService.java`
  - Public BAW-facing service.
- Create `src/main/java/com/bawdocgen/api/DocumentGenerator.java`
  - Main Java API.
- Create `src/main/java/com/bawdocgen/docx/DocxPackageProcessor.java`
  - Reads input DOCX ZIP and writes output DOCX ZIP.
- Create `src/main/java/com/bawdocgen/docx/XmlPartTransformer.java`
  - Replaces placeholders in Word XML parts.
- Create `src/main/java/com/bawdocgen/docx/DocxTemplateRepository.java`
  - Caches bundled template bytes by template name.
- Keep and rename JSON parsers:
  - `FlatMappingJsonParser`
  - `TableMappingJsonParser`
- Keep and adapt placeholder/table logic:
  - `PlaceholderPattern`
  - A new XML-aware replacement implementation.
- Delete or retire:
  - `src/main/java/**/pdf/PdfConverter.java`
  - `BundledFontRegistry`
  - `BundledOnlyFontMapper`
  - `FontNameRewriter`
  - PDF integration tests
  - PDFBox dependency
  - docx4j/FOP dependencies
- Update docs:
  - `README.md`
  - `IBM_BAW_JAR_USAGE.md`

---

## Task 1: Rename Project Coordinates And Public API

**Files:**
- Modify: `pom.xml`
- Move: `src/main/java/com/legacycustomer/baw/document/**` to `src/main/java/com/bawdocgen/**`
- Move: `src/test/java/com/legacycustomer/baw/document/**` to `src/test/java/com/bawdocgen/**`

- [ ] **Step 1: Update Maven coordinates**

Change `pom.xml`:

```xml
<groupId>com.bawdocgen</groupId>
<artifactId>baw-docx-generator</artifactId>
<version>1.0.0</version>
<name>BAW DOCX Generator</name>
<description>Self-contained DOCX template generator for IBM BAW</description>
```

- [ ] **Step 2: Update shaded Jackson relocation**

Change:

```xml
<shadedPattern>com.bawdocgen.shaded.jackson</shadedPattern>
```

- [ ] **Step 3: Move package directories**

Run:

```bash
mkdir -p src/main/java/com/bawdocgen
mkdir -p src/test/java/com/bawdocgen
mv src/main/java/com/legacycustomer/baw/document/* src/main/java/com/bawdocgen/
mv src/test/java/com/legacycustomer/baw/document/* src/test/java/com/bawdocgen/
```

Then remove empty directories:

```bash
find src/main/java/com/legacycustomer -type d -empty -delete
find src/test/java/com/legacycustomer -type d -empty -delete
```

- [ ] **Step 4: Rewrite package declarations and imports**

Run a mechanical replacement:

```bash
rg -l "com\\.legacycustomer\\.baw\\.document|package com\\.legacycustomer\\.baw\\.document" src pom.xml README.md IBM_BAW_JAR_USAGE.md \
  | xargs sed -i 's/com\\.legacycustomer\\.baw\\.document/com.bawdocgen/g'
```

Then inspect:

```bash
rg -n "legacycustomer|com\\.legacycustomer" .
```

Expected: no matches, except possibly old log examples that should be removed or rewritten.

- [ ] **Step 5: Compile to reveal remaining rename issues**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: compile errors only from PDF/docx4j removal tasks not done yet, or no errors if dependencies remain temporarily.

---

## Task 2: Remove PDF Dependencies And PDF Classes

**Files:**
- Modify: `pom.xml`
- Delete: `src/main/java/com/bawdocgen/pdf/PdfConverter.java`
- Delete or rewrite: `src/test/java/com/bawdocgen/PdfGenerationIntegrationTest.java`
- Modify: `src/main/java/com/bawdocgen/api/DocumentGenerator.java`
- Modify: `src/main/java/com/bawdocgen/api/BawDocumentService.java`

- [ ] **Step 1: Remove PDF/docx4j conversion dependencies**

Delete from `pom.xml`:

```xml
<dependency>
    <groupId>org.docx4j</groupId>
    <artifactId>docx4j-export-fo</artifactId>
    <version>${docx4j.version}</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>2.0.32</version>
    <scope>test</scope>
</dependency>
```

If Task 4 fully removes docx4j from the hot path, also remove:

```xml
<dependency>
    <groupId>org.docx4j</groupId>
    <artifactId>docx4j-JAXB-ReferenceImpl</artifactId>
    <version>${docx4j.version}</version>
</dependency>
```

- [ ] **Step 2: Delete PDF converter**

Delete:

```text
src/main/java/com/bawdocgen/pdf/PdfConverter.java
```

- [ ] **Step 3: Replace PDF API methods**

In `DocumentGenerator`, replace:

```java
public byte[] generatePdf(String templateId, String flatMappingJson)
public byte[] generatePdf(byte[] docxTemplateBytes, String flatMappingJson)
```

with:

```java
public byte[] generateDocx(String templateId, String jsonPayload)
public byte[] generateDocx(byte[] docxTemplateBytes, String jsonPayload)
```

- [ ] **Step 4: Replace BAW service method**

Rename `BawDocumentService.java` to:

```text
src/main/java/com/bawdocgen/api/DocxGenerationService.java
```

Use this public API:

```java
package com.bawdocgen.api;

import java.util.Base64;

public class DocxGenerationService {
    public DocxGenerationService() {
    }

    public String generateDocxBase64(String templateName, String jsonPayload) {
        try {
            byte[] docx = DocumentGenerator.getInstance().generateDocx(templateName, jsonPayload);
            return Base64.getEncoder().encodeToString(docx);
        } catch (DocumentGenerationException e) {
            throw new DocxGenerationServiceException(formatError(templateName, jsonPayload, e), e);
        } catch (RuntimeException e) {
            throw new DocxGenerationServiceException(formatUnexpectedError(templateName, jsonPayload, e), e);
        }
    }

    private String formatError(String templateName, String jsonPayload, DocumentGenerationException e) {
        return "DOCX generation failed | code=" + e.getCode()
                + " | message=" + e.getMessage()
                + " | templateName=" + safe(templateName)
                + " | jsonPreview=" + preview(jsonPayload)
                + " | rootCause=" + rootCause(e);
    }

    private String formatUnexpectedError(String templateName, String jsonPayload, RuntimeException e) {
        return "DOCX generation failed | code=DOCX-UNEXPECTED"
                + " | message=" + e.getMessage()
                + " | templateName=" + safe(templateName)
                + " | jsonPreview=" + preview(jsonPayload)
                + " | rootCause=" + rootCause(e);
    }

    private String preview(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private String safe(String value) {
        return value == null ? "<null>" : value;
    }

    private String rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName() + ": " + current.getMessage();
    }

    public static class DocxGenerationServiceException extends RuntimeException {
        public DocxGenerationServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
```

- [ ] **Step 5: Verify no PDF API remains**

Run:

```bash
rg -n "PDF|Pdf|pdf|generatePdf|generatePdfBase64|PdfConverter|FOP|toFO|pdfbox" src/main/java src/test/java pom.xml
```

Expected: no production matches. Test/docs matches only if intentionally describing migration history, preferably removed.

---

## Task 3: Build A ZIP-Based DOCX Processor

**Files:**
- Create: `src/main/java/com/bawdocgen/docx/DocxPackageProcessor.java`
- Create: `src/main/java/com/bawdocgen/docx/XmlPartTransformer.java`
- Test: `src/test/java/com/bawdocgen/DocxPackageProcessorTest.java`

- [ ] **Step 1: Add failing test for DOCX output**

Create:

```java
package com.bawdocgen;

import com.bawdocgen.api.DocumentGenerator;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxPackageProcessorTest {
    @Test
    void generatesDocxZipWithoutPlaceholderText() throws Exception {
        String json = "{\"flat_mapping\":{\"customer.name\":\"Karim Haddad\",\"loan.amount\":\"25000\"}}";

        byte[] docx = DocumentGenerator.getInstance().generateDocx("personal-loan-application", json);

        assertTrue(docx.length > 1000);
        String documentXml = zipEntryText(docx, "word/document.xml");
        assertTrue(documentXml.contains("Karim Haddad"));
        assertFalse(documentXml.contains("{{customer.name}}"));
    }

    private String zipEntryText(byte[] docx, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    while ((read = zip.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalArgumentException("Missing DOCX entry " + name);
    }
}
```

Run:

```bash
mvn -q -Dtest=DocxPackageProcessorTest test
```

Expected: fail until `generateDocx` and ZIP transformation exist.

- [ ] **Step 2: Implement ZIP copy and XML transformation**

Create `DocxPackageProcessor`:

```java
package com.bawdocgen.docx;

import com.bawdocgen.api.DocumentGenerationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxPackageProcessor {
    private final XmlPartTransformer xmlPartTransformer = new XmlPartTransformer();

    public byte[] generate(byte[] templateBytes, Map<String, String> values) throws DocumentGenerationException {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream(templateBytes.length + 8192);
             ZipOutputStream output = new ZipOutputStream(bytes)) {

            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = readAllBytes(input);
                ZipEntry newEntry = new ZipEntry(entry.getName());
                output.putNextEntry(newEntry);
                if (isWordXmlPart(entry.getName())) {
                    output.write(xmlPartTransformer.transform(content, values));
                } else {
                    output.write(content);
                }
                output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentGenerationException("DOCX-006", "Failed to generate DOCX package", e);
        }
    }

    private boolean isWordXmlPart(String name) {
        return name.equals("word/document.xml")
                || name.startsWith("word/header")
                || name.startsWith("word/footer")
                || name.equals("word/footnotes.xml")
                || name.equals("word/endnotes.xml");
    }

    private byte[] readAllBytes(java.io.InputStream inputStream) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int read;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
```

- [ ] **Step 3: Implement initial XML text replacement**

Create `XmlPartTransformer`:

```java
package com.bawdocgen.docx;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class XmlPartTransformer {
    public byte[] transform(byte[] xmlBytes, Map<String, String> values) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            xml = xml.replace("{{" + entry.getKey() + "}}", escapeXml(entry.getValue()));
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
```

This is the minimum implementation. Later tasks make it robust for split runs.

- [ ] **Step 4: Wire `DocumentGenerator.generateDocx`**

Use:

```java
Map<String, String> values = jsonParser.parse(jsonPayload);
byte[] templateBytes = templateRepository.templateBytes(templateId);
return docxPackageProcessor.generate(templateBytes, values);
```

- [ ] **Step 5: Verify**

Run:

```bash
mvn -q -Dtest=DocxPackageProcessorTest test
```

Expected: pass for placeholders that are not split across XML runs.

---

## Task 4: Handle Split Placeholders Across Word Runs

**Files:**
- Modify: `src/main/java/com/bawdocgen/docx/XmlPartTransformer.java`
- Test: `src/test/java/com/bawdocgen/XmlPartTransformerTest.java`

- [ ] **Step 1: Add failing split-run test**

```java
package com.bawdocgen;

import com.bawdocgen.docx.XmlPartTransformer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlPartTransformerTest {
    @Test
    void replacesPlaceholderSplitAcrossTextNodes() {
        String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p>"
                + "<w:r><w:t>{{customer.</w:t></w:r>"
                + "<w:r><w:t>name}}</w:t></w:r>"
                + "</w:p></w:body></w:document>";

        byte[] result = new XmlPartTransformer().transform(
                xml.getBytes(StandardCharsets.UTF_8),
                Collections.singletonMap("customer.name", "Karim Haddad"));

        String output = new String(result, StandardCharsets.UTF_8);
        assertTrue(output.contains("Karim Haddad"));
        assertFalse(output.contains("{{customer."));
        assertFalse(output.contains("name}}"));
    }
}
```

- [ ] **Step 2: Implement XML-aware text-node collection**

Use JDK DOM:

```java
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
factory.setNamespaceAware(true);
factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
```

Collect all `w:t` nodes in document order, concatenate their text, replace placeholders, then write the replaced text back into the first affected `w:t` node and blank the remaining affected nodes.

- [ ] **Step 3: Preserve XML output**

Serialize DOM with `TransformerFactory` and omit XML declaration:

```java
Transformer transformer = TransformerFactory.newInstance().newTransformer();
transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
```

- [ ] **Step 4: Verify**

Run:

```bash
mvn -q -Dtest=XmlPartTransformerTest,DocxPackageProcessorTest test
```

Expected: pass.

---

## Task 5: Preserve Dynamic Table Rows For DOCX Output

**Files:**
- Modify: `src/main/java/com/bawdocgen/docx/XmlPartTransformer.java`
- Keep: `TableMappingJsonParser`
- Test: `src/test/java/com/bawdocgen/DynamicTableDocxTest.java`

- [ ] **Step 1: Add failing dynamic table test**

Create a minimal DOCX fixture or build one from test resources with a row containing:

```text
{{customers[].cif_number}}
{{customers[].customer_full_name}}
```

Use JSON:

```json
{
  "tables": {
    "customers": [
      {"cif_number": "CIF-1", "customer_full_name": "First Customer"},
      {"cif_number": "CIF-2", "customer_full_name": "Second Customer"}
    ]
  }
}
```

Assert `word/document.xml` contains both rows and no `customers[]` placeholders.

- [ ] **Step 2: Implement row cloning at XML level**

In `XmlPartTransformer`, before flat replacement:

1. Find `w:tr` elements whose descendant text contains `{{tableName[].fieldName}}`.
2. Clone the row once per table item.
3. In each clone, replace `{{tableName[].fieldName}}` with that row's field value.
4. Insert clones before the original row.
5. Remove the original placeholder row.

- [ ] **Step 3: Verify**

Run:

```bash
mvn -q -Dtest=DynamicTableDocxTest test
```

Expected: pass.

---

## Task 6: Add Fast Template Byte Cache

**Files:**
- Create: `src/main/java/com/bawdocgen/docx/DocxTemplateRepository.java`
- Modify: `src/main/java/com/bawdocgen/api/DocumentGenerator.java`
- Test: `src/test/java/com/bawdocgen/DocxTemplateRepositoryTest.java`

- [ ] **Step 1: Implement cached template loading**

```java
package com.bawdocgen.docx;

import com.bawdocgen.api.DocumentGenerationException;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DocxTemplateRepository {
    private final ConcurrentMap<String, byte[]> cache = new ConcurrentHashMap<>();

    public byte[] templateBytes(String templateId) throws DocumentGenerationException {
        byte[] cached = cache.get(templateId);
        if (cached != null) {
            return cached.clone();
        }
        byte[] loaded = readTemplate(templateId);
        byte[] existing = cache.putIfAbsent(templateId, loaded);
        return (existing == null ? loaded : existing).clone();
    }

    private byte[] readTemplate(String templateId) throws DocumentGenerationException {
        String path = "/templates/" + templateId + ".docx";
        try (InputStream inputStream = DocxTemplateRepository.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new DocumentGenerationException("DOCX-001", "Bundled template not found: " + templateId);
            }
            return readAllBytes(inputStream);
        } catch (DocumentGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentGenerationException("DOCX-001", "Failed to read bundled template: " + templateId, e);
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws java.io.IOException {
        byte[] buffer = new byte[8192];
        int read;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
```

- [ ] **Step 2: Add cache clone test**

Verify callers cannot mutate cached bytes:

```java
byte[] first = repository.templateBytes("personal-loan-application");
first[0] = 0;
byte[] second = repository.templateBytes("personal-loan-application");
assertNotEquals(0, second[0]);
```

- [ ] **Step 3: Verify**

Run:

```bash
mvn -q -Dtest=DocxTemplateRepositoryTest test
```

Expected: pass.

---

## Task 7: Make Concurrency Explicit And Tested

**Files:**
- Test: `src/test/java/com/bawdocgen/ConcurrentDocxGenerationTest.java`

- [ ] **Step 1: Add concurrent generation test**

```java
package com.bawdocgen;

import com.bawdocgen.api.DocumentGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentDocxGenerationTest {
    @Test
    void supportsConcurrentGenerationCalls() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<byte[]>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final int index = i;
                tasks.add(() -> DocumentGenerator.getInstance().generateDocx(
                        "personal-loan-application",
                        "{\"flat_mapping\":{\"customer.name\":\"Customer " + index + "\",\"loan.amount\":\"" + index + "\"}}"));
            }
            for (Future<byte[]> future : executor.invokeAll(tasks)) {
                byte[] docx = future.get();
                assertTrue(docx.length > 1000);
                assertTrue(docx[0] == 'P' && docx[1] == 'K');
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 2: Verify**

Run:

```bash
mvn -q -Dtest=ConcurrentDocxGenerationTest test
```

Expected: pass quickly. There must be no JVM-wide single-generation lock in the final design.

---

## Task 8: Update BAW Guide For DOCX Output

**Files:**
- Modify: `README.md`
- Modify: `IBM_BAW_JAR_USAGE.md`

- [ ] **Step 1: Update public class and operation**

Document:

```text
Java class: com.bawdocgen.api.DocxGenerationService
Operation:  generateDocxBase64(String templateName, String jsonPayload) : String
```

- [ ] **Step 2: Update output handling**

Replace PDF language with:

```text
The returned String is Base64-encoded DOCX bytes.
After decoding, the binary starts with ZIP header bytes PK.
Use file extension .docx and MIME type:
application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

- [ ] **Step 3: Update BAW testing warning**

Document that:

```text
jsonChars must be in the thousands for the personal-loan test payload.
If jsonChars=62, BAW is not passing the full JSON payload.
```

- [ ] **Step 4: Verify no customer name remains**

Run:

```bash
rg -n "legacycustomer|Legacy Customer|com\\.legacycustomer" README.md IBM_BAW_JAR_USAGE.md pom.xml src
```

Expected: no matches.

---

## Task 9: Final Build And Artifact Verification

**Files:**
- Verify: `target/baw-docx-generator-1.0.0-shaded.jar`

- [ ] **Step 1: Run full tests**

Run:

```bash
mvn -q clean test
```

Expected: all tests pass.

- [ ] **Step 2: Package**

Run:

```bash
mvn -q package
```

Expected:

```text
target/baw-docx-generator-1.0.0-shaded.jar
```

- [ ] **Step 3: Check Java 8 bytecode**

Run:

```bash
javap -verbose -classpath target/baw-docx-generator-1.0.0-shaded.jar \
  com.bawdocgen.api.DocxGenerationService | grep "major version"
```

Expected:

```text
major version: 52
```

- [ ] **Step 4: Check public class is present**

Run:

```bash
jar tf target/baw-docx-generator-1.0.0-shaded.jar | grep com/bawdocgen/api/DocxGenerationService.class
```

Expected:

```text
com/bawdocgen/api/DocxGenerationService.class
```

- [ ] **Step 5: Check removed PDF/docx4j runtime**

Run:

```bash
jar tf target/baw-docx-generator-1.0.0-shaded.jar | grep -E "docx4j|apache/fop|pdfbox" || true
```

Expected: no output unless docx4j is intentionally retained for non-generation validation. For fastest BAW execution, no docx4j/FOP/PDFBox classes should be in the artifact.

---

## BAW Deployment Result

After deployment, BAW should discover:

```text
com.bawdocgen.api.DocxGenerationService
generateDocxBase64(String, String) : String
```

The operation should:

- Return quickly for normal calls.
- Support concurrent process instances.
- Return Base64 DOCX, not PDF.
- Avoid `WordprocessingMLPackage.load(...)`.
- Avoid docx4j/JAXB/FOP ManagedAssetClassLoader scans.
- Avoid any `legacycustomer` names in Java class names, packages, Maven coordinates, docs, or shaded paths.

