package fields.supporting.dup;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.util.*;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Main implements RequestHandler<Map<String, String>, String> {

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        try {
            // Params
            String fieldsAndTags = input.get("fieldsAndTags");
            String base64Docx = input.get("base64Docx");

            // Decode docx
            byte[] decodedDocx = Base64.getDecoder().decode(base64Docx);
            ByteArrayInputStream docxInputStream = new ByteArrayInputStream(decodedDocx);
            XWPFDocument docx = new XWPFDocument(docxInputStream);

            // Process pairs
            List<String> rows = new ArrayList<>();
            String[] elements = fieldsAndTags.split(",");
            for (String element : elements) {
                String[] parts = element.split("/");
                if (parts.length == 2) {
                    String caseField = parts[0];
                    String placeholder = parts[1];
                    rows.add(caseField + "," + placeholder);
                }
            }

            // Get table
            XWPFTable table = docx.getTables().get(0);

            // Delete first row
            if (table.getRows().size() > 0) {
                table.removeRow(1);
            }

            // Loop and insert them into the table (including duplicates)
            for (String row : rows) {
                String[] fields = row.split(",");
                String caseField = fields[0];
                String placeholder = fields[1];

                // Create a new row in the table
                XWPFTableRow tableRow = table.createRow();
                XWPFTableCell cell1 = tableRow.getCell(0);
                XWPFTableCell cell2 = tableRow.getCell(1);

                // Set values in correct columns
                cell1.setText(caseField);
                cell2.setText(placeholder);
            }

            // Remove duplicate rows in the table
            removeDuplicateRows(table);

            // Updated DOCX output stream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            docx.write(outputStream);

            // Updated DOCX to Base64
            byte[] modifiedDocxBytes = outputStream.toByteArray();
            String modifiedDocxBase64 = Base64.getEncoder().encodeToString(modifiedDocxBytes);

            // Return the Base64 encoded DOCX document
            return modifiedDocxBase64;

        } catch (IOException e) {
            e.printStackTrace();
            return "Error processing DOCX file: " + e.getMessage();
        }
    }

    // Function to remove duplicate rows based on their text content
    private void removeDuplicateRows(XWPFTable table) {
        Set<String> seen = new HashSet<>();
        List<XWPFTableRow> rowsToRemove = new ArrayList<>();

        for (int i = 1; i < table.getRows().size(); i++) {  // start at 1 to skip header row
            XWPFTableRow row = table.getRow(i);
            String rowText = getRowText(row);

            if (seen.contains(rowText)) {
                // If this row has already been seen, mark it for removal
                rowsToRemove.add(row);
            } else {
                // Otherwise, add the row's text to the 'seen' set
                seen.add(rowText);
            }
        }

        // Remove the marked rows
        for (XWPFTableRow row : rowsToRemove) {
            table.removeRow(table.getRows().indexOf(row));
        }
    }

    // Helper function to extract text from a row (used to compare rows)
    private String getRowText(XWPFTableRow row) {
        StringBuilder rowText = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) {
            rowText.append(cell.getText()).append(",");
        }
        return rowText.toString();
    }
