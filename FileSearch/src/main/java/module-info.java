module com.ifmg.filesearch {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;
    requires java.desktop;
    requires org.apache.lucene.core;
    requires org.apache.lucene.queryparser;
    requires org.apache.lucene.analysis.common;
    requires org.apache.tika.core;
    requires org.slf4j;

    opens com.ifmg.filesearch to javafx.fxml, com.fasterxml.jackson.databind;
    exports com.ifmg.filesearch;
}