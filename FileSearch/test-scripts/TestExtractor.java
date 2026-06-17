
import com.ifmg.filesearch.FileExtractor;
import java.io.File;

public class TestExtractor {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing Tika Parser availability:");
        String result = FileExtractor.extractFile("C:\\Users\\Caio Rievers Duarte\\Documents\\TestFile.pdf"); // We need a real PDF path or just see the console output
        System.out.println("Result: " + result);
    }
}

