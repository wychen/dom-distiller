// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package com.dom_distiller.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {
    /**
     * This is equivalent  to Java's Character.isWhitespace(). That function is not available
     * with GWT. The ranges are generated with tools/UnicodePatternGenerator.java.
     */
    public static boolean isWhitespace(Character c) {
        int code = (int)c.charValue();
        return between(code, 0x0009, 0x000d) ||
                between(code, 0x001c, 0x0020) ||
                code == 0x1680 ||
                code == 0x180e ||
                between(code, 0x2000, 0x2006) ||
                between(code, 0x2028, 0x2029) ||
                code == 0x205f ||
                code == 0x3000;

    }

    private static boolean between(int t, int lo, int hi) {
        return lo <= t && t <= hi;
    }

    public static boolean isStringAllWhitespace(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (!isWhitespace(s.charAt(i))) return false;
        }
        return true;
    }

    // The version of gwt that we use implements trim improperly (it uses a javascript regex with \s
    // where java's trim explicitly matches \u0000-\u0020). This version is from GWT's trunk.
    public static native String javaTrim(String s) /*-{
        if (s.length == 0 || (s[0] > '\u0020' && s[s.length - 1] > '\u0020')) {
            return s;
        }
        return s.replace(/^[\u0000-\u0020]*|[\u0000-\u0020]*$/g, '');
    }-*/;

    public static boolean match(String input, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.find();
    }

    public static String findAndReplace(String input, String regex, String replace) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(replace);
    }

    public static String[] split(String input, String regex) {
        // Either use String.split(), which is rumored to be very slow
        // (see http://turbomanage.wordpress.com/2011/07/12/gwt-performance-tip-watch-out-for-string-split/),
        return input.split(regex);

/*
        // OR RegEx.split() via Pattern.split(),
        // TODO(kuan): add test for Pattern.split() if using this.
        Pattern pattern = Pattern.compile(regex);
        return pattern.split(input);
*/
    }

    // OR JSNI to call native Javascript regexp (as suggested by the website above).
    // Currently, "ant test.prod" which is closest to the "real world scenario" but still not very
    // accurate, has RegEx.split as the slowest, while GWT String.split and JSNI String.split as
    // almost the same.
    //private static final native int splitLength(String input, String regex) /*-{
        //return input.split(/regex/);
    //}-*/;

    public static int splitLength(String input, String regex) {
        return StringUtil.split(input, regex).length;
    }

    public static String trim(String input) {
        Pattern pattern = Pattern.compile("^\\s+|\\s+$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll("");
    }
}
