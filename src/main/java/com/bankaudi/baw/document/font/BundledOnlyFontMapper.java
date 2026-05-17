package com.bankaudi.baw.document.font;

import org.docx4j.fonts.Mapper;
import org.docx4j.wml.Fonts;

import java.util.Set;

class BundledOnlyFontMapper extends Mapper {
    @Override
    public void populateFontMappings(Set<String> documentFontNames, Fonts fontsInUse) {
        // Mappings are explicitly populated by BundledFontRegistry.
    }
}
