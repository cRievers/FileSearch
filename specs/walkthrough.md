# Multilingual Lucene Search Implementation Walkthrough

The project now supports proper multilingual Lucene searches, gracefully distinguishing Portuguese from English text and indexing them using the optimal Lucene standard analyzers.

## What was changed

### 1. `AnalyzerStrategyFactory`
A new factory class `AnalyzerStrategyFactory.java` was introduced to encapsulate the Lucene Analyzer creation strategy. It leverages the `PerFieldAnalyzerWrapper` to direct fields to appropriate analyzers:
- `content_pt` uses `BrazilianAnalyzer`.
- `content_en` uses `EnglishAnalyzer`.
- Any other field (e.g., `filename`) falls back to `StandardAnalyzer`.

### 2. File Indexing (`FileIndexer.java`)
- **Tika Language Detection Integration:** The Apache Tika `LanguageDetector` API was instantiated to recognize the dominant language of extracted document contents.
- **Dynamic Fields:** Based on language detection (`en` vs others), document contents are stored under the corresponding field (`content_en` or `content_pt`).
- **Dependencies Updated:** Added `tika-langdetect-optimaize` to the project's `pom.xml` to provide Tika with the robust `optimaize` language detection implementation. 

### 3. Smart Filtering (`Filter.java`)
- Replaced explicit field search query parsing (`QueryParser`) with `MultiFieldQueryParser` configured against `content_pt`, `content_en`, and `filename`.
- Queries dynamically apply stemming, stop words filtering, and specific rules of Portuguese or English concurrently, making accurate multilingual searches possible.
- The `Filter` explicitly checks and gathers results from either field, ensuring compatibility with the legacy `content` field.

## Verification
- Code successfully built using `mvn clean compile` ensuring no missing dependencies and zero compilation issues.
- The new logic protects the user flow against undetectable text (falls back to Portuguese `content_pt` automatically).
