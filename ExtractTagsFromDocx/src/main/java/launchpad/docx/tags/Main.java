package launchpad.docx.tags;

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
        // Extract the base64 encoded DOCX from the input map
        String base64Docx = (String) input.get("docxBase64");

        if (base64Docx == null || base64Docx.isEmpty()) {
            context.getLogger().log("No base64 input received");
            return "Error: No base64 input received";
        }

        try {
            // Decode the base64 DOCX content
            byte[] decodedBytes = Base64.decodeBase64(base64Docx);
            XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(decodedBytes));

            List<String> tags = new ArrayList<>();  // Use List to preserve order
            Pattern tagPattern = Pattern.compile("\\{\\{([^\\}]+)\\}\\}");  // Regex for capturing content within {{ }}

            // Log the number of paragraphs in the document
            context.getLogger().log("Number of paragraphs: " + document.getParagraphs().size());

            // Process paragraphs (standard text blocks)
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
                    tags.add(matcher.group());  // Add tags to the List (preserves order)
                }
            }

            // Process tables (cells within tables)
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            String paragraphText = "";
                            for (XWPFRun run : paragraph.getRuns()) {
                                String text = run.getText(0);
                                if (text != null) {
                                    paragraphText += text;  // Concatenate all text in the cell
                                }
                            }
                            // Log the table cell text for debugging
                            context.getLogger().log("Table Cell Text: " + paragraphText);

                            // Match tags in table cell text
                            Matcher matcher = tagPattern.matcher(paragraphText);
                            while (matcher.find()) {
                                tags.add(matcher.group());  // Add tags to the List (preserves order)
                            }
                        }
                    }
                }
            }

            // Return a comma-separated list of all the tags found
            if (tags.isEmpty()) {
                context.getLogger().log("No tags found in the document");
            } else {
                context.getLogger().log("Tags found: " + String.join(",", tags));
            }

            return String.join(",", tags);  // Return tags in the order they were found

        } catch (IOException e) {
            // Log any errors during processing
            context.getLogger().log("Error processing DOCX file: " + e.getMessage());
            return "Error processing DOCX file: " + e.getMessage();
        }
    }
}