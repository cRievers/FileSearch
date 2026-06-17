
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.document.Document;
import java.nio.file.Paths;

public class TestLucene {
    public static void main(String[] args) throws Exception {
        String indexPath = Paths.get(System.getProperty("user.home"), ".filesearch", "lucene_index").toString();
        System.out.println("Path: " + indexPath);
        try (FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
             IndexReader reader = DirectoryReader.open(dir)) {
            System.out.println("Total docs: " + reader.numDocs());
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(new MatchAllDocsQuery(), 100);
            int pdfCount = 0;
            for (var scoreDoc : docs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String ext = doc.get("extension");
                if ("pdf".equals(ext)) pdfCount++;
            }
            System.out.println("Sample PDFs: " + pdfCount + " out of first 100");
        }
    }
}

