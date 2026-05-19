package com.bawdocgen;

import com.bawdocgen.api.DocxGenerationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentDocxGenerationTest {
    @Test
    void serviceSupportsConcurrentBawCalls() throws Exception {
        DocxGenerationService service = new DocxGenerationService();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<byte[]>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int index = i;
                calls.add(() -> Base64.getDecoder().decode(service.generateDocxBase64(
                        "personal-loan-application",
                        "{\"flat_mapping\":{\"personal.first_name\":\"User-" + index + "\"}}")));
            }

            List<Future<byte[]>> futures = executor.invokeAll(calls);
            for (Future<byte[]> future : futures) {
                byte[] docx = future.get();
                assertTrue(docx.length > 1000);
                assertTrue(new String(docx, 0, 2, java.nio.charset.StandardCharsets.US_ASCII).startsWith("PK"));
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
