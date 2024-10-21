package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Base64;

public class Main {

    public static void main(String[] args) {
        try {
            String docxFilePath = "C:\\Users\\You\\Test documents\\Tags.docx"; // file path

            // DOCX to Base64
            String base64Docx = convertDocxToBase64(docxFilePath);

            //JSON example
            String jsonInput = "{ \"Questionnaire\": { \"QuestionGroup\": [ { \"QuestionGroupID\": \"QG01\", \"Name\": \"QG Name 1\", \"Question\": [ { \"QuestionID\": \"Q01\", \"Name\": \"Question Name 1\", \"Answer\": [ { \"AnswerID\": \"A01\", \"Name\": \"Sample Name\" }, { \"AnswerID\": \"A02\", \"Name\": \"Another Name\" } ] } ] } ] } } }";

            // Call the function to replace tags in the DOCX document
            String modifiedDocxBase64 = replaceTagsInDocx(base64Docx, jsonInput);
            System.out.println(modifiedDocxBase64); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String convertDocxToBase64(String filePath) throws Exception {
        File file = new File(filePath);
        byte[] fileBytes = new byte[(int) file.length()];

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.read(fileBytes);
        }

        return Base64.getEncoder().encodeToString(fileBytes);
    }

    public static String replaceTagsInDocx(String base64Docx, String jsonInput) throws Exception {
        // Decode Base64
        byte[] docBytes = Base64.getDecoder().decode(base64Docx);

        // load DOCX document
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docBytes))) {
            // Parsing
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(jsonInput);

            // Replace tags with values from JSON
            replaceTagsInDocument(document, rootNode);

            // Write the modified document to a ByteArrayOutputStream
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
        String text = paragraph.getText();
        System.out.println("Paragraph/Text Cell Text: " + text); //print paragraph text for debugging

        if (text != null) {
            String newText = replaceTags(text, rootNode);
            System.out.println("Original Text: " + text); // for debugging
            System.out.println("New Text: " + newText); // for debugging

            // Rmove all runs in the paragraph
            int runCount = paragraph.getRuns().size();
            for (int i = runCount - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }

            // Create a new run with updated text
            if (!newText.isEmpty()) {
                paragraph.createRun().setText(newText);
            } else {
                System.out.println("No replacement occurred; newText is empty.");
            }
        }
    }

    private static String replaceTags(String text, JsonNode rootNode) {
        //exploring JSON estructure
        JsonNode questionGroups = rootNode.path("Questionnaire").path("QuestionGroup");

        //Loop QuestionGroups
        for (JsonNode questionGroup : questionGroups) {
            String groupId = questionGroup.path("QuestionGroupID").asText();
            String groupName = questionGroup.path("Name").asText();

            //Replace the QuestionGroupID tag
            String groupTag = "{{" + groupId + "}}";
            if (text.contains(groupTag)) {
                System.out.println("Replacing: " + groupTag + " with " + groupName);
                text = text.replace(groupTag, groupName);
            }

            // Loop Questions within the QuestionGroup
            JsonNode questions = questionGroup.path("Question");
            for (JsonNode question : questions) {
                String questionId = question.path("QuestionID").asText();
                String questionName = question.path("Name").asText();

                //replace the QuestionID tag
                String questionTag = "{{" + questionId + "}}";
                if (text.contains(questionTag)) {
                    System.out.println("Replacing: " + questionTag + " with " + questionName);
                    text = text.replace(questionTag, questionName);
                }

                // Process answers for replacements
                JsonNode answers = question.path("Answer");
                for (JsonNode answer : answers) {
                    String answerID = answer.path("AnswerID").asText(); // Get AnswerID
                    String answerName = answer.path("Name").asText(); // Get Name

                    // replace answer tags
                    String answerTag = "{{" + answerID + "}}";
                    if (text.contains(answerTag)) {
                        System.out.println("Replacing: " + answerTag + " with " + answerName);
                        text = text.replace(answerTag, answerName);
                    } else {
                        System.out.println("Tag " + answerTag + " not found in text.");
                    }
                }
            }
        }
        return text;
    }
}
