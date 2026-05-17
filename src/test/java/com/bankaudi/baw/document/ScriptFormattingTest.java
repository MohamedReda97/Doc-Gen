package com.bankaudi.baw.document;

import com.bankaudi.baw.document.engine.PlaceholderReplacer;
import com.bankaudi.baw.document.font.ArabicRunDecorator;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.BooleanDefaultTrue;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.RFonts;
import org.docx4j.wml.RPr;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptFormattingTest {
    private final ObjectFactory factory = new ObjectFactory();

    @Test
    void englishReplacementInRtlPlaceholderUsesLatinRunFormatting() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        R run = run("{{SIGNATORIES}}");
        RPr rPr = factory.createRPr();
        BooleanDefaultTrue rtl = new BooleanDefaultTrue();
        rtl.setVal(Boolean.TRUE);
        rPr.setRtl(rtl);
        run.setRPr(rPr);
        wordPackage.getMainDocumentPart().addObject(paragraph(run));

        new PlaceholderReplacer().replace(wordPackage, Collections.singletonMap("SIGNATORIES", "Ahmed Mohamed Nasser"));

        RPr updated = run.getRPr();
        assertNotNull(updated.getRtl());
        assertFalse(updated.getRtl().isVal());
        assertEquals("en-US", updated.getLang().getVal());
        assertEquals("Liberation Serif", updated.getRFonts().getAscii());
        assertEquals("Liberation Serif", updated.getRFonts().getHAnsi());
    }

    @Test
    void arabicDecoratorKeepsLatinFontSlotsForMixedRuns() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        R run = run("نحن الموقعين أدناه Ahmed Mohamed Nasser");
        RPr rPr = factory.createRPr();
        RFonts fonts = factory.createRFonts();
        fonts.setAscii("Liberation Serif");
        fonts.setHAnsi("Liberation Serif");
        fonts.setCs("Liberation Serif");
        rPr.setRFonts(fonts);
        run.setRPr(rPr);
        wordPackage.getMainDocumentPart().addObject(paragraph(run));

        new ArabicRunDecorator().decorate(wordPackage);

        RFonts updatedFonts = run.getRPr().getRFonts();
        assertEquals("Liberation Serif", updatedFonts.getAscii());
        assertEquals("Liberation Serif", updatedFonts.getHAnsi());
        assertEquals("Noto Naskh Arabic", updatedFonts.getCs());
    }

    @Test
    void englishReplacementInsideArabicRunIsSplitIntoOwnLatinRun() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        P paragraph = paragraph(run("نحن الموقعين أدناه {{SIGNATORIES}} المتخذين"));
        wordPackage.getMainDocumentPart().addObject(paragraph);

        new PlaceholderReplacer().replace(wordPackage, Collections.singletonMap("SIGNATORIES", "Ahmed Mohamed Nasser"));

        assertEquals(3, paragraph.getContent().size());
        R latinRun = (R) paragraph.getContent().get(1);
        assertEquals("Ahmed Mohamed Nasser", textOf(latinRun));
        assertFalse(latinRun.getRPr().getRtl().isVal());
        assertEquals("Liberation Serif", latinRun.getRPr().getRFonts().getAscii());
        assertTrue(textOf((R) paragraph.getContent().get(0)).contains("نحن"));
        assertTrue(textOf((R) paragraph.getContent().get(2)).contains("المتخذين"));
    }

    @Test
    void englishReplacementAcrossArabicRunBoundariesIsSplitIntoOwnLatinRun() throws Exception {
        WordprocessingMLPackage wordPackage = WordprocessingMLPackage.createPackage();
        P paragraph = paragraph(
                run("نحن الموقعين أدناه {{ "),
                run("SIGNATORIES"),
                run("}} المتخذين")
        );
        wordPackage.getMainDocumentPart().addObject(paragraph);

        new PlaceholderReplacer().replace(wordPackage, Collections.singletonMap("SIGNATORIES", "Ahmed Mohamed Nasser"));

        assertEquals(3, paragraph.getContent().size());
        R latinRun = (R) paragraph.getContent().get(1);
        assertEquals("Ahmed Mohamed Nasser", textOf(latinRun));
        assertFalse(latinRun.getRPr().getRtl().isVal());
        assertEquals("Liberation Serif", latinRun.getRPr().getRFonts().getAscii());
        assertTrue(textOf((R) paragraph.getContent().get(0)).contains("نحن"));
        assertTrue(textOf((R) paragraph.getContent().get(2)).contains("المتخذين"));
    }

    private P paragraph(R run) {
        return paragraph(new R[]{run});
    }

    private P paragraph(R... runs) {
        P paragraph = factory.createP();
        for (R run : runs) {
            run.setParent(paragraph);
            paragraph.getContent().add(run);
        }
        return paragraph;
    }

    private R run(String value) {
        R run = factory.createR();
        Text text = factory.createText();
        text.setValue(value);
        text.setParent(run);
        run.getContent().add(text);
        return run;
    }

    private String textOf(R run) {
        StringBuilder text = new StringBuilder();
        for (Object content : run.getContent()) {
            if (content instanceof Text) {
                text.append(((Text) content).getValue());
            }
        }
        return text.toString();
    }
}
