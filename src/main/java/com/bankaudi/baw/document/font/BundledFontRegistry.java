package com.bankaudi.baw.document.font;

import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BundledFontRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(BundledFontRegistry.class);

    private static final Map<String, String> FONTS = new LinkedHashMap<>();
    private static volatile Map<String, URI> extractedFontUris;

    static {
        FONTS.put("Noto Sans", "/fonts/noto/NotoSans-Regular.ttf");
        FONTS.put("Noto Sans Bold", "/fonts/noto/NotoSans-Bold.ttf");
        FONTS.put("Noto Sans Italic", "/fonts/noto/NotoSans-Italic.ttf");
        FONTS.put("Noto Sans Bold Italic", "/fonts/noto/NotoSans-BoldItalic.ttf");
        FONTS.put("Noto Naskh Arabic", "/fonts/noto/NotoNaskhArabic-Regular.ttf");
        FONTS.put("Noto Naskh Arabic Bold", "/fonts/noto/NotoNaskhArabic-Bold.ttf");
        FONTS.put("Noto Sans Symbols", "/fonts/noto/NotoSansSymbols-Regular.ttf");
        FONTS.put("Liberation Sans", "/fonts/liberation/LiberationSans-Regular.ttf");
        FONTS.put("Liberation Sans Bold", "/fonts/liberation/LiberationSans-Bold.ttf");
        FONTS.put("Liberation Sans Italic", "/fonts/liberation/LiberationSans-Italic.ttf");
        FONTS.put("Liberation Sans Bold Italic", "/fonts/liberation/LiberationSans-BoldItalic.ttf");
        FONTS.put("Liberation Serif", "/fonts/liberation/LiberationSerif-Regular.ttf");
    }

    public void configure(WordprocessingMLPackage wordPackage) {
        configure(0, wordPackage);
    }

    public void configure(long requestId, WordprocessingMLPackage wordPackage) {
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, PhysicalFont> registered = registerFonts(requestId);
            BundledOnlyFontMapper mapper = new BundledOnlyFontMapper();
            map(mapper, "Calibri", "Liberation Sans");
            map(mapper, "Arial", "Noto Sans");
            map(mapper, "Times New Roman", "Liberation Serif");
            map(mapper, "Cambria", "Liberation Serif");
            map(mapper, "Segoe UI Symbol", "Noto Sans Symbols");
            map(mapper, "Symbol", "Noto Sans Symbols");
            map(mapper, "Wingdings", "Noto Sans Symbols");
            map(mapper, "Noto Naskh Arabic", "Noto Naskh Arabic");
            for (String fontName : registered.keySet()) {
                map(mapper, fontName, fontName);
            }
            wordPackage.setFontMapper(mapper);
            LOG.info("BAW-DOC-TIMING request={} stage=font-configure-complete durationMs={} registeredFonts={}",
                    requestId, System.currentTimeMillis() - startedAt, registered.size());
        } catch (Exception e) {
            LOG.info("BAW-DOC-TIMING request={} stage=font-configure-failed durationMs={} error={}",
                    requestId, System.currentTimeMillis() - startedAt, e.getClass().getSimpleName());
            LOG.warn("Failed to configure docx4j font mapper. PDF conversion will use docx4j defaults.", e);
        }
    }

    private Map<String, PhysicalFont> registerFonts(long requestId) {
        long startedAt = System.currentTimeMillis();
        Map<String, PhysicalFont> registered = new LinkedHashMap<>();
        for (Map.Entry<String, URI> entry : fontUris().entrySet()) {
            long fontStartedAt = System.currentTimeMillis();
            try {
                URI uri = entry.getValue();
                PhysicalFonts.addPhysicalFont(uri);
                PhysicalFont physicalFont = resolvePhysicalFont(entry.getKey(), uri);
                if (physicalFont != null) {
                    PhysicalFonts.put(entry.getKey(), physicalFont);
                    registered.put(entry.getKey(), physicalFont);
                    LOG.info("BAW-DOC-TIMING request={} stage=register-font font=\"{}\" durationMs={} uri={}",
                            requestId, entry.getKey(), System.currentTimeMillis() - fontStartedAt, uri);
                } else {
                    LOG.warn("Bundled font {} was not registered by docx4j from {}", entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                LOG.warn("Failed to register bundled font {}", entry.getKey(), e);
            }
        }
        LOG.info("BAW-DOC-TIMING request={} stage=register-fonts-complete durationMs={} registeredFonts={}",
                requestId, System.currentTimeMillis() - startedAt, registered.size());
        return registered;
    }

    private Map<String, URI> fontUris() {
        Map<String, URI> current = extractedFontUris;
        if (current != null) {
            return current;
        }
        synchronized (BundledFontRegistry.class) {
            if (extractedFontUris == null) {
                extractedFontUris = extractFontsToTempFiles();
            }
            return extractedFontUris;
        }
    }

    private Map<String, URI> extractFontsToTempFiles() {
        long startedAt = System.currentTimeMillis();
        Map<String, URI> uris = new LinkedHashMap<>();
        try {
            Path fontDirectory = Files.createTempDirectory("baw-document-generator-fonts-");
            fontDirectory.toFile().deleteOnExit();
            for (Map.Entry<String, String> entry : FONTS.entrySet()) {
                String resourcePath = entry.getValue();
                String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                Path target = fontDirectory.resolve(fileName);
                try (InputStream inputStream = BundledFontRegistry.class.getResourceAsStream(resourcePath)) {
                    if (inputStream == null) {
                        LOG.warn("Bundled font resource is missing: {}", resourcePath);
                        continue;
                    }
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                    target.toFile().deleteOnExit();
                    uris.put(entry.getKey(), target.toUri());
                }
            }
            LOG.info("BAW-DOC-TIMING stage=extract-fonts-complete durationMs={} fonts={} directory={}",
                    System.currentTimeMillis() - startedAt, uris.size(), fontDirectory);
        } catch (Exception e) {
            LOG.info("BAW-DOC-TIMING stage=extract-fonts-failed durationMs={} extractedFonts={} error={}",
                    System.currentTimeMillis() - startedAt, uris.size(), e.getClass().getSimpleName());
            LOG.warn("Failed to extract bundled fonts to temporary files", e);
        }
        return uris;
    }

    private PhysicalFont resolvePhysicalFont(String alias, URI uri) {
        PhysicalFont exact = PhysicalFonts.get(alias);
        if (exact != null) {
            return exact;
        }
        List<PhysicalFont> fonts = PhysicalFonts.getPhysicalFont(alias, uri);
        return fonts == null || fonts.isEmpty() ? null : fonts.get(0);
    }

    private void map(BundledOnlyFontMapper mapper, String templateFont, String bundledFont) {
        PhysicalFont physicalFont = PhysicalFonts.get(bundledFont);
        if (physicalFont == null) {
            LOG.warn("Cannot map template font {} because bundled font {} is not registered", templateFont, bundledFont);
            return;
        }
        mapper.put(templateFont, physicalFont);
    }
}
