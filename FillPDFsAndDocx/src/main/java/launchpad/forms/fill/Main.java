package launchpad.forms.fill;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main implements RequestHandler<Map<String, Object>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper for JSON parsing

    @Override
    public String handleRequest( Map<String, Object> input, Context context) {
        try {
            // Get the base64 input and determine file type
            String base64Input = (String) input.get("base64Input");
            if (base64Input == null || base64Input.isEmpty()) {
                return "No base64 input received.";
            }

            // Decode base64 input to byte array
            byte[] fileBytes = Base64.getDecoder().decode(base64Input);

            // Detect the file type
            String fileType = detectFileType(fileBytes);

            // Based on file type, call the appropriate function
            if ("PDF".equals(fileType)) {
                // PDF logic
                String base64PDF = (String) input.get("base64Input");
                String jsonCase = (String) input.get("jsonCase");
                return handlePdf(base64PDF, jsonCase, context);
            } else if ("DOCX".equals(fileType)) {
                // DOCX logic
                String base64Docx = (String) input.get("base64Input");
                String jsonInput = (String) input.get("jsonCase");
                return handleDocx(base64Docx, jsonInput, context);
            } else {
                return "Unsupported file type";
            }
        } catch (Exception e) {
            context.getLogger().log("Error processing file: " + e.getMessage());
            return "Error processing file: " + e.getMessage();
        }
    }

    // Detect the file type based on headers
    private String detectFileType(byte[] fileBytes) {
        if (fileBytes.length > 4) {
            // Check if it's pdf, header "%PDF"
            if (fileBytes[0] == 0x25 && fileBytes[1] == 0x50 && fileBytes[2] == 0x44 && fileBytes[3] == 0x46) {
                return "PDF";
            }
            // Check if it's docx header
            else if (fileBytes[0] == 0x50 && fileBytes[1] == 0x4B && fileBytes[2] == 0x03 && fileBytes[3] == 0x04) {
                return "DOCX";
            }
        }
        return "UNKNOWN";
    }

    // PDF logic
    private String handlePdf(String base64PDF, String jsonCase, Context context) {
        try {
            // Sanitize JSON and decode base64 PDF
            jsonCase = sanitizeJsonString(jsonCase);
            byte[] pdfBytes = Base64.getDecoder().decode(base64PDF);
            ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfBytes);

            // Load PDF document
            PDDocument document = PDDocument.load(pdfInputStream);
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

            if (acroForm != null) {
                // Parse the sanitized JSON input into a Map
                Map<String, Object> jsonMap = parseJson(jsonCase);

                for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                    String fieldName = sanitizeFieldName(entry.getKey());
                    Object value = entry.getValue();

                    // Find the matching field in the PDF
                    PDField field = findMatchingField(acroForm, fieldName);

                    if (field != null) {
                        String normalizedValue = normalizeValue(value);

                        if (field instanceof PDComboBox) {
                            PDComboBox comboBox = (PDComboBox) field;
                            List<String> options = comboBox.getOptions();
                            if (options.contains(normalizedValue)) {
                                comboBox.setValue(normalizedValue);
                            } else {
                                comboBox.setValue("");
                            }
                        } else if (field instanceof PDCheckBox) {
                            PDCheckBox checkBox = (PDCheckBox) field;
                            if (normalizedValue.equals("true") || normalizedValue.equals("Yes")) {
                                checkBox.check();
                            } else {
                                checkBox.unCheck();
                            }
                        } else if (field instanceof PDRadioButton) {
                            PDRadioButton radioButton = (PDRadioButton) field;
                            List<String> exportValues = radioButton.getExportValues();
                            String normalizedInputValue = normalizeValue(value).trim().toLowerCase();
                            for (String exportValue : exportValues) {
                                String normalizedExportValue = exportValue.trim().toLowerCase();
                                if (normalizedInputValue.equals(normalizedExportValue)) {
                                    radioButton.setValue(exportValue);
                                    break;
                                }
                            }
                        } else if (field instanceof PDTextField) {
                            PDTextField textField = (PDTextField) field;
                            if (normalizedValue.equals("true") || normalizedValue.equals("Yes")) {
                                textField.setValue("Yes");
                            } else if (normalizedValue.equals("false") || normalizedValue.equals("No")) {
                                textField.setValue("No");
                            } else {
                                textField.setValue(normalizedValue);
                            }
                        } else {
                            field.setValue(normalizedValue);
                        }
                        field.setReadOnly(true); // Make field read-only after setting value
                    }
                }
            }

            document.getDocumentCatalog().getAcroForm().flatten();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            document.close();

            String updatedBase64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            return updatedBase64;

        } catch (Exception e) {
            context.getLogger().log("Error processing PDF: " + e.getMessage());
            return "Error processing PDF: " + e.getMessage();
        }
    }

    // DOCX logic update (handle different tag formats like {{tag}}, {tag}}, }}tag{{)
    private String handleDocx(String base64Docx, String jsonInput, Context context) {
        try {
            return replaceTagsInDocx(base64Docx, jsonInput);
        } catch (Exception e) {
            context.getLogger().log("Error processing DOCX: " + e.getMessage());
            return "Error processing DOCX: " + e.getMessage();
        }
    }

    // Helper methods for PDF logic
    private Map<String, Object> parseJson(String json) throws IOException {
        return objectMapper.readValue(json, Map.class);
    }

    private String normalizeValue(Object value) {
        if (value == null) return "";
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        } else if (value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        } else if (value instanceof Double || value instanceof Float) {
            return String.format("%.2f", value);
        } else {
            return value.toString();
        }
    }

    private String sanitizeJsonString(String json) {
        StringBuilder sanitizedJson = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch < 32 || ch == 127) {
                sanitizedJson.append(String.format("\\u%04x", (int) ch));
            } else {
                sanitizedJson.append(ch);
            }
        }
        return sanitizedJson.toString();
    }

    private String sanitizeFieldName(String fieldName) {
        return fieldName.replaceAll("[\\x00-\\x1F\\x7F]", "_").replaceAll("\\s+", " ").trim();
    }

    private PDField findMatchingField(PDAcroForm acroForm, String fieldName) {
        for (PDField field : acroForm.getFields()) {
            String actualFieldName = sanitizeFieldName(field.getFullyQualifiedName());
            if (actualFieldName.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }

    // DOCX helper method
    private static String replaceTagsInDocx(String base64Docx, String jsonInput) throws Exception {
        byte[] docBytes = Base64.getDecoder().decode(base64Docx);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonInput);
            replaceTagsInDocument(document, rootNode);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        }
    }

    private static void replaceTagsInDocument(XWPFDocument document, JsonNode rootNode) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceTextInParagraph(paragraph, rootNode);
        }

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
        if (paragraphText != null) {
            String newText = replaceTags(paragraphText, rootNode);
            if (!newText.equals(paragraphText)) {
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }
                String[] parts = newText.split("(?<=})|(?=\\{)");
                for (String part : parts) {
                    XWPFRun run = paragraph.createRun();
                    run.setText(part);
                }
            }
        }
    }

    private static String replaceTags(String text, JsonNode rootNode) {
        // Updated regex to handle more tag formats
        String regex = "\\{\\{([^\\s\\}]+)\\}\\}|\\{([^\\s\\}]+)\\}\\}|\\}\\}([^\\s\\}]+)\\{\\{|\\{\\{([^\\s\\}]+)\\}";

        for (Iterator<Map.Entry<String, JsonNode>> it = rootNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            String tag = entry.getKey().trim(); // Trim spaces from the key name
            JsonNode valueNode = entry.getValue();

            // Check if the tag has a valid value in the JSON
            String replacement = null;

            // If the value exists and it's not empty or just spaces, use it
            if (!valueNode.isMissingNode()) {
                replacement = valueNode.asText();
                if (replacement != null && replacement.trim().isEmpty()) {
                    replacement = null;  // Set to null if the value is empty or only spaces
                }
            }

            // Replace the tag only if there's a valid replacement value; otherwise, leave the tag intact
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);

            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String matchedTag = matcher.group(0);
                // Also trim any extra spaces from the matched tag inside the text
                String matchedTagTrimmed = matchedTag.trim();

                if (matchedTagTrimmed.contains(tag)) {
                    if (replacement != null) {
                        // If replacement exists, replace the tag with its corresponding value
                        matchedTag = matchedTag.replace("{{" + tag + "}}", replacement)
                                .replace("{" + tag + "}}", replacement)
                                .replace("}}"+tag+"{{", replacement)
                                .replace("{{" + tag + "}", replacement);
                    }
                }
                // If no replacement or value is empty, leave the tag as it is
                matcher.appendReplacement(sb, matchedTag);
            }
            matcher.appendTail(sb);
            text = sb.toString();
        }

        return text;
    }

}