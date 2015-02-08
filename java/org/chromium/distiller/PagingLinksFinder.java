// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

/*
 * Parts of this file are adapted from Readability.
 *
 * Readability is Copyright (c) 2010 Src90 Inc
 * and licenced under the Apache License, Version 2.0.
 */

package org.chromium.distiller;

import org.chromium.distiller.proto.DomDistillerProtos;

import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.BaseElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.user.client.Window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class finds the next and previous page links for the distilled document.  The functionality
 * for next page links is migrated from readability.getArticleTitle() in chromium codebase's
 * third_party/readability/js/readability.js, and then expanded for previous page links; boilerpipe
 * doesn't have such capability.
 * First, it determines the base URL of the document.  Then, for each anchor in the document, its
 * href and text are compared to the base URL and examined for next- or previous-paging-related
 * information.  If it passes, its score is then determined by applying various heuristics on its
 * href, text, class name and ID,  Lastly, the page link with the highest score of at least 50 is
 * considered to have enough confidence as the next or previous page link.
 */
public class PagingLinksFinder {
    // Match for next page: next, continue, >, >>, » but not >|, »| as those usually mean last.
    private static final RegExp REG_NEXT_LINK =
            RegExp.compile("(next|weiter|continue|>([^\\|]|$)|»([^\\|]|$))", "i");
    private static final RegExp REG_PREV_LINK = RegExp.compile("(prev|early|old|new|<|«)", "i");
    private static final RegExp REG_POSITIVE = RegExp.compile(
            "article|body|content|entry|hentry|main|page|pagination|post|text|blog|story", "i");
    private static final RegExp REG_NEGATIVE = RegExp.compile(
            "combx|comment|com-|contact|foot|footer|footnote|masthead|media|meta"
                    + "|outbrain|promo|related|shoutbox|sidebar|sponsor|shopping|tags"
                    + "|tool|widget",
            "i");
    private static final RegExp REG_EXTRANEOUS = RegExp.compile(
            "print|archive|comment|discuss|e[\\-]?mail|share|reply|all|login|sign|single"
                    + "|as one|article",
            "i");
    private static final RegExp REG_PAGINATION = RegExp.compile("pag(e|ing|inat)", "i");
    private static final RegExp REG_LINK_PAGINATION =
            RegExp.compile("p(a|g|ag)?(e|ing|ination)?(=|\\/)[0-9]{1,2}$", "i");
    private static final RegExp REG_FIRST_LAST = RegExp.compile("(first|last)", "i");
    // Examples that match PAGE_NUMBER_REGEX are: "_p3", "-pg3", "p3", "_1", "-12-2".
    // Examples that don't match PAGE_NUMBER_REGEX are: "_p3 ", "p", "p123".
    private static final RegExp REG_PAGE_NUMBER =
            RegExp.compile("((_|-)?p[a-z]*|(_|-))[0-9]{1,2}$", "gi");

    private static final RegExp REG_HREF_CLEANER = RegExp.compile("/?(#.*)?$");

    public static DomDistillerProtos.PaginationInfo getPaginationInfo(String original_url) {
        DomDistillerProtos.PaginationInfo info = DomDistillerProtos.PaginationInfo.create();
        String next = findNext(Document.get().getDocumentElement(), original_url);
        if (next != null) {
            info.setNextPage(next);
        }
        return info;
    }

    /**
     * @param original_url The original url of the page being processed.
     * @return The next page link for the document.
     */
    public static String findNext(Element root, String original_url) {
        return findPagingLink(root, original_url, PageLink.NEXT);
    }

    /**
     * @param original_url The original url of the page being processed.
     * @return The previous page link for the document.
     */
    public static String findPrevious(Element root, String original_url) {
        return findPagingLink(root, original_url, PageLink.PREV);
    }

