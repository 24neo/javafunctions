package launchpad.docx.extract;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main implements RequestHandler<Map<String, Object>, String> {

    public String handleRequest(Map<String, Object> input, Context context) {
        // Extract the base64 encoded DOCX
        String base64Docx = (String) input.get("docxBase64");

        if (base64Docx == null || base64Docx.isEmpty()) {
            context.getLogger().log("No base64 input received");
            return "Error: No base64 input received";
        }

        try {
            // Decode the base64 docx
            byte[] decodedBytes = Base64.decodeBase64(base64Docx);
            XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(decodedBytes));

            List<String> tags = new ArrayList<>();  // Use List to preserve order
            List<String> issues = new ArrayList<>(); // To store issue messages

            // Acceoting {tag}}, {{tag}, and {{tag}
            Pattern tagPattern = Pattern.compile("\\{\\{([^\\}]+)\\}\\}|\\{([^\\}]+)\\}\\}|\\{\\{([^\\}]+)\\}");

            // Process paragraphs
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String paragraphText = "";
                for (XWPFRun run : paragraph.getRuns()) {
                    String text = run.getText(0);
                    if (text != null) {
                        paragraphText += text;  // Concatenate all text in the paragraph
                    }
                }
                // Log the paragraph text for debugging
                context.getLogger().log("Paragraph Text: " + paragraphText);

                // Match tags in paragraph text
                Matcher matcher = tagPattern.matcher(paragraphText);
                while (matcher.find()) {
                    String tag = matcher.group();

                    // Add the tag to the list
                    tags.add(tag);

                    // Handle tags that are more than 50 characters
                    if (tag.length() - 4 > 50) {
                        issues.add("Tag is too long (more than 50 characters excluding curly brackets): " + tag);
                    }
                }
            }

            // Process tables
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            String paragraphText = "";
                            for (XWPFRun run : paragraph.getRuns()) {
                                String text = run.getText(0);
                                if (text != null) {
                                    paragraphText += text;
                                }
                            }
                            // Logs
                            context.getLogger().log("Table Cell Text: " + paragraphText);

                            // Match tags in table cell text
                            Matcher matcher = tagPattern.matcher(paragraphText);
                            while (matcher.find()) {
                                String tag = matcher.group();

                                // Add the tag to the list regardless of its format (e.g., {tag}}, {{tag})
                                tags.add(tag);

                                // Handle tags with more than 50 characters
                                if (tag.length() - 4 > 50) {
                                    issues.add("Tag is too long (more than 50 characters excluding curly brackets): " + tag);
                                }
                            }
                        }
                    }
                }
            }

            // Prepare output message
            String output = "";

            if (!issues.isEmpty()) {
                // If there are issues, return the issues list
                output = "Issues found:\n" + String.join("\n", issues);
            } else if (tags.isEmpty()) {
                // If no tags were found, indicate that no tags were found
                output = "No tags found in the document";
            } else {
                // If no issues, return the valid tags found
                output = String.join(",", tags);
            }

            return output;
        } catch (IOException e) {
            // Log any errors during processing
            context.getLogger().log("Error processing DOCX file: " + e.getMessage());
            return "Error processing DOCX file: " + e.getMessage();
        }
    }
}