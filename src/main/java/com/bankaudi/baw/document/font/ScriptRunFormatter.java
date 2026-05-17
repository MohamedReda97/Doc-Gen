package com.bankaudi.baw.document.font;

import org.docx4j.jaxb.Context;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.CTLanguage;
import org.docx4j.wml.R;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;

public class ScriptRunFormatter {
    private static final String LATIN_FONT = "Liberation Serif";
    private static final String ARABIC_FONT = "Noto Naskh Arabic";

    public void formatReplacement(R run, String replacement) {
        if (run == null || replacement == null || replacement.isEmpty()) {
            return;
        }

        boolean hasArabic = containsArabic(replacement);
        boolean hasLatin = containsLatin(replacement);
        if (!hasArabic && !hasLatin) {
            return;
        }

        RPr rPr = run.getRPr();
        boolean needsLatinOverride = hasArabic || isRtl(rPr) || usesArabicFontForLatin(rPr);
        if (hasLatin && !needsLatinOverride) {
            return;
        }

        if (rPr == null) {
            rPr = Context.getWmlObjectFactory().createRPr();
            run.setRPr(rPr);
        }

        RFonts fonts = rPr.getRFonts();
        if (fonts == null) {
            fonts = Context.getWmlObjectFactory().createRFonts();
            rPr.setRFonts(fonts);
        }

        if (hasLatin) {
            fonts.setAscii(LATIN_FONT);
            fonts.setHAnsi(LATIN_FONT);
        }
        if (hasArabic) {
            fonts.setCs(ARABIC_FONT);
        }

        CTLanguage language = rPr.getLang();
        if (language == null) {
            language = Context.getWmlObjectFactory().createCTLanguage();
            rPr.setLang(language);
        }

        if (hasArabic) {
            language.setVal("ar-SA");
            language.setBidi("ar-SA");
        } else {
            language.setVal("en-US");
            language.setBidi(null);
            BooleanDefaultTrue rtl = new BooleanDefaultTrue();
            rtl.setVal(Boolean.FALSE);
            rPr.setRtl(rtl);
        }
    }

    public void formatLatinReplacementInMixedScriptContext(R run, String replacement) {
        if (run == null || replacement == null || !containsLatin(replacement) || containsArabic(replacement)) {
            return;
        }

        RPr rPr = run.getRPr();
        if (rPr == null) {
            rPr = Context.getWmlObjectFactory().createRPr();
            run.setRPr(rPr);
        }

        RFonts fonts = rPr.getRFonts();
        if (fonts == null) {
            fonts = Context.getWmlObjectFactory().createRFonts();
            rPr.setRFonts(fonts);
        }
        fonts.setAscii(LATIN_FONT);
        fonts.setHAnsi(LATIN_FONT);

        CTLanguage language = rPr.getLang();
        if (language == null) {
            language = Context.getWmlObjectFactory().createCTLanguage();
            rPr.setLang(language);
        }
        language.setVal("en-US");
        language.setBidi(null);

        BooleanDefaultTrue rtl = new BooleanDefaultTrue();
        rtl.setVal(Boolean.FALSE);
        rPr.setRtl(rtl);
    }

    private boolean isRtl(RPr rPr) {
        return rPr != null && rPr.getRtl() != null && rPr.getRtl().isVal();
    }

    private boolean usesArabicFontForLatin(RPr rPr) {
        if (rPr == null || rPr.getRFonts() == null) {
            return false;
        }
        RFonts fonts = rPr.getRFonts();
        return isArabicFont(fonts.getAscii()) || isArabicFont(fonts.getHAnsi());
    }

    private boolean isArabicFont(String fontName) {
        return fontName != null && fontName.toLowerCase(java.util.Locale.ROOT).contains("arabic");
    }

    public boolean containsArabic(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.ARABIC
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                    || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
                    || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                    || block == Character.UnicodeBlock.ARABIC_EXTENDED_A) {
                return true;
            }
        }
        return false;
    }

    public boolean containsLatin(String text) {
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.BASIC_LATIN
                    || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                    || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                    || block == Character.UnicodeBlock.LATIN_EXTENDED_B) {
                char c = text.charAt(i);
                if (Character.isLetter(c)) {
                    return true;
                }
            }
        }
        return false;
    }
}