    private static String findPagingLink(Element root, String original_url, PageLink pageLink) {
        // findPagingLink() is static, so clear mLinkDebugInfo before processing the links.
        if (LogUtil.isLoggable(LogUtil.DEBUG_LEVEL_PAGING_INFO)) {
            mLinkDebugInfo.clear();
        }

        String baseUrl = findBaseUrl(original_url);
        // Remove trailing '/' from window location href, because it'll be used to compare with
        // other href's whose trailing '/' are also removed.
        String wndLocationHref = StringUtil.findAndReplace(original_url, "\\/$", "");
        NodeList<Element> allLinks = root.getElementsByTagName("A");
        Map<String, PagingLinkObj> possiblePages = new HashMap<String, PagingLinkObj>();

        AnchorElement baseAnchor = createAnchorWithBase(original_url);

        // The trailing "/" is essential to ensure the whole hostname is matched, and not just the
        // prefix of the hostname. It also maintains the requirement of having a "path" in the URL.
        String allowedPrefix = getScheme(original_url) + "://" + getHostname(original_url) + "/";

        // Loop through all links, looking for hints that they may be next- or previous- page links.
        // Things like having "page" in their textContent, className or id, or being a child of a
        // node with a page-y className or id.
        // Also possible: levenshtein distance? longest common subsequence?
        // After we do that, assign each page a score.
        for (int i = 0; i < allLinks.getLength(); i++) {
            AnchorElement link = AnchorElement.as(allLinks.getItem(i));

            // Note that AnchorElement.getHref() returns the absolute URI, so there's no need to
            // worry about relative links.
            String linkHref = resolveLinkHref(link, baseAnchor);

            if (!linkHref.substring(0, allowedPrefix.length()).equalsIgnoreCase(allowedPrefix)) {
                appendDbgStrForLink(link, "ignored: prefix");
                continue;
            }

            if (pageLink == PageLink.NEXT) {
                String linkHrefRemaining = linkHref.substring(allowedPrefix.length());
                if (!StringUtil.match(linkHrefRemaining, "\\d")) {
                    appendDbgStrForLink(link, "ignored: no number");
                    continue;
                }
            }

            int width = link.getOffsetWidth();
            int height = link.getOffsetHeight();
            if (width == 0 || height == 0) {
                appendDbgStrForLink(link, "ignored: sz=" + width + "x" + height);
                continue;
            }

            if (!DomUtil.isVisible(link)) {
                appendDbgStrForLink(link, "ignored: invisible");
                continue;
            }

            // Remove url anchor and then trailing '/' from link's href.
            linkHref = REG_HREF_CLEANER.replace(linkHref, "");
            appendDbgStrForLink(link, "-> " + linkHref);

            // Ignore page link that is the same as current window location.
            // If the page link is same as the base URL:
            // - next page link: ignore it, since we would already have seen it.
            // - previous page link: don't ignore it, since some sites will simply have the same
            //                       base URL for the first page.
            if (linkHref.equalsIgnoreCase(wndLocationHref)
                    || (pageLink == PageLink.NEXT && linkHref.equalsIgnoreCase(baseUrl))) {
                appendDbgStrForLink(
                        link, "ignored: same as current or base url " + baseUrl);
                continue;
            }

            // Use javascript innerText (instead of javascript textContent) to only get visible
            // text.
            String linkText = DomUtil.getInnerText(link);

            // If the linkText looks like it's not the next or previous page, skip it.
            if (REG_EXTRANEOUS.test(linkText) || linkText.length() > 25) {
                appendDbgStrForLink(link, "ignored: one of extra");
                continue;
            }

            // For next page link, if the initial part of the URL is identical to the base URL, but
            // the rest of it doesn't contain any digits, it's certainly not a next page link.
            // However, this doesn't apply to previous page link, because most sites will just have
            // the base URL for the first page.
            // TODO(kuan): baseUrl (returned by findBaseUrl()) is NOT the prefix of the current
            // window location, even though it appears to be so the way it's used here.
            // TODO(kuan): do we need to apply this heuristic to previous page links if current page
            // number is not 2?
            if (pageLink == PageLink.NEXT) {
                String linkHrefRemaining = StringUtil.findAndReplace(linkHref, baseUrl, "");
                if (!StringUtil.match(linkHrefRemaining, "\\d")) {
                    appendDbgStrForLink(link, "ignored: no number beyond base url " + baseUrl);
                    continue;
                }
            }

            PagingLinkObj linkObj = null;
            if (!possiblePages.containsKey(linkHref)) {  // Have not encountered this href.
                linkObj = new PagingLinkObj(i, 0, linkText, linkHref);
                possiblePages.put(linkHref, linkObj);
            } else {  // Have already encountered this href, append its text to existing entry's.
                linkObj = possiblePages.get(linkHref);
                linkObj.mLinkText += " | " + linkText;
            }

            // If the base URL isn't part of this URL, penalize this link.  It could still be the
            // link, but the odds are lower.
            // Example: http://www.actionscript.org/resources/articles/745/1/JavaScript-and-VBScript-Injection-in-ActionScript-3/Page1.html.
            // TODO(kuan): again, baseUrl (returned by findBaseUrl()) is NOT the prefix of the
            // current window location, even though it appears to be so the way it's used here.
            if (linkHref.indexOf(baseUrl) != 0) {
                linkObj.mScore -= 25;
                appendDbgStrForLink(link, "score=" + linkObj.mScore +
                        ": not part of base url " + baseUrl);
            }

            // Concatenate the link text with class name and id, and determine the score based on
            // existence of various paging-related words.
            String linkData = linkText + " " + link.getClassName() + " " + link.getId();
            appendDbgStrForLink(link, "txt+class+id=" + linkData);
            if (pageLink == PageLink.NEXT ? REG_NEXT_LINK.test(linkData)
                                          : REG_PREV_LINK.test(linkData)) {
                linkObj.mScore += 50;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has " +
                        (pageLink == PageLink.NEXT ? "next" : "prev" + " regex"));
            }
            if (REG_PAGINATION.test(linkData)) {
                linkObj.mScore += 25;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has pag* word");
            }
            if (REG_FIRST_LAST.test(linkData)) {
                // -65 is enough to negate any bonuses gotten from a > or » in the text.
                // If we already matched on "next", last is probably fine.
                // If we didn't, then it's bad.  Penalize.
                // Same for "prev".
                if ((pageLink == PageLink.NEXT && !REG_NEXT_LINK.test(linkObj.mLinkText))
                        || (pageLink == PageLink.PREV && !REG_PREV_LINK.test(linkObj.mLinkText))) {
                    linkObj.mScore -= 65;
                    appendDbgStrForLink(link, "score=" + linkObj.mScore +
                            ": has first|last but no " +
                            (pageLink == PageLink.NEXT ? "next" : "prev") + " regex");
                }
            }
            if (REG_NEGATIVE.test(linkData) || REG_EXTRANEOUS.test(linkData)) {
                linkObj.mScore -= 50;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has neg or extra regex");
            }
            if (pageLink == PageLink.NEXT ? REG_PREV_LINK.test(linkData)
                                          : REG_NEXT_LINK.test(linkData)) {
                linkObj.mScore -= 200;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has opp of " +
                        (pageLink == PageLink.NEXT ? "next" : "prev") + " regex");
            }

