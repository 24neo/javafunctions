package launchpad.pdf.extract;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // Input base64
            String base64Pdf = (String) input.get("base64Pdf");
            if (base64Pdf == null || base64Pdf.isEmpty()) {
                return "No base64 PDF data provided.";
            }

            // Decode base64
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

            // Extract form field names from the PDF
            List<String> fieldNames = extractFormFieldNames(pdfBytes);

            // Reverse the order of fields
            Collections.reverse(fieldNames);

            // Return the fields sepparate dby comma
            return String.join(",", fieldNames);

        } catch (IOException e) {
            context.getLogger().log("Error processing PDF: " + e.getMessage());
            return "Error processing PDF: " + e.getMessage();
        }
    }

    private List<String> extractFormFieldNames(byte[] pdfBytes) throws IOException {
        List<FieldWithPosition> fieldsWithPosition = new ArrayList<>();

        // Load PDF from byte array
        PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));

        // Access the AcroForm (form) of the PDF
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

        if (acroForm != null) {
            // Iterate through fields and capture their positions
            Iterator<PDField> fieldIterator = acroForm.getFieldIterator();
            while (fieldIterator.hasNext()) {
                PDField field = fieldIterator.next();
                PDRectangle position = field.getWidgets().get(0).getRectangle();
                fieldsWithPosition.add(new FieldWithPosition(field.getPartialName(), position));
            }

            // Sort fields first by vertical position, then by horizontal position
            fieldsWithPosition.sort(Comparator
                    .comparingDouble((FieldWithPosition f) -> f.position.getLowerLeftY()) // Sort by vertical position (top to bottom)
                    .thenComparingDouble(f -> f.position.getLowerLeftX())); // Then sort by horizontal position (left to right)
        }

        // Extract sorted fields
        List<String> sortedFieldNames = new ArrayList<>();
        for (FieldWithPosition fieldWithPosition : fieldsWithPosition) {
            sortedFieldNames.add(fieldWithPosition.fieldName);
        }

        // Close
        document.close();

        return sortedFieldNames;
    }

    private static class FieldWithPosition {
        String fieldName;
        PDRectangle position;

        FieldWithPosition(String fieldName, PDRectangle position) {
            this.fieldName = fieldName;
            this.position = position;
        }
    }
}