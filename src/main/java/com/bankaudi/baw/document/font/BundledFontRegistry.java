package com.bankaudi.baw.document.font;

import org.docx4j.fonts.PhysicalFont;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BundledFontRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(BundledFontRegistry.class);

    private static final Map<String, String> FONTS = new LinkedHashMap<>();

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
        try {
            Map<String, PhysicalFont> registered = registerFonts();
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
        } catch (Exception e) {
            LOG.warn("Failed to configure docx4j font mapper. PDF conversion will use docx4j defaults.", e);
        }
    }

    private Map<String, PhysicalFont> registerFonts() {
        Map<String, PhysicalFont> registered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : FONTS.entrySet()) {
            URL url = BundledFontRegistry.class.getResource(entry.getValue());
            if (url == null) {
                LOG.warn("Bundled font resource is missing: {}", entry.getValue());
                continue;
            }
            try {
                URI uri = url.toURI();
                PhysicalFonts.addPhysicalFont(uri);
                PhysicalFont physicalFont = resolvePhysicalFont(entry.getKey(), uri);
                if (physicalFont != null) {
                    PhysicalFonts.put(entry.getKey(), physicalFont);
                    registered.put(entry.getKey(), physicalFont);
                } else {
                    LOG.warn("Bundled font {} was not registered by docx4j from {}", entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                LOG.warn("Failed to register bundled font {}", entry.getKey(), e);
            }
        }
        return registered;
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
