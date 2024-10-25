package org.envelope.base64;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Map;

public class Main implements RequestHandler<Map<String, String>, String> {

    @Override
    public String handleRequest(Map<String, String> input, Context context) {
        try {
            String url = input.get("url"); // uri
            String authToken = input.get("authToken"); // authToken
            return fetchPdfAsBase64(url, authToken);
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return "Error processing request";
        }
    }

    public static String fetchPdfAsBase64(String urlString, String authToken) throws Exception {
        // create URL object
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // request method GET
        connection.setRequestMethod("GET");
        // set auth header
        connection.setRequestProperty("Authorization", authToken);

        // check the response code
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed: HTTP error code: " + responseCode);
        }

        // read the input stream and convert to byte array
        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }

            // get the byte array from the output stream
            byte[] pdfBytes = byteArrayOutputStream.toByteArray();

            // byte array to Base64
            return Base64.getEncoder().encodeToString(pdfBytes);
        } finally {
            connection.disconnect(); // connection close
        }
    }
}
