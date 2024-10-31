package org.questionnaire;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class Main implements RequestHandler<QuestionnaireInput, String> {

    @Override
    public String handleRequest(QuestionnaireInput input, Context context) {
        try {

            byte[] docxBytes = Base64.getDecoder().decode(input.getDocxBase64());
            XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docxBytes));


            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(input.getJsonInput());
            JsonNode questionGroups = rootNode.path("Q1").path("QuestionGroup");

            XWPFTable table = document.getTables().get(0);

            for (int i = table.getRows().size() - 1; i > 0; i--) {
                table.removeRow(i);
            }


            for (JsonNode group : questionGroups) {
                String groupName = group.path("Name").asText();
                JsonNode questions = group.path("Question");

                for (JsonNode question : questions) {
                    String questionName = question.path("Name").asText();
                    StringBuilder answers = new StringBuilder();


                    for (JsonNode answer : question.path("Answer")) {
                        if (answers.length() > 0) {
                            answers.append(", ");
                        }
                        answers.append(answer.path("Name").asText());
                    }

                    // Add a new row to the table
                    XWPFTableRow row = table.createRow();
                    row.getCell(0).setText(groupName);
                    row.getCell(1).setText(questionName);
                    row.getCell(2).setText(answers.toString());
                }
            }


            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            document.close();


            return Base64.getEncoder().encodeToString(outputStream.toByteArray());

        } catch (Exception e) {
            context.getLogger().log("Error processing the request: " + e.getMessage());
            return null;
        }
    }
}


class QuestionnaireInput {
    private String docxBase64;
    private String jsonInput;


    public String getDocxBase64() {
        return docxBase64;
    }

    public void setDocxBase64(String docxBase64) {
        this.docxBase64 = docxBase64;
    }

    public String getJsonInput() {
        return jsonInput;
    }

    public void setJsonInput(String jsonInput) {
        this.jsonInput = jsonInput;
    }
}