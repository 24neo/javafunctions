package org.tags.ids;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
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
        JsonNode answerArray = rootNode.path("Q1").path("QuestionGroup");

        for (JsonNode questionGroup : answerArray) {
            JsonNode questions = questionGroup.path("Question");

            for (JsonNode question : questions) {
                String QuestionID = question.path("QuestionID").asText();
                String questionName = question.path("Name").asText();
                System.out.println("Replacing: {{" + QuestionID + "}} with " + questionName);

                String tagToReplaceQ = "{{" + QuestionID + "}}";
                if (text.contains(tagToReplaceQ)) {
                    text = text.replace(tagToReplaceQ, questionName);
                } else {
                    System.out.println("Tag " + tagToReplaceQ + " not found in text.");
                }
                JsonNode answers = question.path("Answer");

                for (JsonNode answer : answers) {
                    String answerID = answer.path("AnswerID").asText();
                    String answerName = answer.path("Name").asText();
                    System.out.println("Replacing: {{" + answerID + "}} with " + answerName);

                    String tagToReplace = "{{" + answerID + "}}";
                    if (text.contains(tagToReplace)) {
                        text = text.replace(tagToReplace, answerName);
                    } else {
                        System.out.println("Tag " + tagToReplace + " not found in text.");
                    }
                }
            }
        }
        return text;
    }
}