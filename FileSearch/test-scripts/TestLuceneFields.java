
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.document.Document;
import java.nio.file.Paths;

public class TestLuceneFields {
    public static void main(String[] args) throws Exception {
        String indexPath = Paths.get(System.getProperty("user.home"), ".filesearch", "lucene_index").toString();
        try (FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
             IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(new MatchAllDocsQuery(), 10);
            for (var scoreDoc : docs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                System.out.println("ID: " + doc.get("id"));
                System.out.println("Ext: " + doc.get("extension"));
                System.out.println("Filename: " + doc.get("filename"));
                System.out.println("---");
            }
        }
    }
}

