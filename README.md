# BAW Document Generator

Self-contained Java 11 library for filling DOCX templates from flat JSON data and producing PDF bytes using docx4j + Apache FOP.

## Build

```bash
mvn clean package
```

The deployable artifact is:

```text
target/document-generator-1.0.0-shaded.jar
```

## Main API

```java
byte[] pdf = DocumentGenerator.getInstance()
    .generatePdf(templateBytes, jsonPayload);
```

Bundled templates can be placed under:

```text
src/main/resources/templates/{templateId}.docx
```

And generated with:

```java
byte[] pdf = DocumentGenerator.getInstance()
    .generatePdf("personal-loan-application", jsonPayload);
```

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

Missing values are replaced with empty text. Extra JSON keys are logged as warnings.

## Fonts

The JAR bundles open fonts from Noto and Liberation. Common template fonts are mapped as follows:

- `Calibri` -> `Liberation Sans`
- `Arial` -> `Noto Sans`
- `Times New Roman` / `Cambria` -> `Liberation Serif`
- Arabic runs -> `Noto Naskh Arabic`
- Symbol fonts -> `Noto Sans Symbols`

For predictable production PDFs, templates should be visually approved using the bundled-font output from this engine.

## Fidelity Model

This library does not call Microsoft Word, LibreOffice, Aspose, external services, or server-installed fonts.

PDF exactness means: the template is approved against this library's own docx4j/FOP rendering output. Do not expect byte-for-byte or layout-identical parity with Microsoft Word's "Save as PDF" renderer.

## Current Sample

The repository includes:

- `Personal_Loan_Application_form_template(1).docx`
- `Personal_Loan_Application_form_variables.json`
- Bundled resource template: `templates/personal-loan-application.docx`

The sample template currently has 142 detected placeholders, all present in the sample `flat_mapping`.
