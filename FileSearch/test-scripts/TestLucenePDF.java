
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import java.nio.file.Paths;

public class TestLucenePDF {
    public static void main(String[] args) throws Exception {
        String indexPath = Paths.get(System.getProperty("user.home"), ".filesearch", "lucene_index").toString();
        try (FSDirectory dir = FSDirectory.open(Paths.get(indexPath));
             IndexReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(new TermQuery(new Term("extension", "pdf")), 10);
            System.out.println("Total PDFs: " + docs.totalHits.value);
        }
    }
}

