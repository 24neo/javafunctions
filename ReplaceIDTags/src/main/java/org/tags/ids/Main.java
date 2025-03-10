package org.tags.ids;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

public class Main implements RequestHandler<Map<String, String>, String> {

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        try {
            String base64Docx = input.get("base64Docx");
            String jsonInput = input.get("jsonInput");

            // Call the function to replace tags in the DOCX document
            return replaceTagsInDocx(base64Docx, jsonInput);
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return "Error processing request";
        }
    }

    public static String replaceTagsInDocx(String base64Docx, String jsonInput) throws Exception {
        // Decode Base64
        byte[] docBytes = Base64.getDecoder().decode(base64Docx);

        // Load DOCX document
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            // Parsing JSON
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonInput);

            // Replace tags with values from JSON
            replaceTagsInDocument(document, rootNode);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }
/*     //This is the new method to suport invalid inputs; this is commented to be activated in case to support other kind of tags
    private static String replaceTags(String text, JsonNode rootNode) {
        // Regex pattern to match all three tag formats: {{tag}}, {tag}}, and {{tag}.
        String regex = "(\\{\\{([^\}]+))\\}\\}|\\{([^\}]+)\\}\\}|\\{\\{([^\}]+)\\})";

        // Iterate json
        for (Iterator<Map.Entry<String, JsonNode>> it = rootNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String tag = entry.getKey(); // {{tag}},{tag}},}}tag{{ or {{tag}
            String replacement = entry.getValue().asText(); //value

            System.out.println("Replacing: " + tag + " with " + replacement);

            // Create a Pattern object
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);

            // Use Matcher to find matches and replace them
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                // Check which tag format was matched
                String matchedTag = matcher.group(0); // Get the full match
                if (matchedTag.contains("{{") && matchedTag.contains("}}")) {
                    // for {{tag}}
                    matchedTag = matchedTag.replace(tag, replacement);
                } else if (matchedTag.contains("{") && matchedTag.contains("}}")) {
                    // for {tag}}
                    matchedTag = matchedTag.replace(tag, replacement);
                    matchedTag = matchedTag.replace(tag, replacement);
                } else if (matchedTag.contains("{{") && !matchedTag.contains("}}")) {
                    // for {{tag}
                    matchedTag = matchedTag.replace(tag, replacement);
                }

                // Append the modified match to the StringBuffer
                matcher.appendReplacement(sb, matchedTag);
            }
            // Append the remaining part of the string
            matcher.appendTail(sb);

            // Update the text with all replacements done
            text = sb.toString();
        }

        return text;
    }*/
    private static void replaceTagsInDocument(XWPFDocument document, JsonNode rootNode) {
        // Replace tags in paragraphs
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceTextInParagraph(paragraph, rootNode);
        }

        // Replace tags in tables
        document.getTables().forEach(table -> {
            for (int rowIndex = 0; rowIndex < table.getNumberOfRows(); rowIndex++) {
                for (int cellIndex = 0; cellIndex < table.getRow(rowIndex).getTableCells().size(); cellIndex++) {
                    XWPFTableCell cell = table.getRow(rowIndex).getCell(cellIndex);
                    for (XWPFParagraph cellParagraph : cell.getParagraphs()) {
                        replaceTextInParagraph(cellParagraph, rootNode);
                    }
                }
            }
        });
    }

    private static void replaceTextInParagraph(XWPFParagraph paragraph, JsonNode rootNode) {
        String paragraphText = paragraph.getText();
        System.out.println("Paragraph/Text Cell Text: " + paragraphText);

        if (paragraphText != null) {
            String newText = replaceTags(paragraphText, rootNode);
            System.out.println("Original Text: " + paragraphText);
            System.out.println("New Text: " + newText);

            if (!newText.equals(paragraphText)) {
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }

                String[] parts = newText.split("(?<=})|(?=\\{)");
                for (String part : parts) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(part);
                }
            } else {
                System.out.println("No replacement occurred; newText is the same as paragraphText.");
            }
        }
    }

    private static String replaceTags(String text, JsonNode rootNode) {
        // Iterate through the rootNode which now contains direct key-value pairs
        for (Iterator<Map.Entry<String, JsonNode>> it = rootNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String tag = entry.getKey(); // for example {{tagname}}
            String replacement = entry.getValue().asText(); // for example "neo24 test"

            System.out.println("Replacing: " + tag + " with " + replacement);

            // Replace the tag in the text if it exists
            if (text.contains(tag)) {
                text = text.replace(tag, replacement);
            } else {
                System.out.println("Tag " + tag + " not found in text.");
            }
        }
        return text;
    }
}