            // Check if a parent element contains page or paging or paginate.
            boolean positiveMatch = false, negativeMatch = false;
            Element parent = link.getParentElement();
            while (parent != null && (positiveMatch == false || negativeMatch == false)) {
                String parentClassAndId = parent.getClassName() + " " + parent.getId();
                if (!positiveMatch && REG_PAGINATION.test(parentClassAndId)) {
                    linkObj.mScore += 25;
                    positiveMatch = true;
                    appendDbgStrForLink(link,"score=" + linkObj.mScore +
                            ": posParent - " + parentClassAndId);
                }
                // TODO(kuan): to get 1st page for prev page link, this can't be applied; however,
                // the non-application might be the cause of recursive prev page being returned,
                // i.e. for page 1, it may incorrectly return page 3 for prev page link.
                if (!negativeMatch && REG_NEGATIVE.test(parentClassAndId)) {
                    // If this is just something like "footer", give it a negative.
                    // If it's something like "body-and-footer", leave it be.
                    if (!REG_POSITIVE.test(parentClassAndId)) {
                        linkObj.mScore -= 25;
                        negativeMatch = true;
                        appendDbgStrForLink(link, "score=" + linkObj.mScore + ": negParent - " +
                                parentClassAndId);
                    }
                }
                parent = parent.getParentElement();
            }

            // If the URL looks like it has paging in it, add to the score.
            // Things like /page/2/, /pagenum/2, ?p=3, ?page=11, ?pagination=34.
            if (REG_LINK_PAGINATION.test(linkHref) || REG_PAGINATION.test(linkHref)) {
                linkObj.mScore += 25;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has paging info");
            }

