package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

import java.io.IOException;
import java.util.Base64;

public class Main {

    public static void main(String[] args) {
        String inputFilePath = "C:\\Users\\Hehe\\Test documents\\docxencodedbase64.txt"; // Path to your Base64-encoded text file
        String outputFilePath = "C:\\Users\\Hehe\\Test documents\\Tagsoutput.docx"; // Path to save the decoded DOCX file

        try {
            // Read Base64 content from the input file
            String base64Content = readFile(inputFilePath);

            // Decode Base64
            byte[] decodedBytes = Base64.getDecoder().decode(base64Content);

            // Write the decoded content to the output DOCX file
            writeFile(outputFilePath, decodedBytes);

            System.out.println("Decoding completed. Output saved to: " + outputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readFile(String filePath) throws IOException {
        File file = new File(filePath);
        FileInputStream fis = new FileInputStream(file);
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = fis.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, bytesRead));
        }
        fis.close();

        return sb.toString().trim(); // Return trimmed content
    }

    private static void writeFile(String filePath, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(filePath);
        fos.write(data);
        fos.close();
    }
}
