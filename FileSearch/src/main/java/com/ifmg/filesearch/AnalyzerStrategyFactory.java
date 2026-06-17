package com.ifmg.filesearch;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;

import java.util.HashMap;
import java.util.Map;

public class AnalyzerStrategyFactory {

    public static Analyzer createMultilingualAnalyzer() {
        Map<String, Analyzer> analyzerMap = new HashMap<>();
        analyzerMap.put("content_pt", new BrazilianAnalyzer());
        analyzerMap.put("content_en", new EnglishAnalyzer());

        // Se o campo não for content_pt ou content_en, usa o StandardAnalyzer como fallback (ex: filename)
        return new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerMap);
    }
}
