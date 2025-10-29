package com.ifmg.filesearch;

import java.io.BufferedReader;
import java.io.FileReader;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FileExtractor {
    public static String extractFile(String filePath) {
        String termination = filePath.substring(filePath.lastIndexOf("."));
        switch(termination){
            case ".txt":
                return readTxt(filePath, 7000);
            case ".docx":
                return readDocx(filePath, 7000);
            default:
                return ("Unsupported file type");
        }
    }

    private static String readTxt(String filePath, int limit) {
        try(BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            int charCount = 0;
            while (line != null && charCount < limit) {
                charCount += line.length();
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String everything = sb.toString();
            return everything;
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private static String readDocx(String filePath, int limit) {
        File file = new File(filePath);
        String result = "";
        try (FileInputStream fis = new FileInputStream(file)) {
            XWPFDocument document = new XWPFDocument(fis);
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                result += paragraph.getText() + System.lineSeparator();
                if (result.length() >= limit) break;
            }
            document.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

}

