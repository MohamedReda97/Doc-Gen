package com.bankaudi.baw.document.cli;

import com.bankaudi.baw.document.api.DocumentGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class DocumentGeneratorCli {
    private DocumentGeneratorCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: DocumentGeneratorCli <template.docx> <data.json> <output.pdf>");
            System.exit(2);
        }

        Path templatePath = Paths.get(args[0]);
        Path jsonPath = Paths.get(args[1]);
        Path outputPath = Paths.get(args[2]);

        byte[] template = Files.readAllBytes(templatePath);
        String json = Files.readString(jsonPath);
        byte[] pdf = DocumentGenerator.getInstance().generatePdf(template, json);

        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, pdf);

        System.out.println("Generated PDF:");
        System.out.println(outputPath.toAbsolutePath());
        System.out.println("Size: " + pdf.length + " bytes");
    }
}
