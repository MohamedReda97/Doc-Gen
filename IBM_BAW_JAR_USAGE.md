# IBM BAW JAR Usage Guide

This guide explains how to deploy and call the `document-generator` JAR from IBM Business Automation Workflow (BAW), pass the template name and JSON payload as strings, and receive the generated PDF.

Official IBM references:

- IBM BAW Java service discovery: https://www.ibm.com/docs/en/baw/24.0.x?topic=service-invoking-java
- IBM BAW integration samples: https://github.com/ibmbpm/Integration-Samples

## 1. Important Compatibility Note

The earlier BAW error was:

```text
UnsupportedClassVersionError: JVMCFRE199E bad major version 55.0
the maximum supported major version is 52.0
```

Meaning:

- `major version 55.0` = Java 11 bytecode.
- `major version 52.0` = Java 8 bytecode.
- Your BAW runtime is using a Java 8-compatible JVM, so the JAR must be compiled for Java 8.

The project is now configured to build Java 8 bytecode using Maven compiler `release 8`, and the docx4j dependency line was moved to the Java 8-compatible `8.3.x` family.

## 2. Public BAW Service Class

Use this class in BAW:

```text
com.bankaudi.baw.document.api.BawDocumentService
```

Use this method:

```java
public String generatePdfBase64(String templateName, String jsonPayload)
```

Why this wrapper exists:

- BAW Java Integration Service discovery supports public Java classes and public methods.
- IBM documents supported Java integration parameter and return types; `String` is supported.
- Raw `byte[]` output is not listed in the supported return type table, so the BAW-facing method returns the PDF as a Base64 `String`.
- The lower-level Java API still exists as `DocumentGenerator.getInstance().generatePdf(...)` for normal Java callers.

## 3. Template Name Rule

The method input `templateName` maps to a bundled DOCX resource:

```text
src/main/resources/templates/{templateName}.docx
```

Example:

```text
templateName = personal-loan-application
```

Loads:

```text
/templates/personal-loan-application.docx
```

To add a new template:

1. Put the DOCX file under `src/main/resources/templates/`.
2. Name it with a stable ID, for example `pledge-letter-fresh-usd.docx`.
3. Call the service with `templateName = pledge-letter-fresh-usd`.
4. Rebuild and redeploy the JAR.

## 4. JSON Payload Contract

Pass the JSON payload as one string.

Preferred shape:

```json
{
  "flat_mapping": {
    "customer.name": "Ahmed Mohamed Nasser",
    "loan.amount": "10000"
  },
  "tables": {
    "customers": [
      {
        "cif_number": "CIF-1",
        "customer_full_name": "First Customer"
      },
      {
        "cif_number": "CIF-2",
        "customer_full_name": "Second Customer"
      }
    ]
  }
}
```

DOCX placeholders must match the keys exactly:

```text
{{customer.name}}
{{loan.amount}}
```

For dynamic table rows, put one placeholder row in the DOCX table:

```text
{{customers[].cif_number}}
{{customers[].customer_full_name}}
```

The engine clones that row once per item in `tables.customers`.

### Ready Test Payload

For the bundled personal loan template, use this test JSON file:

```text
BAW_personal-loan-application-test-payload.json
```

It contains exactly the 142 placeholders used by:

```text
src/main/resources/templates/personal-loan-application.docx
```

Use these values in the BAW service task:

```text
templateName = personal-loan-application
jsonPayload  = contents of BAW_personal-loan-application-test-payload.json as a string
```

This payload is meant for service-flow testing, so it avoids the missing-placeholder warnings that appear when the JSON does not match the template.

## 5. Build The BAW-Compatible JAR

From the project root:

```bash
mvn clean package
```

Deploy this file to BAW:

```text
target/document-generator-1.0.0-shaded.jar
```

Do not deploy the smaller non-shaded JAR unless you also deploy every dependency. The shaded JAR includes docx4j, FOP, Jackson, fonts, and bundled templates.

Optional bytecode check:

```bash
javap -verbose -classpath target/document-generator-1.0.0-shaded.jar \
  com.bankaudi.baw.document.api.BawDocumentService | grep "major version"
```

Expected:

```text
major version: 52
```

## 6. Add The JAR To IBM BAW

In Process Designer or Workflow Center:

1. Open the Process App or Toolkit that will call the generator.
2. Add `target/document-generator-1.0.0-shaded.jar` as a managed/server file.
3. If the JAR is placed in a Toolkit, add that Toolkit as a dependency to the Process App.
4. Snapshot or save the Process App/Toolkit so the JAR is available for service discovery.

