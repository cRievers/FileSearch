module com.ifmg.filesearch {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.ifmg.filesearch to javafx.fxml;
    exports com.ifmg.filesearch;
}