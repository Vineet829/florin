package com.florin.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfText {

    private PdfText() {}

    public static String of(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static List<String> pages(byte[] pdf) {
        try (PDDocument document = PDDocument.load(pdf)) {
            List<String> pages = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return pages;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static List<String> overlaps(byte[] pdf) {
        record Piece(int page, String text, float left, float right, float baseline) {}
        List<Piece> pieces = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                private StringBuilder text;
                private float left;
                private float right;
                private float baseline;
                private boolean inArray;

                private void start() {
                    text = new StringBuilder();
                    left = Float.MAX_VALUE;
                    right = -Float.MAX_VALUE;
                }

                private void finish() {
                    if (text != null && !text.toString().isBlank()) {
                        pieces.add(new Piece(getCurrentPageNo(), text.toString(), left, right, baseline));
                    }
                    text = null;
                }

                @Override
                public void showTextStrings(COSArray array) throws IOException {
                    start();
                    inArray = true;
                    try {
                        super.showTextStrings(array);
                    } finally {
                        inArray = false;
                        finish();
                    }
                }

                @Override
                protected void showText(byte[] string) throws IOException {
                    if (inArray) {
                        super.showText(string);
                        return;
                    }
                    start();
                    super.showText(string);
                    finish();
                }

                @Override
                protected void processTextPosition(TextPosition glyph) {
                    if (text != null) {
                        if (text.isEmpty()) {
                            baseline = glyph.getYDirAdj();
                        }
                        text.append(glyph.getUnicode());
                        if (!glyph.getUnicode().isBlank()) {
                            left = Math.min(left, glyph.getXDirAdj());
                            right = Math.max(right, glyph.getXDirAdj() + glyph.getWidthDirAdj());
                        }
                    }
                    super.processTextPosition(glyph);
                }
            }.getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        List<String> overlaps = new ArrayList<>();
        for (int first = 0; first < pieces.size(); first++) {
            for (int second = first + 1; second < pieces.size(); second++) {
                Piece a = pieces.get(first);
                Piece b = pieces.get(second);
                boolean oneLine = a.page() == b.page() && Math.abs(a.baseline() - b.baseline()) < 1;
                boolean repainted = a.text().equals(b.text()) && Math.abs(a.left() - b.left()) < 0.1f;
                if (oneLine && !repainted && Math.min(a.right(), b.right()) - Math.max(a.left(), b.left()) > 0.1f) {
                    overlaps.add(a.text() + " / " + b.text());
                }
            }
        }
        return overlaps;
    }

    static float fontSizeOf(byte[] pdf, String text) {
        float[] size = {-1};
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void writeString(String line, List<TextPosition> positions) throws IOException {
                    int at = line.indexOf(text);
                    if (size[0] < 0 && at >= 0 && at < positions.size()) {
                        size[0] = positions.get(at).getFontSizeInPt();
                    }
                    super.writeString(line, positions);
                }
            }.getText(document);
            return size[0];
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static float gapBefore(byte[] pdf, String text) {
        StringBuilder shown = new StringBuilder();
        List<TextPosition> glyphs = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition glyph) {
                    shown.append(glyph.getUnicode());
                    while (glyphs.size() < shown.length()) {
                        glyphs.add(glyph);
                    }
                }
            }.getText(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        int at = shown.lastIndexOf(text);
        if (at < 0) {
            throw new IllegalArgumentException("The PDF doesn't show " + text);
        }
        TextPosition start = glyphs.get(at);
        float end = Float.NEGATIVE_INFINITY;
        for (TextPosition glyph : glyphs.subList(0, at)) {
            if (!glyph.getUnicode().isBlank() && Math.abs(glyph.getYDirAdj() - start.getYDirAdj()) < 0.5f) {
                end = Math.max(end, glyph.getXDirAdj() + glyph.getWidthDirAdj());
            }
        }
        return start.getXDirAdj() - end;
    }
}
