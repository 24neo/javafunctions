package fields.extract;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

public class Main implements RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // Get base64
            String base64Pdf = (String) input.get("base64Pdf");
            if (base64Pdf == null || base64Pdf.isEmpty()) {
                return "No base64 PDF data provided.";
            }

            // Decode base64
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);

            // Extract form field names from the PDF
            List<String> fieldNames = extractFormFieldNames(pdfBytes);

            // Return the comma-separated form field names
            return String.join(",", fieldNames);

        } catch (IOException e) {
            context.getLogger().log("Error processing PDF: " + e.getMessage());
            return "Error processing PDF: " + e.getMessage();
        }
    }

    private List<String> extractFormFieldNames(byte[] pdfBytes) throws IOException {
        List<String> fieldNames = new ArrayList<>();

        // Load PDF from byte array
        PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));

        // Access the AcroForm (form) of the PDF
        PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();

        if (acroForm != null) {
            // Get the iterator of fields in the form
            Iterator<PDField> fieldIterator = acroForm.getFieldIterator();
            while (fieldIterator.hasNext()) {
                // Add the field name to the list
                PDField field = fieldIterator.next();
                fieldNames.add(field.getPartialName());
            }
        }

        // Close the document
        document.close();

        return fieldNames;
    }
}