package com.ifmg.filesearch;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.List;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

public class FileExtractor {
    public static String readFile(String filePath) {
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
        // Placeholder for DOCX reading logic
        try {
			File file = new File(filePath);
			FileInputStream fis = new FileInputStream(file.getAbsolutePath());

			XWPFDocument document = new XWPFDocument(fis);

			List<XWPFParagraph> paragraphs = document.getParagraphs();
			
			System.out.println("Total no of paragraph "+paragraphs.size());
			for (XWPFParagraph para : paragraphs) {
				System.out.println(para.getText());
			}
			fis.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
        return "DOCX reading not implemented yet.";
    }

    public static void main(String[] args) {
        String content = readFile("C:\\Users\\Caio Rievers Duarte\\OneDrive - Instituto Federal de Minas Gerais\\Documents\\docs pessoais\\Curriculo_Caio_Rievers_Duarte.docx");
        System.out.println(content);
    }

}

