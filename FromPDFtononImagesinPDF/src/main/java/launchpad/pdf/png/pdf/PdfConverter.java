package launchpad.pdf.png.pdf;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import java.util.*;


public class PdfConverter {


    public static String processPdf(Map<String, String> input) {
        String pdfBase64 = input.get("pdfBase64");

        if (pdfBase64 == null || pdfBase64.isEmpty()) {
            return "Invalid input: PDF Base64 string is missing or empty.";
        }

        try {
            // Decode the input Base64 string
            byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
            ByteArrayInputStream pdfInputStream = new ByteArrayInputStream(pdfBytes);

            // Convert PDF to images
            List<PageImage> pageImages = convertPdfToImages(pdfInputStream);

            // Convert images back to pdf
            String newPdfBase64 = convertImagesToPdf(pageImages);

            // Return final base64
            return newPdfBase64;

        } catch (IOException e) {
            return "Error during PDF to image conversion: " + e.getMessage();
        }
    }

    private static List<PageImage> convertPdfToImages(ByteArrayInputStream pdfInputStream) throws IOException {
        List<PageImage> pageImages = new ArrayList<>();
        PDDocument document = PDDocument.load(pdfInputStream);
        PDFRenderer pdfRenderer = new PDFRenderer(document);

        int numPages = document.getNumberOfPages();
        for (int pageIndex = 0; pageIndex < numPages; pageIndex++) {
            BufferedImage bufferedImage = pdfRenderer.renderImageWithDPI(pageIndex, 300); // Resolution
            PDRectangle originalSize = document.getPage(pageIndex).getMediaBox();
            pageImages.add(new PageImage(bufferedImage, originalSize));
        }

        document.close();
        return pageImages;
    }

    private static String convertImagesToPdf(List<PageImage> pageImages) throws IOException {
        PDDocument newDocument = new PDDocument();

        for (PageImage pageImage : pageImages) {
            PDRectangle pageSize = pageImage.originalSize;
            PDPage newPage = new PDPage(pageSize);
            newDocument.addPage(newPage);

            // Convert bufferedImage to byte array and add to PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(pageImage.image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            PDImageXObject pdImage = PDImageXObject.createFromByteArray(newDocument, imageBytes, "image");

            PDPageContentStream contentStream = new PDPageContentStream(newDocument, newPage);
            contentStream.drawImage(pdImage, 0, 0, pageSize.getWidth(), pageSize.getHeight());
            contentStream.close();
        }

        ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
        newDocument.save(pdfOutputStream);
        newDocument.close();

        return Base64.getEncoder().encodeToString(pdfOutputStream.toByteArray());
    }

    // Class to hold the image and its original page size
    private static class PageImage {
        BufferedImage image;
        PDRectangle originalSize;

        public PageImage(BufferedImage image, PDRectangle originalSize) {
            this.image = image;
            this.originalSize = originalSize;
        }
    }

    // For testing
    public static void main(String[] args) {
        // path
        String filePath = "C:\\Users\\Documents\\largeBase64.txt";

        try {
            // Read base64 from file
            String base64String = readBase64FromFile(filePath);

            // Input map
            Map<String, String> input = new HashMap<>();
            input.put("pdfBase64", base64String);

            String result = PdfConverter.processPdf(input);
            System.out.println(result);

        } catch (IOException e) {
            System.out.println("Error reading Base64 file: " + e.getMessage());
        }
    }

    // Read base64 from txt
    private static String readBase64FromFile(String filePath) throws IOException {
        StringBuilder base64String = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                base64String.append(line.trim());
            }
        }
        return base64String.toString();
    }

}
