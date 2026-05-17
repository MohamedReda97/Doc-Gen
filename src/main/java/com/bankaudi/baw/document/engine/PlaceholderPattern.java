package com.bankaudi.baw.document.engine;

import java.util.regex.Pattern;

final class PlaceholderPattern {
    static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\[\\]-]+)\\s*}}");

    private PlaceholderPattern() {
    }
}
