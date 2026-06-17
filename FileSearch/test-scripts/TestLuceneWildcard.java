
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.document.Document;
import java.nio.file.Paths;

public class TestLuceneWildcard {
    public static void main(String[] args) throws Exception {
        String indexPath = Paths.get(System.getProperty("user.home"), ".filesearch", "lucene_index").toString();
        try (FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
             IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(new WildcardQuery(new Term("filename", "*.pdf")), 10);
            System.out.println("Total PDFs by filename: " + docs.totalHits.value);
            for (var scoreDoc : docs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                System.out.println("Found: " + doc.get("filename") + " Ext: " + doc.get("extension"));
            }
        }
    }
}