IBM notes that the JAR must be added before creating the external service, and that updating a managed JAR does not automatically refresh an already discovered Java service. If you change public method signatures, create a new external service or rediscover the class.

## 7. Create The External Java Service

1. Create a new External Service.
2. Choose Java service from Server File.
3. Select the managed file `document-generator-1.0.0-shaded.jar`.
4. Select Java class:

```text
com.bankaudi.baw.document.api.BawDocumentService
```

Do not select `com.bankaudi.baw.document.api.DocumentGenerator` for the BAW service flow. That lower-level class exposes overloaded `generatePdf` methods and BAW may display the `byte[]` return value as an `Integer List`, which is confusing and harder to store as a PDF.

If the class list is alphabetical, look under `B`, not under `D`.

If `BawDocumentService` is not visible:

1. Rebuild the project with `mvn clean package`.
2. Upload `target/document-generator-1.0.0-shaded.jar` again.
3. Remove/recreate or rediscover the external service.
4. Confirm the JAR contains the class:

```bash
jar tf target/document-generator-1.0.0-shaded.jar | grep BawDocumentService
```

Expected:

```text
com/bankaudi/baw/document/api/BawDocumentService.class
```

5. Finish the wizard.
6. Confirm the discovered operation:

```text
generatePdfBase64(String, String) -> String
```

BAW may show generic input names such as `Parameter 1` and `Parameter 2`. Rename or document them as:

- `Parameter 1`: `templateName`
- `Parameter 2`: `jsonPayload`
- output: `pdfBase64`

### If You Already Selected `DocumentGenerator`

Your screenshots show this operation list:

```text
extractPlaceholders
generatePdf(Integer List, String)
generatePdf(String, String)
getInstance
validateTemplate
```

For the current requirement, do not use this service definition. Create a new external service using `BawDocumentService` instead.

If server logs show this after you delete or edit assets:

```text
ClassNotFoundException: com.bankaudi.baw.document.api.DocumentGenerator
TWProcess validateJavaBinding com.bankaudi.baw.document.api.DocumentGenerator
```

BAW still has an old Java binding pointing to the low-level `DocumentGenerator` class. Remove that old external service or service task binding, recreate the external service from `BawDocumentService`, save the process app, and create a new snapshot.

If you must test the existing `DocumentGenerator` service temporarily, the only relevant option is:

```text
generatePdf(String Parameter 1, String Parameter 2) : Integer Return Value
```

Where:

- `Parameter 1` = template name, for example `personal-loan-application`
- `Parameter 2` = JSON payload string

But the return value is the raw PDF `byte[]` shown by BAW as an integer list. The recommended `BawDocumentService.generatePdfBase64` method returns a normal `String`, which is much easier to map, store, decode, and send.

## 8. Call The Service From A Service Flow

1. Add a Service Task to the Service Flow.
2. In the Implementation section, select the external Java service.
3. Select the `generatePdfBase64` operation.
4. In Data Mapping, map:

```text
templateName  -> "personal-loan-application"
jsonPayload   -> tw.local.jsonPayload
output String -> tw.local.pdfBase64
```

Example JavaScript to prepare the JSON string:

```javascript
tw.local.jsonPayload = JSON.stringify({
  flat_mapping: {
    "customer.name": tw.local.customerName,
    "loan.amount": tw.local.loanAmount
  }
});
```

## 9. Get The Output PDF

The service output is a Base64 string containing the full PDF bytes.

To verify the output:

1. Confirm `tw.local.pdfBase64` is not empty.
2. Decode it as Base64.
3. The decoded bytes must start with:

```text
%PDF-
```

For downstream use, choose one of these patterns:

- Store `pdfBase64` as process data if the next system accepts Base64 documents.
- Decode `pdfBase64` in a follow-up Java service and write it to an ECM/document repository.
- Decode `pdfBase64` and pass the bytes to an email or document attachment integration that supports binary content.
- For troubleshooting only, decode it outside BAW and save it as `output.pdf`.

Example local verification outside BAW:

```bash
base64 --decode generated-pdf.txt > output.pdf
```

Where `generated-pdf.txt` contains the exact returned `pdfBase64` string.

## 10. Local Smoke Test Before Deploying

You can test generation locally with:

