# IBM BAW JAR Usage Guide

This guide explains how to deploy and call the `baw-docx-generator` shaded JAR from IBM Business Automation Workflow (BAW), pass the template name and JSON payload as strings, and receive the generated DOCX as Base64.

Official IBM references:

- IBM BAW Java service discovery: https://www.ibm.com/docs/en/baw/24.0.x?topic=service-invoking-java
- IBM BAW integration samples: https://github.com/ibmbpm/Integration-Samples

## 1. Compatibility

BAW environments commonly run Java 8-compatible JVMs. The JAR must be compiled for Java 8 bytecode.

Optional bytecode check:

```bash
javap -verbose -classpath target/baw-docx-generator-1.0.0-shaded.jar \
  com.bawdocgen.api.DocxGenerationService | grep "major version"
```

Expected:

```text
major version: 52
```

## 2. Public BAW Service Class

Use this class in BAW:

```text
com.bawdocgen.api.DocxGenerationService
```

Use this method:

```java
public String generateDocxBase64(String templateName, String jsonPayload)
```

Why this wrapper exists:

- BAW Java Integration Service discovery supports public Java classes and public methods.
- IBM documents supported Java integration parameter and return types; `String` is supported.
- Raw `byte[]` output is not listed in the supported return type table, so the BAW-facing method returns the DOCX as a Base64 `String`.
- The public operation uses generic package, class, method, and artifact names.

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
4. Rebuild and redeploy the shaded JAR.

## 4. JSON Payload Contract

Pass the JSON payload as one string.

Preferred shape:

```json
{
  "flat_mapping": {
    "person.name": "Ahmed Mohamed Nasser",
    "application.amount": "10000"
  },
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

DOCX placeholders must match the keys exactly:

```text
{{person.name}}
{{application.amount}}
```

For dynamic table rows, put one placeholder row in the DOCX table:

```text
{{applicants[].id}}
{{applicants[].full_name}}
```

The engine clones that row once per item in `tables.applicants`.

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

This payload is meant for service-flow testing, so it avoids missing-placeholder warnings that appear when the JSON does not match the template.

## 5. Build The BAW-Compatible JAR

From the project root:

```bash
mvn clean package
```

Deploy this file to BAW:

```text
target/baw-docx-generator-1.0.0-shaded.jar
```

Do not deploy the smaller non-shaded JAR unless you also deploy every dependency. The shaded JAR includes the required runtime libraries and bundled templates.

Confirm the public class is present:

```bash
jar tf target/baw-docx-generator-1.0.0-shaded.jar \
  | grep com/bawdocgen/api/DocxGenerationService.class
```

Expected:

```text
com/bawdocgen/api/DocxGenerationService.class
```

## 6. Add The JAR To IBM BAW

In Process Designer or Workflow Center:

1. Open the Process App or Toolkit that will call the generator.
2. Add `target/baw-docx-generator-1.0.0-shaded.jar` as a managed/server file.
3. If the JAR is placed in a Toolkit, add that Toolkit as a dependency to the Process App.
4. Snapshot or save the Process App/Toolkit so the JAR is available for service discovery.

IBM notes that the JAR must be added before creating the external service, and that updating a managed JAR does not automatically refresh an already discovered Java service. If you change public method signatures, create a new external service or rediscover the class.

## 7. Create The External Java Service

1. Create a new External Service.
2. Choose Java service from Server File.
3. Select the managed file `baw-docx-generator-1.0.0-shaded.jar`.
4. Select Java class:

```text
com.bawdocgen.api.DocxGenerationService
```

5. Finish the wizard.
6. Confirm the discovered operation:

```text
generateDocxBase64(String, String) -> String
```

BAW may show generic input names such as `Parameter 1` and `Parameter 2`. Rename or document them as:

- `Parameter 1`: `templateName`
- `Parameter 2`: `jsonPayload`
- output: `docxBase64`

If `DocxGenerationService` is not visible:

1. Rebuild the project with `mvn clean package`.
2. Upload `target/baw-docx-generator-1.0.0-shaded.jar` again.
3. Remove/recreate or rediscover the external service.
4. Confirm the JAR contains the class with the `jar tf` command from section 5.

If BAW server logs show an old external Java binding after a redeploy, remove the stale external service or service task binding, recreate the external service from `DocxGenerationService`, save the process app, and create a new snapshot.

## 8. Call The Service From A Service Flow

1. Add a Service Task to the Service Flow.
2. In the Implementation section, select the external Java service.
3. Select the `generateDocxBase64` operation.
4. In Data Mapping, map:

```text
templateName  -> "personal-loan-application"
jsonPayload   -> tw.local.jsonPayload
output String -> tw.local.docxBase64
```

Example JavaScript to prepare the JSON string:

```javascript
tw.local.jsonPayload = JSON.stringify({
  flat_mapping: {
    "person.name": tw.local.personName,
    "application.amount": tw.local.applicationAmount
  }
});
```

## 9. Get The Output DOCX

The service output is a Base64 string containing the full DOCX bytes.

To verify the output:

1. Confirm `tw.local.docxBase64` is not empty.
2. Decode it as Base64.
3. The decoded bytes must start with the ZIP header bytes:

```text
PK
```

Use this file metadata downstream:

```text
Extension: .docx
MIME type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

