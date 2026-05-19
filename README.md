# BAW DOCX Generator

Self-contained Java 8-compatible library for filling DOCX templates from JSON data and returning generated DOCX bytes as Base64 for IBM Business Automation Workflow (BAW).

## Build

```bash
mvn clean package
```

The deployable artifact is:

```text
target/baw-docx-generator-1.0.0-shaded.jar
```

Use the shaded JAR in BAW. It is compiled for Java class major version 52 so it can run on IBM BAW environments that support Java 8 bytecode.

## Public BAW API

Use the BAW-friendly facade when discovering the JAR as an IBM BAW External Java Service:

```java
String docxBase64 = new DocxGenerationService()
    .generateDocxBase64("personal-loan-application", jsonPayload);
```

Class to discover:

```text
com.bawdocgen.api.DocxGenerationService
```

Method to call:

```java
public String generateDocxBase64(String templateName, String jsonPayload)
```

The returned string is Base64-encoded DOCX content. After decoding, the binary starts with the ZIP header bytes `PK` and should be stored with:

```text
Extension: .docx
MIME type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

When generation fails, the facade throws a runtime error with a readable one-line message containing the library error code, template name, JSON preview, and root cause. This makes BAW service-flow testing easier without opening server logs for every failure.

Complete BAW deployment and usage instructions are in:

```text
IBM_BAW_JAR_USAGE.md
```

## Template Names

Bundled templates are loaded from:

```text
src/main/resources/templates/{templateName}.docx
```

For example:

```text
templateName = personal-loan-application
```

loads:

```text
/templates/personal-loan-application.docx
```

Do not include the `.docx` extension in `templateName`.

## JSON Contract

The preferred input shape is:

```json
{
  "flat_mapping": {
    "personal.first_name": "Karim",
    "children[0].name": "Elias Nasser"
  }
}
```

Placeholders in DOCX must match exactly:

```text
{{personal.first_name}}
{{children[0].name}}
```

Missing values are replaced with empty text. Extra JSON keys may be logged as warnings.

Dynamic table data can be passed with a `tables` object:

```json
{
  "tables": {
    "applicants": [
      {
        "id": "A-1",
        "full_name": "First Applicant"
      },
      {
        "id": "A-2",
        "full_name": "Second Applicant"
      }
    ]
  }
}
```

Use one placeholder row in the DOCX table:

```text
{{applicants[].id}}
{{applicants[].full_name}}
```

## Current Sample

The repository includes:

- `personal-loan-application.docx`
- `Personal_Loan_Application_form_variables.json`
- Bundled resource template: `templates/personal-loan-application.docx`

The sample template currently has 142 detected placeholders, all present in the sample `flat_mapping`.
