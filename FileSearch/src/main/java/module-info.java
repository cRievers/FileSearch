module com.ifmg.filesearch {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.ooxml;
    requires org.apache.poi.poi;

    opens com.ifmg.filesearch to javafx.fxml;
    exports com.ifmg.filesearch;
}