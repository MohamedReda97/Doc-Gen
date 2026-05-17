# IBM BAW Reusable Document Generation Library — Full Implementation Plan

## Document Information
- **Project**: Bank Audi BAW Document Generation Library
- **Type**: Reusable JAR Library
- **Target Platform**: IBM Business Automation Workflow (BAW) / WebSphere / Liberty
- **Language**: Java 11+
- **Input**: JSON data + DOCX template
- **Output**: PDF (pixel-perfect to template)
- **Supported Languages**: English, Arabic, Mixed Arabic/English
- **Date**: 2026-05-16

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture & Design Principles](#2-architecture--design-principles)
3. [Technology Stack](#3-technology-stack)
4. [Module Breakdown](#4-module-breakdown)
5. [Data Flow](#5-data-flow)
6. [Placeholder System Specification](#6-placeholder-system-specification)
7. [Template Processing Engine](#7-template-processing-engine)
8. [Arabic / RTL / Mixed-Language Support](#8-arabic--rtl--mixed-language-support)
9. [PDF Conversion Pipeline](#9-pdf-conversion-pipeline)
10. [Font Management](#10-font-management)
11. [Exception Handling & Logging](#11-exception-handling--logging)
12. [JSON Data Contract](#12-json-data-contract)
13. [Public API Specification](#13-public-api-specification)
14. [BAW Integration Guide](#14-baw-integration-guide)
15. [Testing Strategy](#15-testing-strategy)
16. [Build & Deployment](#16-build--deployment)
17. [Performance & Scaling](#17-performance--scaling)
18. [Security Considerations](#18-security-considerations)
19. [Implementation Phases](#19-implementation-phases)
20. [Deliverables Checklist](#20-deliverables-checklist)

---

## 1. Project Overview

### 1.1 Purpose
Build a generic, reusable Java library (packaged as a shaded JAR) that:
- Accepts a DOCX template and a JSON data payload
- Replaces placeholders with data values
- Handles repeating table rows dynamically
- Supports Arabic, English, and mixed-language content
- Produces a PDF that is visually identical to the DOCX template in fonts, styling, structure, and layout
- Deploys inside IBM BAW as a Java Integration Service dependency

### 1.2 Scope
| In Scope | Out of Scope |
|----------|-------------|
| DOCX template processing | DOC template support |
| PDF generation from DOCX | Direct PDF editing |
| Arabic/English/mixed text | Right-to-left UI components |
| Dynamic table row generation | Interactive PDF forms (AcroForms) |
| Checkbox rendering (☐/☑) | Digital signatures |
| Font embedding in PDF | OCR / image extraction |
| JSON input | XML/CSV input |
| BAW Java Integration Service | REST API wrapper (future phase) |

### 1.3 Key Requirements
1. **Generic**: Works with ANY DOCX template — no template-specific code
2. **Pixel-perfect PDF**: Output PDF must match the DOCX template exactly in fonts, colors, spacing, margins, tables, headers, footers
3. **Arabic support**: Full bidirectional text, complex script shaping, proper font fallback
4. **JSON input**: Single JSON string as data source
5. **Reusable JAR**: Single shaded JAR with zero external dependencies at runtime
6. **BAW-compatible**: No classloader conflicts with WebSphere/Liberty

---

## 2. Architecture & Design Principles

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           IBM BAW Process Flow                              │
│                                                                             │
│  [Human Service / System Task]                                              │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────────────────────────────┐                                    │
│  │  Java Integration Service (BAW)     │                                    │
│  │  • Receives TW Business Object      │                                    │
│  │  • Converts to JSON                 │                                    │
│  │  • Loads DOCX template              │                                    │
│  │  • Calls DocumentGenerator          │                                    │
│  └─────────────────────────────────────┘                                    │
│         │                                                                   │
│         ▼ JSON + DOCX bytes                                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    DOCUMENT GENERATION LIBRARY                       │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌───────────┐ │    │
│  │  │  Template   │→ │   Placeholder│→ │  Arabic/RTL │→ │   PDF     │ │    │
│  │  │  Loader     │  │   Engine     │  │  Processor  │  │ Converter │ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └───────────┘ │    │
│  │         ↑                    ↑              ↑              ↑        │    │
│  │         └────────────────────┴──────────────┴──────────────┘        │    │
│  │                          JSON Data                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│         │                                                                   │
│         ▼ byte[] PDF                                                        │
│  ┌─────────────────────────────────────┐                                    │
│  │  BAW Document Store / ECM / Email   │                                    │
│  └─────────────────────────────────────┘                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Design Principles

| Principle | Application |
|-----------|-------------|
| **Single Responsibility** | Each module does one thing: load, parse, replace, convert |
| **Open/Closed** | New placeholder types added without changing core engine |
| **Fail Fast** | Validate template and JSON before any processing |
| **Zero External Dependencies** | Shaded JAR — all libraries bundled, no runtime downloads |
| **Immutable Input** | Template and JSON are never modified in-place; work on copies |
| **Language Agnostic Core** | Arabic handling is a decorator, not a special case |

### 2.3 Package Structure

```
com.bankaudi.baw.document/
├── api/
│   ├── DocumentGenerator.java              # Main public API
│   ├── DocumentRequest.java                # Request DTO
│   ├── DocumentResponse.java               # Response DTO
│   └── DocumentGenerationException.java    # Checked exception
├── engine/
│   ├── TemplateLoader.java                 # DOCX loading & caching
│   ├── PlaceholderEngine.java              # Core replacement logic
│   ├── RunNormalizer.java                  # Word XML fragmentation handler
│   ├── PlaceholderSpanReplacer.java        # Multi-run placeholder replacement
│   └── TableRowRepeater.java               # Dynamic table row cloning
├── internationalization/
│   ├── ArabicPreprocessor.java             # RTL/bidi preprocessing
│   ├── LanguageDetector.java               # Auto-detect script per run
│   └── FontResolver.java                   # Font selection per script
├── pdf/
│   ├── PdfConverter.java                   # DOCX → PDF orchestrator
│   ├── FopConfiguration.java               # Apache FOP config builder
│   └── FontEmbedder.java                   # TTF font embedding
├── validation/
│   ├── TemplateValidator.java              # Pre-flight template checks
│   ├── JsonSchemaValidator.java            # JSON structure validation
│   └── PlaceholderExtractor.java           # Extract {{TOKENS}} from DOCX
├── model/
│   ├── PlaceholderType.java                # SIMPLE, TABLE_ROW, CHECKBOX
│   ├── PlaceholderDefinition.java          # Parsed placeholder metadata
│   └── FontDefinition.java                 # Font family, path, script
├── util/
│   ├── JsonParser.java                     # JSON → Map converter
│   ├── XmlUtils.java                       # OOXML helper methods
│   └── LoggingUtils.java                   # Structured logging
└── baw/
    ├── BawIntegrationService.java          # BAW-specific wrapper
    └── BawDataMapper.java                  # TWObject → JSON converter
```

---

## 3. Technology Stack

### 3.1 Core Libraries

| Library | Version | Purpose | License |
|---------|---------|---------|---------|
| **docx4j-JAXB-ReferenceImpl** | 11.4.9 | DOCX parsing, manipulation, OOXML model | Apache 2.0 |
| **docx4j-export-FO** | 11.4.9 | DOCX → XSL-FO conversion | Apache 2.0 |
| **Apache FOP** | 2.9 | XSL-FO → PDF rendering | Apache 2.0 |
| **Apache XML Graphics Commons** | 2.9 | FOP dependency | Apache 2.0 |
| **JAXB Runtime** | 2.3.x | XML binding for docx4j | CDDL 1.1 |
| **SLF4J API** | 1.7.36 | Logging facade | MIT |
| **Jackson Databind** | 2.15.x | JSON parsing | Apache 2.0 |

### 3.2 Fonts (Bundled in JAR)

| Font | File | Script | License | Path in JAR |
|------|------|--------|---------|-------------|
| **Amiri** | Amiri-Regular.ttf | Arabic | OFL 1.1 | `/fonts/amiri/Amiri-Regular.ttf` |
| **Amiri Bold** | Amiri-Bold.ttf | Arabic | OFL 1.1 | `/fonts/amiri/Amiri-Bold.ttf` |
| **Amiri Italic** | Amiri-Slanted.ttf | Arabic | OFL 1.1 | `/fonts/amiri/Amiri-Slanted.ttf` |
| **Arial Unicode MS** | ARIALUNI.TTF | Universal | Proprietary* | `/fonts/arial/ARIALUNI.TTF` |
| **Noto Sans** | NotoSans-Regular.ttf | Latin fallback | OFL 1.1 | `/fonts/noto/NotoSans-Regular.ttf` |
| **Noto Sans Arabic** | NotoSansArabic-Regular.ttf | Arabic fallback | OFL 1.1 | `/fonts/noto/NotoSansArabic-Regular.ttf` |

> *Arial Unicode MS: Must be licensed separately. Provide fallback to Noto if unavailable.

### 3.3 Build Tools

| Tool | Version | Purpose |
|------|---------|---------|
| Maven | 3.9.x | Build & dependency management |
| Maven Shade Plugin | 3.5.1 | Create uber-JAR with relocated packages |
| Maven Compiler Plugin | 3.11.x | Java 11 compilation |
| JUnit 5 | 5.10.x | Unit testing |

---

## 4. Module Breakdown

### 4.1 Module: `api` — Public Interface

**Responsibility**: Define the contract between BAW and the library.

**Key Classes**:
- `DocumentGenerator` — Singleton/factory, thread-safe, main entry point
- `DocumentRequest` — Immutable DTO: template bytes, JSON string, options
- `DocumentResponse` — Immutable DTO: PDF bytes, metadata, or error info
- `DocumentGenerationException` — Checked exception with error codes

**Thread Safety**: `DocumentGenerator` must be thread-safe. Use `ThreadLocal` for per-request state if needed.

### 4.2 Module: `engine` — Core Processing

**Responsibility**: Parse DOCX, replace placeholders, handle tables.

**Key Classes**:
- `TemplateLoader` — Load DOCX from `byte[]`, cache parsed `WordprocessingMLPackage`
- `PlaceholderEngine` — Scan document for `{{TOKENS}}`, replace with values
- `RunNormalizer` — Merge adjacent `<w:r>` runs with identical formatting
- `PlaceholderSpanReplacer` — Handle tokens split across multiple runs
- `TableRowRepeater` — Clone template rows for list data, remove directive rows

**Processing Order**:
1. Load DOCX → `WordprocessingMLPackage`
2. Normalize runs (merge fragmented text)
3. Extract all placeholder tokens
4. Validate against JSON data
5. Replace simple placeholders
6. Process table row directives (`{{#EACH:...}}`)
7. Replace table cell placeholders
8. Return modified `WordprocessingMLPackage`

### 4.3 Module: `internationalization` — Language Support

**Responsibility**: Detect scripts, apply RTL, select fonts.

**Key Classes**:
- `LanguageDetector` — Detect Arabic vs Latin per text run using Unicode blocks
- `ArabicPreprocessor` — Set `w:bidi="1"`, `w:lang="ar-SA"`, RTL font on Arabic runs
- `FontResolver` — Map script + style → font file path

**Rules**:
- Paragraph contains Arabic → entire paragraph gets `w:bidi="1"` and right justification
- Individual run contains Arabic → run gets `w:rtl`, `w:rFonts` set to Amiri, `w:lang` set to `ar-SA`
- Mixed Arabic/English in same run → use Arial Unicode MS or Amiri (both support both scripts)
- Numbers in Arabic text → remain LTR (Unicode bidi algorithm handles this)

### 4.4 Module: `pdf` — Conversion

**Responsibility**: Convert processed DOCX to pixel-perfect PDF.

**Key Classes**:
- `PdfConverter` — Orchestrate DOCX → XSL-FO → PDF pipeline
- `FopConfiguration` — Build FOP config XML with embedded font paths
- `FontEmbedder` — Ensure all used fonts are embedded in PDF

**Pipeline**:
1. Receive processed `WordprocessingMLPackage`
2. If Arabic detected → run `ArabicPreprocessor`
3. Convert DOCX → XSL-FO using docx4j-export-FO
4. Configure FOP with font registry (classpath-based TTF paths)
5. Convert XSL-FO → PDF using Apache FOP
6. Validate PDF output (header check, size check)
7. Return `byte[]`

### 4.5 Module: `validation` — Quality Gates

**Responsibility**: Fail fast with meaningful errors.

**Key Classes**:
- `TemplateValidator` — Scan DOCX for valid placeholder syntax, report invalid tokens
- `JsonSchemaValidator` — Ensure JSON structure matches expected schema (optional)
- `PlaceholderExtractor` — Extract all `{{TOKENS}}` from template for comparison with JSON keys

**Validation Rules**:
- Placeholder format: `{{UPPERCASE_UNDERSCORE}}` — regex `\{\{[A-Z_][A-Z0-9_]*\}\}`
- Table directive format: `{{#EACH:UPPERCASE}}` — regex `\{\{#EACH:[A-Z_][A-Z0-9_]*\}\}`
- Invalid examples: `{{first name}}` (lowercase, space), `{{123}}` (starts with number), `{TOKEN}` (single brace)
- Missing required placeholder → error (unless prefixed with `OPT_`)
- JSON key not used in template → warning (not error)

### 4.6 Module: `util` — Utilities

**Key Classes**:
- `JsonParser` — Jackson-based JSON → `Map<String, Object>` converter
- `XmlUtils` — OOXML namespace handling, JAXB unwrapping
- `LoggingUtils` — Correlation ID generation, structured log formatting

### 4.7 Module: `baw` — BAW-Specific Integration

**Responsibility**: Bridge between BAW TWObjects and library JSON input.

**Key Classes**:
- `BawIntegrationService` — Java Integration Service method signatures
- `BawDataMapper` — Convert `TWObject`, `TWList` to JSON string

**Note**: This module is OPTIONAL. The core library accepts JSON only. BAW integration is a thin wrapper.

---

## 5. Data Flow

### 5.1 Happy Path Flow

```
BAW Process
    │
    ▼
┌─────────────────┐
│ 1. Prepare Data │  TWObject → JSON string
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 2. Load Template│  byte[] DOCX from Managed File / classpath
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ 3. DocumentGenerator.generate()         │
│    ├── Parse JSON → Map<String, Object> │
│    ├── Load DOCX → WordprocessingMLPackage
│    ├── Validate template                │
│    ├── Normalize runs                   │
│    ├── Replace simple placeholders      │
│    ├── Process table row directives     │
│    ├── Apply Arabic preprocessing       │
│    ├── Convert DOCX → XSL-FO            │
│    ├── Configure FOP with fonts         │
│    ├── Convert XSL-FO → PDF             │
│    └── Validate PDF output              │
└────────┬────────────────────────────────┘
         │
         ▼ byte[]
┌─────────────────┐
│ 4. Return PDF   │  To BAW for ECM storage / email attachment
└─────────────────┘
```

### 5.2 Error Flow

```
Any step fails
    │
    ▼
┌─────────────────────────────────────────┐
│ DocumentGenerationException             │
│ ├── ErrorCode (enum)                    │
│ ├── ErrorMessage (human readable)       │
│ ├── Context (Map: templateId, keys, etc)│
│ ├── CorrelationId (UUID for tracing)    │
│ └── Retryable (boolean)                 │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ BAW Error Handler                       │
│ ├── Log to system log                   │
│ ├── Route to error subprocess if needed │
│ └── Retry if retryable=true             │
└─────────────────────────────────────────┘
```

---

## 6. Placeholder System Specification

### 6.1 Placeholder Syntax

| Type | Syntax | Example | Description |
|------|--------|---------|-------------|
| **Simple** | `{{TOKEN}}` | `{{FIRST_NAME}}` | Replaced with string value |
| **Optional** | `{{OPT_TOKEN}}` | `{{OPT_MIDDLE_NAME}}` | No error if missing in JSON |
| **Table Directive** | `{{#EACH:LIST_KEY}}` | `{{#EACH:OTHER_LOANS}}` | Marks row for repetition |
| **Table Cell** | `{{PREFIX_FIELD}}` | `{{LOAN_BANK_NAME}}` | Replaced within table row context |
| **Checkbox** | `{{CHECK_FIELD}}` | `{{CHECK_CAR}}` | Replaced with ☐ or ☑ |
| **Date Format** | `{{TOKEN:FORMAT}}` | `{{DOB:dd/MM/yyyy}}` | Format date (future enhancement) |

### 6.2 Naming Conventions

- All uppercase: `FIRST_NAME`, not `first_name`
- Underscore separator: `LOAN_BANK_NAME`, not `LOANBANKNAME`
- Alphanumeric + underscore only
- Max length: 64 characters
- Prefix table fields with list key: `LOAN_`, `CHILD_`, `INCOME_`

### 6.3 JSON to Placeholder Mapping

```json
{
  "first_name": "John",
  "other_loans": [
    {"bank_name": "Bank Audi", "amount": 5000},
    {"bank_name": "BNP", "amount": 3000}
  ]
}
```

Maps to:
- `{{FIRST_NAME}}` → `"John"`
- `{{#EACH:OTHER_LOANS}}` → directive (no value)
- `{{LOAN_BANK_NAME}}` → `"Bank Audi"` (first row), `"BNP"` (second row)
- `{{LOAN_AMOUNT}}` → `"5000"` (first row), `"3000"` (second row)

### 6.4 Table Row Directive Rules

1. Directive row: single cell contains `{{#EACH:LIST_KEY}}`, other cells empty
2. Template row: immediately follows directive row, contains `{{PREFIX_FIELD}}` placeholders
3. List key in JSON must be array of objects
4. Each object in array becomes one cloned row
5. Prefix = list key (uppercase) + underscore
6. If array is empty → remove directive row and template row
7. If array has N items → produce N rows, remove directive + template, insert N clones

---

## 7. Template Processing Engine

### 7.1 Run Normalization Algorithm

**Problem**: Word splits text across multiple `<w:r>` elements. `{{FIRST_NAME}}` may become:
```xml
<w:r><w:t>{{FIRST</w:t></w:r>
<w:r><w:t>_NAME}}</w:t></w:r>
```

**Solution**: Merge adjacent runs with identical formatting.

**Algorithm**:
```
For each paragraph in document:
    buffer = empty
    For each element in paragraph content:
        If element is <w:r>:
            If buffer is empty OR run.format == buffer.format:
                buffer.merge(run)  // Append text, track xml:space
            Else:
                flush(buffer)      // Create single merged run
                buffer.startNew(run)
        Else:
            flush(buffer)          // Non-run element breaks sequence
            keep(element)
    flush(buffer)                  // Final flush
```

**Formatting Comparison**: Compare `RPr` properties:
- `rFonts` (ascii, hAnsi, cs, eastAsia)
- `b` (bold), `i` (italic)
- `u` (underline)
- `sz`, `szCs` (font size)
- `color`
- `shd` (shading)
- `highlight`

**Edge Case**: If formatting changes WITHIN a placeholder (e.g., `{{FIRST` bold + `_NAME}}` normal), normalization cannot merge. Use `PlaceholderSpanReplacer` to handle multi-run spans.

### 7.2 PlaceholderSpanReplacer Algorithm

**Purpose**: Replace placeholders that span multiple runs after normalization.

**Algorithm**:
```
For each paragraph:
    Extract all text slices with (run, textElement, globalStart, text)
    Build full paragraph text
    Find all {{TOKEN}} matches in full text
    For each match (from end to start to avoid index shift):
        Determine which runs contain the match
        Replace text in first run: before + replacement + after
        Clear text in subsequent runs
        Remove empty runs from paragraph
```

### 7.3 Table Processing Algorithm

```
For each table in document:
    For each row in table:
        If row contains {{#EACH:LIST_KEY}}:
            listKey = extract key
            data = json.get(listKey)  // Must be List<Map>
            templateRow = next row

            If data is empty:
                Remove directive row
                Remove template row
            Else:
                For each item in data:
                    newRow = deepClone(templateRow)
                    For each cell in newRow:
                        For each placeholder in cell:
                            key = placeholder without prefix
                            value = item.get(key)
                            Replace placeholder with value
                    Insert newRow after directive row

                Remove directive row
                Remove template row
```

---

## 8. Arabic / RTL / Mixed-Language Support

### 8.1 Detection Strategy

**Per-Run Detection**:
```java
boolean containsArabic(String text) {
    for (char c : text.toCharArray()) {
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.ARABIC) {
            return true;
        }
    }
    return false;
}
```

**Per-Paragraph Detection**: If ANY run in paragraph contains Arabic → treat paragraph as Arabic.

### 8.2 XML Modifications for Arabic

**Paragraph Level** (`PPr`):
```xml
<w:pPr>
    <w:bidi w:val="1"/>           <!-- RTL paragraph direction -->
    <w:jc w:val="right"/>         <!-- Right justification -->
</w:pPr>
```

**Run Level** (`RPr`):
```xml
<w:rPr>
    <w:rFonts w:ascii="Amiri" w:hAnsi="Amiri" w:cs="Amiri"/>
    <w:lang w:val="ar-SA"/>       <!-- Arabic language tag -->
    <w:rtl w:val="1"/>            <!-- Right-to-left run -->
</w:rPr>
```

### 8.3 Mixed Arabic/English Text

**Example**: `رقم الحساب: ACC-12345`

- Arabic text (`رقم الحساب`) flows RTL
- Colon (`:`) is neutral, adopts surrounding direction
- English text (`ACC-12345`) flows LTR (numbers are always LTR)
- **Result**: Visual order is `ACC-12345 :رقم الحساب` (read right-to-left)

**Implementation**: Unicode Bidirectional Algorithm (UBA) is handled automatically by:
- Apache FOP (with `complex-scripts enabled="true"`)
- Embedded Arabic font with proper OpenType tables (Amiri)

### 8.4 Font Selection Logic

```
If text contains Arabic script:
    If bold requested → Amiri-Bold.ttf
    If italic requested → Amiri-Slanted.ttf
    Else → Amiri-Regular.ttf
Else (Latin only):
    Use template's original font (preserved from DOCX)
    If template font unavailable → NotoSans-Regular.ttf
```

### 8.5 Arabic Template Design Guidelines

For business users creating Arabic templates:
1. Set paragraph direction to RTL in Word before adding Arabic text
2. Use Amiri or Arial Unicode MS as the document font
3. Place English placeholders (e.g., `{{FIRST_NAME}}`) where Arabic text will appear — library will apply Arabic formatting
4. Keep table headers in Arabic; data rows will be filled dynamically
5. Test with mixed strings like: `الاسم: {{FIRST_NAME}}`

---

## 9. PDF Conversion Pipeline

### 9.1 Pipeline Stages

```
Stage 1: DOCX Preprocessing
    ├── Run normalization
    ├── Placeholder replacement
    ├── Table row expansion
    └── Arabic preprocessing (if detected)

Stage 2: DOCX → XSL-FO
    ├── docx4j-export-FO converts OOXML to XSL-FO
    ├── Preserves styles, margins, tables, images
    └── Generates intermediate FO XML

Stage 3: XSL-FO → PDF
    ├── Apache FOP parses FO XML
    ├── Loads fonts from classpath
    ├── Applies complex script shaping (Arabic)
    ├── Embeds subsetted fonts in PDF
    └── Outputs PDF/A-1b compliant file

Stage 4: Validation
    ├── PDF header check (%PDF-1.x)
    ├── Non-empty check
    ├── Size check (< 50MB)
    └── (Optional) PDF/A compliance validation
```

### 9.2 FOP Configuration

FOP config XML must be built programmatically with classpath font paths:

```xml
<fop version="1.0">
    <renderers>
        <renderer mime="application/pdf">
            <fonts>
                <font embed-url="classpath:/fonts/amiri/Amiri-Regular.ttf"
                      kerning="yes" sub-font="Amiri">
                    <font-triplet name="Amiri" style="normal" weight="normal"/>
                    <font-triplet name="Arial" style="normal" weight="normal"/>
                </font>
                <!-- Additional fonts... -->
                <auto-detect/>
            </fonts>
            <complex-scripts enabled="true"/>
        </renderer>
    </renderers>
</fop>
```

**Classpath Font Loading**: FOP must use a custom `ResourceResolver` to load fonts from JAR classpath:
```java
FopFactoryBuilder builder = new FopFactoryBuilder(baseURI);
builder.setResourceResolver(new ClassPathResourceResolver());
```

### 9.3 Pixel-Perfect Requirements

To ensure PDF matches DOCX exactly:

| Aspect | DOCX Source | PDF Output | Control |
|--------|-------------|------------|---------|
| Font family | `w:rFonts` | Embedded subset | FOP font triplet mapping |
| Font size | `w:sz` (half-points) | Exact point size | Direct conversion |
| Bold/Italic | `w:b`, `w:i` | Font weight/style | Font triplet selection |
| Color | `w:color` (hex) | RGB in PDF | Direct conversion |
| Paragraph spacing | `w:spacing` | FO space-before/after | docx4j handles |
| Margins | `w:sectPr > w:pgMar` | FO page margins | docx4j handles |
| Table borders | `w:tcBorders` | FO border properties | docx4j handles |
| Images | `w:drawing` or `w:pict` | FO external-graphic | docx4j handles |

**Limitations**:
- Complex Word art / 3D effects may not render
- Advanced table cell shading gradients may simplify
- Embedded Excel objects are not supported
- Text boxes with complex wrapping may shift slightly

---

## 10. Font Management

### 10.1 Font Bundling Strategy

All fonts bundled inside JAR at `/fonts/`:
```
/src/main/resources/fonts/
├── amiri/
│   ├── Amiri-Regular.ttf
│   ├── Amiri-Bold.ttf
│   └── Amiri-Slanted.ttf
├── arial/
│   └── ARIALUNI.TTF          (optional, proprietary)
└── noto/
    ├── NotoSans-Regular.ttf
    └── NotoSansArabic-Regular.ttf
```

### 10.2 Font Loading at Runtime

```java
public class FontRegistry {
    private final Map<String, FontDefinition> fonts = new HashMap<>();

    public void registerFonts() {
        registerFont("Amiri", "/fonts/amiri/Amiri-Regular.ttf", 
                     "normal", "normal");
        registerFont("Amiri", "/fonts/amiri/Amiri-Bold.ttf",
                     "normal", "bold");
        // ... etc
    }

    public InputStream loadFont(String family, String style, String weight) {
        String path = resolvePath(family, style, weight);
        return getClass().getResourceAsStream(path);
    }
}
```

### 10.3 Font Substitution Map

If template uses a font not bundled:

| Template Font | Substitute With | Reason |
|---------------|-----------------|--------|
| Arial | Amiri (Arabic) / Noto Sans (Latin) | Universal coverage |
| Times New Roman | Noto Sans | Clean fallback |
| Calibri | Noto Sans | Metric-compatible |
| Tahoma | Amiri | Similar Arabic coverage |
| Any Arabic font | Amiri | Best Arabic OpenType support |

### 10.4 Font Embedding in PDF

- All fonts used in document MUST be embedded as subsets
- FOP `embed-url` attribute triggers embedding
- Subsetting reduces PDF size (only used glyphs embedded)
- Required for print fidelity and PDF/A compliance

---

## 11. Exception Handling & Logging

### 11.1 Exception Hierarchy

```
DocumentGenerationException (checked)
├── TemplateException
│   ├── TemplateNotFoundException
│   ├── TemplateInvalidException
│   └── PlaceholderMismatchException
├── DataException
│   ├── JsonParseException
│   ├── MissingRequiredFieldException
│   └── InvalidDataTypeException
├── ConversionException
│   ├── PdfConversionException
│   ├── FontNotFoundException
│   └── ArabicRenderingException
└── SystemException
    ├── OutOfMemoryException
    └── TimeoutException
```

### 11.2 Error Codes

| Code | Category | Description | Retryable |
|------|----------|-------------|-----------|
| `DOC-001` | Template | Template not found | No |
| `DOC-002` | Template | Invalid placeholder syntax | No |
| `DOC-003` | Data | Missing required field | No |
| `DOC-004` | Data | JSON parse error | No |
| `DOC-005` | Data | Type mismatch (expected array) | No |
| `DOC-006` | Conversion | PDF conversion failed | Yes (3x) |
| `DOC-007` | Conversion | Font not found/missing | No |
| `DOC-008` | Conversion | Arabic rendering error | Yes (3x) |
| `DOC-009` | System | Out of memory | Yes (3x) |
| `DOC-010` | System | Timeout (>30s) | Yes (3x) |
| `DOC-999` | System | Unknown error | Yes (1x) |

### 11.3 Logging Requirements

- **Framework**: SLF4J with JUL (Java Util Logging) binding for BAW compatibility
- **Correlation ID**: Every request gets UUID, propagated through all log lines
- **Log Levels**:
  - `INFO`: Request start/end, template loaded, validation passed
  - `DEBUG`: Run normalization details, placeholder replacement steps
  - `WARN`: Unused JSON keys, font substitutions
  - `ERROR`: All exceptions with full stack trace and context
- **Log Format**: `[CORRELATION_ID] [TIMESTAMP] [LEVEL] [CLASS] MESSAGE`

### 11.4 Context Data in Exceptions

Every exception carries:
- `correlationId`: UUID for tracing
- `templateId`: Template identifier
- `placeholderCount`: Number of placeholders found
- `missingPlaceholders`: Set of missing keys
- `processingStage`: Which stage failed (LOAD, VALIDATE, REPLACE, CONVERT)
- `durationMs`: Time elapsed before failure

---

## 12. JSON Data Contract

### 12.1 Top-Level Structure

```json
{
  "_meta": {
    "template_id": "loan_application_en",
    "language": "en",
    "generation_date": "2026-05-16T10:30:00Z"
  },
  "data": {
    "FIRST_NAME": "John",
    "LAST_NAME": "Doe",
    "AMOUNT_REQUESTED": "50000",
    "CHECK_CAR": true,
    "OTHER_LOANS": [
      {
        "BANK_NAME": "Bank Audi",
        "OUTSTANDING_AMOUNT": "15000",
        "CURRENCY": "USD"
      }
    ]
  }
}
```

### 12.2 Field Types

| JSON Type | Placeholder Handling | Example |
|-----------|---------------------|---------|
| `string` | Direct replacement | `"John"` → `John` |
| `number` | Convert to string | `50000` → `50000` |
| `boolean` | Checkbox: `true`→☑, `false`→☐ | `true` → ☑ |
| `array` | Table row directive required | See 12.3 |
| `null` | Empty string replacement | `null` → `""` |
| `object` | Not supported at top level | Nesting via prefix convention |

### 12.3 Array Data for Tables

```json
{
  "OTHER_LOANS": [
    {
      "BANK_NAME": "Bank Audi",
      "CLIENT_SINCE": "2019",
      "ACCOUNT_TYPE": "Current",
      "TYPE": "Home",
      "INSTITUTION": "Bank Audi sal",
      "OUTSTANDING_AMOUNT": "15000",
      "MONTHLY_PAYMENT": "500",
      "CURRENCY": "USD",
      "MATURITY": "2028-12-31"
    },
    {
      "BANK_NAME": "BNP Paribas",
      "CLIENT_SINCE": "2020",
      "ACCOUNT_TYPE": "Savings",
      "TYPE": "Car",
      "INSTITUTION": "BNP",
      "OUTSTANDING_AMOUNT": "8000",
      "MONTHLY_PAYMENT": "300",
      "CURRENCY": "USD",
      "MATURITY": "2027-06-30"
    }
  ]
}
```

**Mapping Rule**: Array key `OTHER_LOANS` → table directive `{{#EACH:OTHER_LOANS}}` → cell placeholders `{{LOAN_BANK_NAME}}`, `{{LOAN_CLIENT_SINCE}}`, etc.

### 12.4 Optional Fields

Prefix with `OPT_` in template to make optional:
- Template: `{{OPT_MIDDLE_NAME}}`
- JSON: `"OPT_MIDDLE_NAME": "Robert"` or omit key entirely
- If missing → replaced with empty string, no error

### 12.5 Arabic JSON Example

```json
{
  "_meta": {
    "template_id": "loan_application_ar",
    "language": "ar"
  },
  "data": {
    "FIRST_NAME": "أحمد",
    "LAST_NAME": "محمد",
    "AMOUNT_REQUESTED": "50000",
    "CURRENCY": "دولار أمريكي",
    "CHECK_CAR": true,
    "HOME_ADDRESS": "بيروت، لبنان"
  }
}
```

---

## 13. Public API Specification

### 13.1 Main API Class

```java
package com.bankaudi.baw.document.api;

/**
 * Thread-safe document generator.
 * 
 * Usage:
 *   DocumentGenerator generator = DocumentGenerator.getInstance();
 *   DocumentResponse response = generator.generate(request);
 *   byte[] pdf = response.getPdfBytes();
 */
public class DocumentGenerator {

    private static final DocumentGenerator INSTANCE = new DocumentGenerator();

    public static DocumentGenerator getInstance() { return INSTANCE; }

    /**
     * Generate PDF from DOCX template and JSON data.
     * 
     * @param request Contains template bytes, JSON string, and options
     * @return DocumentResponse with PDF bytes or error information
     * @throws DocumentGenerationException for fatal errors
     */
    public DocumentResponse generate(DocumentRequest request) 
        throws DocumentGenerationException;

    /**
     * Validate template without generating PDF.
     * 
     * @param templateBytes DOCX template
     * @return ValidationResult with placeholder list and errors
     */
    public ValidationResult validateTemplate(byte[] templateBytes);

    /**
     * Pre-load and cache template for faster subsequent generation.
     * 
     * @param templateId Unique template identifier
     * @param templateBytes DOCX template
     */
    public void preloadTemplate(String templateId, byte[] templateBytes);

    /**
     * Clear template cache.
     */
    public void clearCache();
}
```

### 13.2 Request/Response DTOs

```java
public class DocumentRequest {
    private final byte[] templateBytes;      // DOCX file bytes
    private final String jsonData;           // JSON string per section 12
    private final String templateId;         // For caching and logging
    private final GenerationOptions options; // Optional settings

    // Builder pattern constructor
    public static Builder builder() { return new Builder(); }
}

public class GenerationOptions {
    private boolean enableArabicDetection = true;
    private boolean embedFonts = true;
    private boolean pdfA1bCompliance = true;
    private long timeoutMs = 30000;
    private String fallbackFont = "Amiri";

    // Getters, builder...
}

public class DocumentResponse {
    private final boolean success;
    private final byte[] pdfBytes;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> errorContext;
    private final long durationMs;
    private final String correlationId;

    public boolean isSuccess();
    public byte[] getPdfBytes();           // null if failed
    public String getErrorCode();          // null if success
    public String getErrorMessage();       // null if success
    public boolean isRetryable();
}

public class ValidationResult {
    private final boolean valid;
    private final Set<String> placeholders;
    private final Set<String> invalidPlaceholders;
    private final List<String> errors;
    private final List<String> warnings;
}
```

### 13.3 Usage Example

```java
// BAW Java Integration Service
public String generateLoanPdf(byte[] templateBytes, TWObject twData) {
    try {
        // 1. Convert BAW data to JSON
        String json = BawDataMapper.toJson(twData);

        // 2. Build request
        DocumentRequest request = DocumentRequest.builder()
            .templateBytes(templateBytes)
            .jsonData(json)
            .templateId("loan_application_v1")
            .options(GenerationOptions.builder()
                .enableArabicDetection(true)
                .pdfA1bCompliance(true)
                .build())
            .build();

        // 3. Generate
        DocumentGenerator generator = DocumentGenerator.getInstance();
        DocumentResponse response = generator.generate(request);

        // 4. Handle result
        if (response.isSuccess()) {
            return saveToEcm(response.getPdfBytes());
        } else {
            logError(response.getErrorCode() + ": " + response.getErrorMessage());
            return "ERROR:" + response.getErrorCode();
        }

    } catch (Exception e) {
        logError("Unexpected error: " + e.getMessage());
        return "ERROR:UNEXPECTED";
    }
}
```

---

## 14. BAW Integration Guide

### 14.1 JAR Deployment

1. **Build shaded JAR**:
   ```bash
   mvn clean package -Pshade
   ```
   Output: `document-generator-1.0.0-shaded.jar`

2. **Deploy to BAW**:
   - Option A: Add JAR to Process App as **Server File**
   - Option B: Place in `<was_profile>/shared` for cross-app use
   - Option C: For Liberty, add to `<library>` in `server.xml`

3. **Classloader Configuration**:
   - Ensure JAR loads in application classloader, not parent-first
   - Test for `ClassCastException` with existing BAW Apache libraries

### 14.2 Java Integration Service Setup

1. Create new **Java Integration Service** in Process Designer
2. Add JAR to service dependencies
3. Define method signature:
   ```java
   public String generateDocument(byte[] templateBytes, 
                                   TWObject businessData,
                                   String templateId,
                                   String language)
   ```
4. Map BAW `TWObject` to JSON using `BawDataMapper`
5. Call `DocumentGenerator.generate()`
6. Return ECM document ID or error code

### 14.3 BAW Data Mapping

```java
public class BawDataMapper {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String toJson(TWObject twObject) {
        Map<String, Object> map = convertTwObject(twObject);

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("_meta", buildMeta(twObject));
        wrapper.put("data", map);

        try {
            return mapper.writeValueAsString(wrapper);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize BAW data", e);
        }
    }

    private static Map<String, Object> convertTwObject(TWObject twObject) {
        Map<String, Object> result = new HashMap<>();

        // Iterate TWObject properties
        for (String propertyName : twObject.getPropertyNames()) {
            Object value = twObject.getProperty(propertyName);

            if (value instanceof TWObject) {
                result.put(propertyName.toUpperCase(), convertTwObject((TWObject) value));
            } else if (value instanceof TWList) {
                result.put(propertyName.toUpperCase(), convertTwList((TWList) value));
            } else {
                result.put(propertyName.toUpperCase(), value);
            }
        }

        return result;
    }

    private static List<Map<String, Object>> convertTwList(TWList twList) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < twList.getArraySize(); i++) {
            TWObject item = twList.getArrayData(i);
            result.add(convertTwObject(item));
        }
        return result;
    }
}
```

### 14.4 Process Flow Design

```
[Start]
   │
   ▼
[Prepare Data] ──► [Validate Required Fields]
   │                      │
   ▼                      ▼ (missing)
[Load Template]    [Request Data Fix]
   │                      │
   ▼                      ▼
[Call Document    [Retry]
 Generator]              │
   │                      ▼
   ▼                [Escalate if >3 retries]
[Check Result]
   │
   ├─► Success ──► [Save to ECM] ──► [Continue]
   │
   └─► Error ──► [Check Retryable]
          │
          ├─► Yes ──► [Retry Gate (max 3)] ──► [Re-call Generator]
          │
          └─► No ──► [Log Error] ──► [Notify Admin] ──► [Error Subprocess]
```

---

## 15. Testing Strategy

### 15.1 Unit Tests

| Test Class | Coverage |
|------------|----------|
| `RunNormalizerTest` | Merge adjacent runs, preserve formatting, handle xml:space |
| `PlaceholderEngineTest` | Simple replacement, missing key, null value, empty string |
| `PlaceholderSpanReplacerTest` | Multi-run token spanning, formatting change mid-token |
| `TableRowRepeaterTest` | Single row, multiple rows, empty array, missing directive |
| `LanguageDetectorTest` | Arabic detection, Latin only, mixed text, empty string |
| `ArabicPreprocessorTest` | RTL flag setting, font change, language tag |
| `JsonParserTest` | Valid JSON, invalid JSON, nested objects, arrays |
| `TemplateValidatorTest` | Valid placeholders, invalid syntax, extract all tokens |

### 15.2 Integration Tests

| Test Class | Scenario |
|------------|----------|
| `EndToEndEnglishTest` | Full English loan form → PDF, verify text content |
| `EndToEndArabicTest` | Full Arabic loan form → PDF, verify RTL direction |
| `EndToEndMixedTest` | Mixed Arabic/English form → PDF, verify bidi |
| `EndToEndTableTest` | Form with 3 tables, 5 rows each → PDF, verify all rows present |
| `EndToEndCheckboxTest` | Form with 10 checkboxes, mixed true/false → PDF |
| `LargeDocumentTest` | 100-page document, 1000 placeholders → Performance check |
| `ConcurrentTest` | 10 threads generating simultaneously → Thread safety |

### 15.3 Visual Regression Tests

- Generate PDF from known template + known data
- Compare with golden master PDF using image diff (e.g., ImageMagick)
- Tolerance: 1% pixel difference (accounts for font subsetting variations)
- Run on every build in CI/CD pipeline

### 15.4 Test Templates

Create minimal test templates:
- `test_simple.docx` — Single placeholder
- `test_table.docx` — One table with directive
- `test_arabic.docx` — Arabic text with placeholder
- `test_mixed.docx` — Mixed Arabic/English
- `test_full_loan.docx` — Simplified version of production loan form

---

## 16. Build & Deployment

### 16.1 Maven POM Structure

```xml
<project>
    <groupId>com.bankaudi.baw</groupId>
    <artifactId>document-generator</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <docx4j.version>11.4.9</docx4j.version>
        <fop.version>2.9</fop.version>
        <jackson.version>2.15.2</jackson.version>
    </properties>

    <dependencies>
        <!-- docx4j -->
        <dependency>
            <groupId>org.docx4j</groupId>
            <artifactId>docx4j-JAXB-ReferenceImpl</artifactId>
            <version>${docx4j.version}</version>
        </dependency>
        <dependency>
            <groupId>org.docx4j</groupId>
            <artifactId>docx4j-export-fo</artifactId>
            <version>${docx4j.version}</version>
        </dependency>

        <!-- Apache FOP -->
        <dependency>
            <groupId>org.apache.xmlgraphics</groupId>
            <artifactId>fop-core</artifactId>
            <version>${fop.version}</version>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>1.7.36</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <relocations>
                                <!-- Critical: Relocate to avoid BAW classloader conflicts -->
                                <relocation>
                                    <pattern>org.apache.poi</pattern>
                                    <shadedPattern>com.bankaudi.shaded.poi</shadedPattern>
                                </relocation>
                                <relocation>
                                    <pattern>org.docx4j</pattern>
                                    <shadedPattern>com.bankaudi.shaded.docx4j</shadedPattern>
                                </relocation>
                                <relocation>
                                    <pattern>com.fasterxml.jackson</pattern>
                                    <shadedPattern>com.bankaudi.shaded.jackson</shadedPattern>
                                </relocation>
                            </relocations>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

### 16.2 Relocation Rules

| Original Package | Shaded Package | Reason |
|-----------------|----------------|--------|
| `org.apache.poi` | `com.bankaudi.shaded.poi` | BAW may include older POI |
| `org.docx4j` | `com.bankaudi.shaded.docx4j` | Avoid version conflicts |
| `com.fasterxml.jackson` | `com.bankaudi.shaded.jackson` | BAW may include different Jackson |
| `org.slf4j` | Keep as-is | BAW uses SLF4J, compatible |

**Do NOT relocate**:
- `javax.xml.bind` (JAXB) — BAW provides this
- `org.w3c.dom` — Standard JDK
- `org.slf4j` — Logging facade, safe to share

### 16.3 CI/CD Pipeline

```
[Developer Push]
    │
    ▼
[Build & Unit Tests] ──► Fail? ──► Notify Developer
    │
    ▼
[Integration Tests] ──► Fail? ──► Notify Developer
    │
    ▼
[Visual Regression] ──► Fail? ──► Notify Developer
    │
    ▼
[Package Shaded JAR]
    │
    ▼
[Deploy to Artifactory/Nexus]
    │
    ▼
[Deploy to BAW DEV]
    │
    ▼
[Smoke Tests on BAW]
    │
    ▼
[Promote to BAW UAT → PROD]
```

---

## 17. Performance & Scaling

### 17.1 Performance Targets

| Metric | Target | Notes |
|--------|--------|-------|
| Simple form (1 page, 20 fields) | < 2 seconds | Including PDF conversion |
| Complex form (5 pages, 100 fields, 3 tables) | < 5 seconds | Including PDF conversion |
| Arabic form (same complexity) | < 7 seconds | Arabic shaping adds overhead |
| Concurrent requests | 10 simultaneous | Per BAW JVM instance |
| Memory per request | < 256 MB heap | Peak during FOP conversion |
| Template cache | Unlimited | Templates cached in memory |

### 17.2 Optimization Strategies

1. **Template Caching**: Parse DOCX once, cache `WordprocessingMLPackage`. Clone for each request.
2. **Font Caching**: Load TTF fonts once, reuse in FOP.
3. **FOP Reuse**: Reuse `FopFactory` instance (thread-safe after initialization).
4. **Lazy Conversion**: Only run Arabic preprocessor if Arabic text detected.
5. **Streaming**: Use `ByteArrayOutputStream` with initial capacity estimate.

### 17.3 JVM Tuning for BAW

```bash
# Recommended JVM options for document generation
-Xms512m -Xmx2048m           # Heap size
-XX:+UseG1GC                 # G1 garbage collector
-XX:MaxGCPauseMillis=200     # Max GC pause
-Dfop.fonts.cache-dir=/tmp/fop-cache  # FOP font cache
```

### 17.4 Monitoring

Expose via JMX or logging:
- `document.generation.count` — Total generations
- `document.generation.duration` — Histogram of generation times
- `document.generation.errors` — Error count by code
- `document.cache.size` — Number of cached templates
- `document.cache.hit_rate` — Cache hit percentage

---

## 18. Security Considerations

### 18.1 Input Validation

- **JSON size limit**: Max 10MB to prevent memory exhaustion
- **Template size limit**: Max 50MB
- **Placeholder count limit**: Max 10,000 per document
- **Table row limit**: Max 500 rows per table
- **No script execution**: JSON must not contain executable code

### 18.2 PDF Security

- **No JavaScript**: Disable PDF JavaScript in FOP config
- **No external links**: Validate no `file://` or `http://` in document
- **Font embedding**: Prevents font substitution attacks
- **PDF/A-1b**: Archival format, no encryption by default

### 18.3 BAW Security

- JAR runs with BAW application security context
- No file system access outside temp directory
- Templates loaded from BAW Managed Files (access-controlled)
- JSON data from BAW Business Objects (already authenticated)

---

## 19. Implementation Phases

### Phase 1: Foundation (Week 1-2)
- [ ] Project setup: Maven POM, package structure, logging
- [ ] DOCX loading with docx4j
- [ ] JSON parsing with Jackson
- [ ] Basic placeholder replacement (single run, no normalization)
- [ ] Unit tests for core classes

### Phase 2: Robustness (Week 3-4)
- [ ] Run normalization engine
- [ ] Placeholder span replacer (multi-run)
- [ ] Table row repeater
- [ ] Template validator
- [ ] Exception hierarchy and error codes
- [ ] Comprehensive unit tests

### Phase 3: Arabic Support (Week 5-6)
- [ ] Language detector
- [ ] Arabic preprocessor (RTL, fonts, language tags)
- [ ] Font bundling and registry
- [ ] FOP configuration with Arabic fonts
- [ ] Arabic test templates and integration tests

### Phase 4: PDF Pipeline (Week 7-8)
- [ ] DOCX → XSL-FO conversion
- [ ] XSL-FO → PDF conversion with FOP
- [ ] Font embedding in PDF
- [ ] PDF validation
- [ ] Pixel-perfect comparison tests

### Phase 5: BAW Integration (Week 9-10)
- [ ] BAW data mapper (TWObject → JSON)
- [ ] Java Integration Service wrapper
- [ ] Shaded JAR build with relocations
- [ ] BAW deployment and testing
- [ ] Process flow design with error handling

### Phase 6: Hardening (Week 11-12)
- [ ] Performance testing and optimization
- [ ] Concurrent load testing
- [ ] Memory profiling and leak detection
- [ ] Security review
- [ ] Documentation finalization
- [ ] UAT and production deployment

---

## 20. Deliverables Checklist

### Code Deliverables
- [ ] Source code in Git repository
- [ ] Shaded JAR (`document-generator-1.0.0-shaded.jar`)
- [ ] Javadoc for all public APIs
- [ ] Unit test suite (>80% coverage)
- [ ] Integration test suite
- [ ] Visual regression test suite

### Documentation Deliverables
- [ ] **This implementation plan** (this document)
- [ ] API reference (Javadoc)
- [ ] Template design guide for business users
- [ ] BAW integration guide
- [ ] Troubleshooting guide (error codes, solutions)
- [ ] Performance tuning guide

### Template Deliverables
- [ ] English loan application template (production-ready)
- [ ] Arabic loan application template (production-ready)
- [ ] Test templates (simple, table, Arabic, mixed)
- [ ] Template validation tool

### Deployment Deliverables
- [ ] BAW Process App with Java Integration Service
- [ ] Deployment scripts for DEV/UAT/PROD
- [ ] Monitoring dashboard configuration
- [ ] Runbook for operations team

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **BAW** | IBM Business Automation Workflow |
| **DOCX** | Microsoft Word Open XML document format |
| **OOXML** | Office Open XML — the XML schema for DOCX |
| **XSL-FO** | Extensible Stylesheet Language Formatting Objects |
| **FOP** | Apache Formatting Objects Processor |
| **SDT** | Structured Document Tag (Word Content Control) |
| **RTL** | Right-to-Left text direction |
| **Bidi** | Bidirectional text algorithm |
| **TWObject** | BAW Teamworks Business Object |
| **TWList** | BAW Teamworks List Object |
| **Shaded JAR** | Uber-JAR with relocated packages to avoid conflicts |

## Appendix B: Reference Documents

- [docx4j Documentation](https://www.docx4java.org/trac/docx4j)
- [Apache FOP Documentation](https://xmlgraphics.apache.org/fop/)
- [IBM BAW Java Integration Service Guide](https://www.ibm.com/docs/en/baw)
- [OOXML Specification (ISO/IEC 29500)](https://www.iso.org/standard/71691.html)
- [Unicode Bidirectional Algorithm (UAX #9)](https://unicode.org/reports/tr9/)

---

*Document Version: 1.0*
*Author: IBM BAW Solution Architect*
*Date: 2026-05-16*
*Status: Implementation Ready*
