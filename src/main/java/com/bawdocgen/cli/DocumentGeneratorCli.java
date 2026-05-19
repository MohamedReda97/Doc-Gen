package com.bawdocgen.cli;

import com.bawdocgen.api.DocumentGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public final class DocumentGeneratorCli {
    private DocumentGeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: DocumentGeneratorCli <template.docx> <data.json> <output.docx>");
            System.exit(2);
        }

        Path templatePath = Paths.get(args[0]);
        Path jsonPath = Paths.get(args[1]);
        Path outputPath = Paths.get(args[2]);

        byte[] template = Files.readAllBytes(templatePath);
        String json = new String(Files.readAllBytes(jsonPath), StandardCharsets.UTF_8);
        byte[] docx = DocumentGenerator.getInstance().generateDocx(template, json);

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, docx);

        System.out.println("Generated DOCX:");
        System.out.println(outputPath.toAbsolutePath());
        System.out.println("Size: " + docx.length + " bytes");
    }
}
