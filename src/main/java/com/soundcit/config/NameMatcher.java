package com.soundcit.config;

import java.util.regex.Pattern;

/**
 * Matches an item's custom name against a rule pattern, OptiFine-CIT style:
 * <ul>
 *   <li>{@code "regex:..."} — case-sensitive regex</li>
 *   <li>{@code "iregex:..."} — case-insensitive regex</li>
 *   <li>{@code "pattern:..."} — wildcard pattern ({@code *} = any run, {@code ?} = any char), case-sensitive</li>
 *   <li>{@code "ipattern:..."} — wildcard pattern, case-insensitive</li>
 *   <li>anything else — exact match, case-insensitive</li>
 * </ul>
 */
public final class NameMatcher {
    private final String raw;
    private final Pattern pattern;

    private NameMatcher(String raw, Pattern pattern) {
        this.raw = raw;
        this.pattern = pattern;
    }

    public static NameMatcher parse(String spec) {
        if (spec.startsWith("regex:")) {
            return new NameMatcher(spec, Pattern.compile(spec.substring("regex:".length())));
        }
        if (spec.startsWith("iregex:")) {
            return new NameMatcher(spec, Pattern.compile(spec.substring("iregex:".length()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        if (spec.startsWith("pattern:")) {
            return new NameMatcher(spec, Pattern.compile(wildcardToRegex(spec.substring("pattern:".length()))));
        }
        if (spec.startsWith("ipattern:")) {
            return new NameMatcher(spec, Pattern.compile(wildcardToRegex(spec.substring("ipattern:".length())), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        }
        return new NameMatcher(spec, null);
    }

    public boolean matches(String name) {
        if (pattern != null) {
            return pattern.matcher(name).matches();
        }
        return raw.equalsIgnoreCase(name);
    }

    private static String wildcardToRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wildcard.length(); i++) {
            char c = wildcard.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append('.');
                default -> sb.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return raw;
    }
}
