package util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class HtmlContentExtractor {
    
    // Patterns to remove unwanted HTML elements
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE_PATTERN = Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile("&[a-zA-Z]+;");
    
    /**
     * Extracts readable text content from HTML
     */
    public static String extractText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        
        // Remove script tags
        html = SCRIPT_PATTERN.matcher(html).replaceAll("");
        
        // Remove style tags
        html = STYLE_PATTERN.matcher(html).replaceAll("");
        
        // Remove HTML comments
        html = html.replaceAll("<!--.*?-->", "");
        
        // Extract text from common content tags (p, div, article, section, h1-h6, li, etc.)
        StringBuilder textBuilder = new StringBuilder();
        
        // Extract from paragraph tags
        extractFromTag(html, "p", textBuilder);
        extractFromTag(html, "div", textBuilder);
        extractFromTag(html, "article", textBuilder);
        extractFromTag(html, "section", textBuilder);
        extractFromTag(html, "main", textBuilder);
        extractFromTag(html, "h1", textBuilder);
        extractFromTag(html, "h2", textBuilder);
        extractFromTag(html, "h3", textBuilder);
        extractFromTag(html, "h4", textBuilder);
        extractFromTag(html, "h5", textBuilder);
        extractFromTag(html, "h6", textBuilder);
        extractFromTag(html, "li", textBuilder);
        extractFromTag(html, "span", textBuilder);
        
        String text = textBuilder.toString();
        
        // If no content found in specific tags, extract all text
        if (text.trim().isEmpty()) {
            text = html;
        }
        
        // Remove all remaining HTML tags
        text = HTML_TAG_PATTERN.matcher(text).replaceAll(" ");
        
        // Decode common HTML entities
        text = decodeHtmlEntities(text);
        
        // Normalize whitespace
        text = WHITESPACE_PATTERN.matcher(text).replaceAll(" ");
        
        // Trim and return
        return text.trim();
    }
    
    private static void extractFromTag(String html, String tagName, StringBuilder builder) {
        Pattern pattern = Pattern.compile("<" + tagName + "[^>]*>(.*?)</" + tagName + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String content = matcher.group(1);
            // Remove nested tags
            content = HTML_TAG_PATTERN.matcher(content).replaceAll(" ");
            content = decodeHtmlEntities(content);
            content = WHITESPACE_PATTERN.matcher(content).replaceAll(" ").trim();
            if (!content.isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(content);
            }
        }
    }
    
    private static String decodeHtmlEntities(String text) {
        // Decode common HTML entities
        text = text.replace("&nbsp;", " ");
        text = text.replace("&amp;", "&");
        text = text.replace("&lt;", "<");
        text = text.replace("&gt;", ">");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");
        text = text.replace("&#39;", "'");
        text = text.replace("&mdash;", "—");
        text = text.replace("&ndash;", "–");
        text = text.replace("&hellip;", "...");
        text = text.replace("&copy;", "©");
        text = text.replace("&reg;", "®");
        text = text.replace("&trade;", "™");
        text = text.replace("&euro;", "€");
        text = text.replace("&pound;", "£");
        text = text.replace("&yen;", "¥");
        text = text.replace("&cent;", "¢");
        
        // Decode numeric entities (&#123; format)
        Pattern numericEntity = Pattern.compile("&#(\\d+);");
        Matcher matcher = numericEntity.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(result, String.valueOf((char) code));
        }
        matcher.appendTail(result);
        text = result.toString();
        
        // Decode hex entities (&#x1F; format)
        Pattern hexEntity = Pattern.compile("&#x([0-9a-fA-F]+);");
        matcher = hexEntity.matcher(text);
        result = new StringBuffer();
        while (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(result, String.valueOf((char) code));
        }
        matcher.appendTail(result);
        text = result.toString();
        
        return text;
    }
    
    /**
     * Limits the extracted text to a maximum length to avoid token limits
     */
    public static String extractTextWithLimit(String html, int maxLength) {
        String text = extractText(html);
        if (text.length() > maxLength) {
            text = text.substring(0, maxLength);
            // Try to cut at a word boundary
            int lastSpace = text.lastIndexOf(' ');
            if (lastSpace > maxLength * 0.9) {
                text = text.substring(0, lastSpace);
            }
            text += "...";
        }
        return text;
    }
}