            // If the URL contains negative values, give a slight decrease.
            if (REG_EXTRANEOUS.test(linkHref)) {
                linkObj.mScore -= 15;
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": has extra regex");
            }

            // If the link text is too long, penalize the link.
            if (linkText.length() > 10) {
                linkObj.mScore -= linkText.length();
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": text too long");
            }

            // If the link text can be parsed as a number, give it a minor bonus, with a slight bias
            // towards lower numbered pages.  This is so that pages that might not have 'next' in
            // their text can still get scored, and sorted properly by score.
            // TODO(kuan): it might be wrong to assume that it knows about other pages in the
            // document and that it starts on the first page.
            int linkTextAsNumber = JavaScript.parseInt(linkText, 10);
            if (linkTextAsNumber > 0) {
                // Punish 1 since we're either already there, or it's probably before what we
                // want anyway.
                if (linkTextAsNumber == 1) {
                    linkObj.mScore -= 10;
                } else {
                    linkObj.mScore += Math.max(0, 10 - linkTextAsNumber);
                }
                appendDbgStrForLink(link, "score=" + linkObj.mScore + ": linktxt is a num (" +
                        linkTextAsNumber + ")");
            }
        }  // for all links

        // Loop through all of the possible pages from above and find the top candidate for the next
        // page URL.  Require at least a score of 50, which is a relatively high confidence that
        // this page is the next link.
        PagingLinkObj topPage = null;
        if (!possiblePages.isEmpty()) {
            for (PagingLinkObj pageObj : possiblePages.values()) {
                if (pageObj.mScore >= 50 && (topPage == null || topPage.mScore < pageObj.mScore)) {
                    topPage = pageObj;
                }
            }
        }

        String pagingHref = null;
        if (topPage != null) {
            pagingHref = StringUtil.findAndReplace(topPage.mLinkHref, "\\/$", "");
            appendDbgStrForLink(allLinks.getItem(topPage.mLinkIndex), "found: score=" +
                    topPage.mScore + ", txt=[" + topPage.mLinkText + "], " + pagingHref);
        }

        if (LogUtil.isLoggable(LogUtil.DEBUG_LEVEL_PAGING_INFO)) {
            logDbgInfoToConsole(pageLink, pagingHref, allLinks);
        }

        return pagingHref;
    }

    public static AnchorElement createAnchorWithBase(String base_url) {
        Document doc = DomUtil.createHTMLDocument(Document.get());

        BaseElement base = doc.createBaseElement();
        base.setHref(base_url);
        doc.getHead().appendChild(base);

        AnchorElement a = doc.createAnchorElement();
        doc.getBody().appendChild(base);
        return a;
    }

    private static String fixMissingScheme(String url) {
        if (url.isEmpty()) return "";
        if (!url.contains("://")) return "http://" + url;
        return url;
    }

    // The link is resolved using an anchor within a new HTML document with a base tag.
    public static String resolveLinkHref(AnchorElement link, AnchorElement baseAnchor) {
        String linkHref = link.getAttribute("href");
        baseAnchor.setAttribute("href", linkHref);
        return baseAnchor.getHref();
    }

    private static String getScheme(String url) {
        return StringUtil.split(url, ":\\/\\/")[0];
    }

    // Port number is also included if it exists.
    private static String getHostname(String url) {
        url = StringUtil.split(url, ":\\/\\/")[1];
        if (!url.contains("/")) return url;
        return StringUtil.split(url, "\\/")[0];
    }

    private static String getPath(String url) {
        url = StringUtil.split(url, ":\\/\\/")[1];
        if (!url.contains("/")) return "";
        return StringUtil.findAndReplace(url, "^([^/]*)/", "");
    }

    private static String findBaseUrl(String original_url) {
        // This extracts relevant parts from the window location's path based on various heuristics
        // to determine the path of the base URL of the document.  This path is then appended to the
        // window location protocol and host to form the base URL of the document.  This base URL is
        // then used as reference for comparison against an anchor's href to to determine if the
        // anchor is a next or previous paging link.

        // First, from the window's location's path, extract the segments delimited by '/'.  Then,
        // because the later segments probably contain less relevant information for the base URL,
        // reverse the segments for easier processing.
        // Note: '?' is a special character in RegEx, so enclose it within [] to specify the actual
        // character.
        String url = StringUtil.findAndReplace(original_url, "\\?.*$", "");
        String noUrlParams = StringUtil.split(url, ":\\/\\/")[1];
        String[] urlSlashes = StringUtil.split(noUrlParams, "/");
        Collections.reverse(Arrays.asList(urlSlashes));

        // Now, process each segment by extracting relevant information based on various heuristics.
        List<String> cleanedSegments = new ArrayList<String>();
        for (int i = 0; i < urlSlashes.length - 1; i++) {
            String segment = urlSlashes[i];

            // Split off and save anything that looks like a file type.
            if (segment.indexOf(".") != -1) {
                // Because '.' is a special character in RegEx, enclose it within [] to specify the
                // actual character.
                String possibleType = StringUtil.split(segment, "[.]")[1];

                // If the type isn't alpha-only, it's probably not actually a file extension.
                if (!StringUtil.match(possibleType, "[^a-zA-Z]")) {
                    segment = StringUtil.split(segment, "[.]")[0];
                }
            }

            // EW-CMS specific segment replacement. Ugly.
            // Example: http://www.ew.com/ew/article/0,,20313460_20369436,00.html.
            segment = StringUtil.findAndReplace(segment, ",00", "");

            // If the first or second segment has anything looking like a page number, remove it.
            if (i < 2) {
                segment = REG_PAGE_NUMBER.replace(segment, "");
            }

            // Ignore an empty segment.
            if (segment.isEmpty()) continue;

            // If this is purely a number in the first or second segment, it's probably a page
            // number, ignore it.
            if (i < 2 && StringUtil.match(segment, "^\\d{1,2}$")) continue;

            // If this is the first segment and it's just "index", ignore it.
            if (i == 0 && segment.equalsIgnoreCase("index")) continue;

            // If the first or second segment is shorter than 3 characters, and the first
            // segment was purely alphas, ignore it.
            if (i < 2 && segment.length() < 3 && !StringUtil.match(urlSlashes[0], "[a-z]")) {
                continue;
            }

            // If we got here, append the segment to cleanedSegments.
            cleanedSegments.add(segment);
        }  // for all urlSlashes

        return StringUtil.split(url, ":\\/\\/")[0] + "://" + urlSlashes[urlSlashes.length-1] + "/" +
                reverseJoin(cleanedSegments, "/");
    }

    private static String reverseJoin(List<String> array, String delim) {
        // As per http://stackoverflow.com/questions/5748044/gwt-string-concatenation-operator-vs-stringbuffer,
        // + operator is faster for javascript than StringBuffer/StringBuilder.
        String joined = "";
        for (int i = array.size() - 1; i >= 0; i--) {
            joined += array.get(i);
            if (i > 0) joined += delim;
        }
        return joined;
    }

    private static void appendDbgStrForLink(Element link, String message) {
        if (!LogUtil.isLoggable(LogUtil.DEBUG_LEVEL_PAGING_INFO)) return;

        // |message| is concatenated with the existing debug string for |link| (delimited by ";") in
        // mLinkDebugInfo.
        String dbgStr = "";
        if (mLinkDebugInfo.containsKey(link)) dbgStr = mLinkDebugInfo.get(link);
        if (!dbgStr.isEmpty()) dbgStr += "; ";
        dbgStr += message;
        mLinkDebugInfo.put(link, dbgStr);
    }

    private static void logDbgInfoToConsole(PageLink pageLink, String pagingHref,
            NodeList<Element> allLinks) {
        // This logs the following to the console:
        // - number of links processed
        // - the next or previous page link found
        // - for each link: its href, text, concatenated debug string.
        // Location of logging output is different when running in different modes:
        // - "ant test.dev": test output file.
        // - chrome browser distiller viewer: chrome logfile.
        // (TODO)kuan): investigate how to get logging when running "ant test.prod" - currently,
        // nothing appears.  In the meantime, throwing an exception with a log message at suspicious
        // codepoints can produce a call stack and help debugging, albeit tediously.
        LogUtil.logToConsole("numLinks=" + allLinks.getLength() + ", found " +
                (pageLink == PageLink.NEXT ? "next: " : "prev: ") +
                (pagingHref != null ? pagingHref : "null"));

        for (int i = 0; i < allLinks.getLength(); i++) {
            AnchorElement link = AnchorElement.as(allLinks.getItem(i));

            // Use javascript innerText (instead of javascript textContent) to get only visible
            // text.
            String text = DomUtil.getInnerText(link);
            // Trim unnecessary whitespaces from text.
            String[] words = StringUtil.split(text, "\\s+");
            text = "";
            for (int w = 0; w < words.length; w++) {
                text += words[w];
                if (w < words.length - 1) text += " ";
            }

            LogUtil.logToConsole(i + ")" + link.getHref() + ", txt=[" + text + "], dbg=[" +
                    mLinkDebugInfo.get(link) + "]");
        }
    }

    private static class PagingLinkObj {
        private int mLinkIndex = -1;
        private int mScore = 0;
        private String mLinkText;
        private final String mLinkHref;

        PagingLinkObj(int linkIndex, int score, String linkText, String linkHref) {
            mLinkIndex = linkIndex;
            mScore = score;
            mLinkText = linkText;
            mLinkHref = linkHref;
        }
    }

    private enum PageLink {
        NEXT,
        PREV,
    }

    private static final Map<Element, String> mLinkDebugInfo = new HashMap<Element, String>();

}
