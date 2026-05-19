package com.bankaudi.baw.document;

import com.bankaudi.baw.document.font.BundledFontRegistry;
import org.docx4j.fonts.PhysicalFonts;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledFontRegistryTest {
    @Test
    void configuresBundledFontsFromPhysicalTempFiles() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();

        new BundledFontRegistry().configure(wordPackage);

        assertNotNull(wordPackage.getFontMapper());
        assertNotNull(PhysicalFonts.get("Noto Sans"));
        assertNotNull(PhysicalFonts.get("Noto Naskh Arabic"));
        assertNotNull(PhysicalFonts.get("Liberation Sans"));
        assertNotNull(PhysicalFonts.get("Liberation Serif"));
    }
}