```bash
./test-generate-pdf.sh \
  "Pledge_Letter_FRESH_USD.docx" \
  Pledge_Letter_FRESH_USD_variables.json \
  target/generated-pdfs/pledge-letter.pdf
```

You can also run the automated tests:

```bash
mvn clean test
```

## 11. Error Handling In BAW

The BAW-facing method:

```text
BawDocumentService.generatePdfBase64(String templateName, String jsonPayload)
```

throws a runtime error with a detailed single-line message when generation fails. This is intentional: the error is visible in BAW testing/debugging and can be handled by an error boundary or error event without opening server logs each time.

The error message format is:

```text
BAW document generation failed | code=DOC-004 | message=Failed to parse JSON flat_mapping | templateName=personal-loan-application | jsonPreview={not-json | rootCause=JsonParseException: Unexpected character...
```

Common fields:

- `code`: library error code.
- `message`: high-level failure.
- `templateName`: the template name passed from BAW.
- `jsonPreview`: first part of the JSON payload, trimmed to keep the error readable.
- `rootCause`: the deepest Java error, usually the most useful part while testing.

Recommended BAW testing flow:

1. Put the Java service task inside an error-handled service flow path.
2. Add an error boundary/error handler around the Java service task if your BAW version supports it.
3. In the error path, map/display the exception message in a temporary debug variable, coach, or system log.
4. During testing, intentionally pass a bad template name or invalid JSON once to confirm that the readable error appears.

Example missing-template error:

```text
BAW document generation failed | code=DOC-001 | message=Bundled template not found: missing-template | templateName=missing-template | jsonPreview={"flat_mapping":{}} | rootCause=DocumentGenerationException: Bundled template not found: missing-template
```

Example invalid-JSON error:

```text
BAW document generation failed | code=DOC-004 | message=Failed to parse JSON flat_mapping | templateName=personal-loan-application | jsonPreview={not-json | rootCause=JsonParseException: Unexpected character...
```

For successful calls, the method still returns only the Base64 PDF string.

## 12. Troubleshooting

### `bad major version 55.0`

Cause: The JAR was compiled for Java 11.

Fix:

1. Build the updated project with `mvn clean package`.
2. Upload `target/document-generator-1.0.0-shaded.jar`.
3. Verify the class major version is `52`.
4. Rediscover/recreate the BAW external Java service.

### Class cannot be discovered

Check:

- The class is public: `com.bankaudi.baw.document.api.BawDocumentService`.
- The class has a public no-argument constructor.
- The method is public.
- Method inputs and output are supported BAW types; this wrapper uses only `String`.
- The shaded JAR, not the plain JAR, was uploaded.

### Template not found

Cause: The `templateName` does not match a bundled DOCX resource.

Check:

- `templateName` does not include `.docx`.
- The JAR contains `templates/{templateName}.docx`.
- Rebuild the JAR after adding new templates.

Command:

```bash
jar tf target/document-generator-1.0.0-shaded.jar | grep templates/
```

### PDF is generated but layout differs from Microsoft Word

This library uses docx4j and Apache FOP, not Microsoft Word. Approve templates against this engine's PDF output, especially for complex tables, fonts, Arabic text, headers, footers, and exact pagination.

### Logs show bundled fonts are not registered

Older builds attempted to register bundled fonts directly from BAW managed-asset URLs like:

```text
managedasset:...!fonts/noto/NotoSans-Regular.ttf
```

docx4j cannot reliably load fonts from that URL scheme, so the current build extracts bundled fonts to real temporary `.ttf` files on the JVM first and registers those file URLs. Rebuild and redeploy the shaded JAR if you see messages like:

```text
Bundled font Noto Sans was not registered by docx4j
No fonts configured
Document font Calibri is not mapped to a physical font
```

Use:

```bash
mvn clean package
```

Then upload:

```text
target/document-generator-1.0.0-shaded.jar
```

### Large output string

PDF Base64 is about 33 percent larger than the binary PDF. For very large PDFs, prefer a follow-up Java service that stores the PDF directly in a repository and returns a document ID or URL instead of carrying the full Base64 string through process variables.

## 13. Recommended BAW Flow

```text
Service Flow
  1. Prepare templateName
  2. Build jsonPayload string
  3. Call BawDocumentService.generatePdfBase64(templateName, jsonPayload)
  4. Store returned pdfBase64
  5. Decode/store/send the PDF through the target document integration
```

For the current requirement, the key call is:

```text
templateName: personal-loan-application
jsonPayload:  JSON string
return:       Base64 PDF string
```
