module com.ifmg.filesearch {
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;
    requires java.desktop;
    requires transitive org.apache.lucene.core;
    requires org.apache.lucene.queryparser;
    requires transitive org.apache.lucene.analysis.common;
    requires org.apache.lucene.highlighter;
    requires org.apache.tika.core;
    requires java.xml;
    requires org.slf4j;
    requires org.apache.commons.io;
    requires tess4j;
    requires com.sun.jna;
    requires com.microsoft.onnxruntime;
    requires ai.djl.tokenizers;
    requires ai.djl.api;

    opens com.ifmg.filesearch to javafx.fxml, com.fasterxml.jackson.databind;
    exports com.ifmg.filesearch;
}
