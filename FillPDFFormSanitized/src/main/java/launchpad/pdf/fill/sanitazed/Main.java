package launchpad.pdf.fill.sanitized;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON parsing

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // Two inputs
            String base64PDF = (String) input.get("base64PDF");
            String jsonCase = (String) input.get("jsonCase");

            if (base64PDF == null || jsonCase == null) {
                return "Error: Missing required input (base64PDF or jsonCase)";
            }

            // Clean the JSON
            jsonCase = sanitizeJsonString(jsonCase);

            // Decode base64 PDF to a byte array
            byte[] pdfBytes = Base64.getDecoder().decode(base64PDF);
            ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfBytes);

            // Load PDF document
            PDDocument document = PDDocument.load(pdfInputStream);
            PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

            if (acroForm != null) {
                // Parse the sanitized JSON input into a Map
                Map<String, Object> jsonMap = parseJson(jsonCase);

                for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                    // Sanitize the field name by removing or avoiding characters
                    String fieldName = sanitizeFieldName(entry.getKey());
                    Object value = entry.getValue();

                    // Find the matching field in the PDF
                    PDField field = findMatchingField(acroForm, fieldName);

                    if (field != null) {
                        // Normalize the value
                        String normalizedValue = normalizeValue(value);

                        if (field instanceof PDComboBox) { // For dropdowns
                            PDComboBox comboBox = (PDComboBox) field;
                            List<String> options = comboBox.getOptions();

                            // Set the value only if it's a valid dropdown option
                            if (options.contains(normalizedValue)) {
                                comboBox.setValue(normalizedValue);
                            } else {
                                comboBox.setValue(""); // Leave blank if invalid
                            }
                        } else if (field instanceof PDCheckBox) { // For checkbox
                            PDCheckBox checkBox = (PDCheckBox) field;

                            // Check the box if the value is equivalent to "true" or "yes"
                            if (normalizedValue.equals("true") || normalizedValue.equals("Yes")) {
                                checkBox.check();
                            } else {
                                checkBox.unCheck();
                            }
                        } else if (field instanceof PDRadioButton) { // For radio buttons
                            PDRadioButton radioButton = (PDRadioButton) field;

                            // Get options for radio buttons
                            List<String> exportValues = radioButton.getExportValues();

                            // Normalize the input, trim spaces and convert to lowercase
                            String normalizedInputValue = normalizeValue(value).trim().toLowerCase();

                            // Loop through the export values and normalize them
                            for (String exportValue : exportValues) {
                                // Normalize each option
                                String normalizedExportValue = exportValue.trim().toLowerCase();

                                // Check if the input is in a option
                                if (normalizedInputValue.equals(normalizedExportValue)) {
                                    // Select the radio button
                                    radioButton.setValue(exportValue); 
                                    break;
                                }
                            }
                    } else if (field instanceof PDTextField) { // For text fields
                            PDTextField textField = (PDTextField) field;

                            // If the field is a boolean or yes/no, set the value as "Yes" or "No"
                            if (normalizedValue.equals("true") || normalizedValue.equals("Yes")) {
                                textField.setValue("Yes");
                            } else if (normalizedValue.equals("false") || normalizedValue.equals("No")) {
                                textField.setValue("No");
                            } else {
                                textField.setValue(normalizedValue); // Set text
                            }
                        } else {
                            field.setValue(normalizedValue); // Set text fields
                        }
                    }
                }
            }

            // Save to ByteArrayOutputStream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            document.close();

            // Encode updated PDF to base64
            String updatedBase64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            return updatedBase64; // Return base64-encoded PDF
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing PDF: " + e.getMessage();
        }
    }

    // Helper method to parse the JSON string into a Map using Jackson
    private Map<String, Object> parseJson(String json) throws IOException {
        return objectMapper.readValue(json, Map.class); // Convert JSON to a Map
    }

    // Normalize values
    private String normalizeValue(Object value) {
        if (value == null) return "";

        if (value instanceof Boolean) {
            return ((Boolean) value) ? "true" : "false";
        } else if (value instanceof Integer || value instanceof Long) {
            return String.valueOf(value);
        } else if (value instanceof Double || value instanceof Float) {
            return String.format("%.2f", value);
        } else if (value instanceof Date) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format((Date) value); // Format date as a string
        } else {
            return value.toString(); // Handle other object types as strings
        }
    }

    // Helper method to sanitize the field name
    private String sanitizeFieldName(String fieldName) {
        // Replace all non-printable characters ( tabs, newlines) and spaces with an underscore
        return fieldName.replaceAll("[\\x00-\\x1F\\x7F]", "_").replaceAll("\\s+", " ").trim(); // Handle extra spaces
    }

    // Helper method to sanitize the JSON string by avoiding characters
    private String sanitizeJsonString(String json) {
        // Escape tab, newline, etc. in the JSON string
        StringBuilder sanitizedJson = new StringBuilder();

        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch < 32 || ch == 127) { // Non-printable characters (control characters), to be confirmed
                sanitizedJson.append(String.format("\\u%04x", (int) ch));
            } else {
                sanitizedJson.append(ch);
            }
        }

        return sanitizedJson.toString();
    }

    // Find a matching field in the PDF based on sanitized field names
    private PDField findMatchingField(PDAcroForm acroForm, String fieldName) {
        for (PDField field : acroForm.getFields()) {
            String actualFieldName = sanitizeFieldName(field.getFullyQualifiedName());
            if (actualFieldName.equals(fieldName)) {
                return field;
            }
        }
        return null;
    }
}
