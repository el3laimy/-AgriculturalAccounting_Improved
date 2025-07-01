package accounting.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class ForecastReportController {

    @FXML
    private TextArea forecastOutputArea;

    public void initialize() {
        // Initialization logic if needed in the future
    }

    /**
     * Displays the generated forecast data in the TextArea.
     * @param forecastData The string containing the forecast report.
     */
    public void displayForecast(String forecastData) {
        if (forecastOutputArea != null) {
            forecastOutputArea.setText(forecastData);
        } else {
            // Fallback or logging if the TextArea is somehow not injected, though FXML should handle this.
            System.err.println("ForecastOutputArea is null. Cannot display forecast.");
        }
    }
}