For downstream use, choose one of these patterns:

- Store `docxBase64` as process data if the next system accepts Base64 documents.
- Decode `docxBase64` in a follow-up Java service and write it to an ECM/document repository.
- Decode `docxBase64` and pass the bytes to an email or document attachment integration that supports binary content.
- For troubleshooting only, decode it outside BAW and save it as `output.docx`.

Example local verification outside BAW:

```bash
base64 --decode generated-docx.txt > output.docx
file output.docx
```

Where `generated-docx.txt` contains the exact returned `docxBase64` string.

## 10. Prepare The DOCX For ECM Upload

For IBM BAW ECM operations, prefer passing the generated Base64 directly as an `ECMContentStream` instead of writing a temporary file first.

IBM documents `ECMContentStream` with these relevant fields:

- `content`: Base64 document content as a `String`.
- `contentLength`: original binary length in bytes.
- `fileName`: document file name.
- `mimeType`: document MIME type.

Use this MIME type for DOCX:

```text
application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

Example BAW script after calling `generateDocxBase64`:

```javascript
var docxBytes = Packages.javax.xml.bind.DatatypeConverter.parseBase64Binary(tw.local.docxBase64);

if (docxBytes.length < 2 || docxBytes[0] != 0x50 || docxBytes[1] != 0x4B) {
  throw "Generated document is not a valid DOCX ZIP stream";
}

tw.local.contentStream = new tw.object.ECMContentStream();
tw.local.contentStream.content = tw.local.docxBase64;
tw.local.contentStream.contentLength = docxBytes.length;
tw.local.contentStream.fileName = "personal-loan-application.docx";
tw.local.contentStream.mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
```

Then map `tw.local.contentStream` into the ECM operation that creates, checks in, or sets document content.

Only decode to a temporary file if a downstream integration specifically requires a server file path. A temp-file bridge is more fragile in clustered BAW environments because the file exists only on the JVM node that created it and must be cleaned up later.

## 11. Local Smoke Test Before Deploying

Build and run the automated tests:

```bash
mvn clean test
```

Then package the shaded JAR:

```bash
mvn clean package
```

## 12. Error Handling In BAW

The BAW-facing method:

```text
DocxGenerationService.generateDocxBase64(String templateName, String jsonPayload)
```

throws a runtime error with a detailed single-line message when generation fails. This is intentional: the error is visible in BAW testing/debugging and can be handled by an error boundary or error event without opening server logs each time.

The error message format is:

```text
BAW DOCX generation failed | code=DOC-004 | message=Failed to parse JSON flat_mapping | templateName=personal-loan-application | jsonPreview={not-json | rootCause=JsonParseException: Unexpected character...
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
BAW DOCX generation failed | code=DOC-001 | message=Bundled template not found: missing-template | templateName=missing-template | jsonPreview={"flat_mapping":{}} | rootCause=DocumentGenerationException: Bundled template not found: missing-template
```

For successful calls, the method returns only the Base64 DOCX string.

## 13. Troubleshooting

### `bad major version 55.0`

Cause: The JAR was compiled for Java 11.

Fix:

1. Build the updated project with `mvn clean package`.
2. Upload `target/baw-docx-generator-1.0.0-shaded.jar`.
3. Verify the class major version is `52`.
4. Rediscover/recreate the BAW external Java service.

### Class cannot be discovered

Check:

- The class is public: `com.bawdocgen.api.DocxGenerationService`.
- The class has a public no-argument constructor.
- The method is public: `generateDocxBase64(String templateName, String jsonPayload)`.
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
jar tf target/baw-docx-generator-1.0.0-shaded.jar | grep templates/
```

### Large output string

DOCX Base64 is about 33 percent larger than the binary DOCX. For very large documents, prefer a follow-up Java service that stores the DOCX directly in a repository and returns a document ID or URL instead of carrying the full Base64 string through process variables.

## 14. Recommended BAW Flow

```text
Service Flow
  1. Prepare templateName
  2. Build jsonPayload string
  3. Call DocxGenerationService.generateDocxBase64(templateName, jsonPayload)
  4. Store returned docxBase64
  5. Decode/store/send the DOCX through the target document integration
```

For the current requirement, the key call is:

```text
templateName: personal-loan-application
jsonPayload:  JSON string
return:       Base64 DOCX string
```
