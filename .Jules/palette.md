## 2026-01-24 - Input Initialization vs Placeholders
**Learning:** JavaFX TextFields initialized with text (e.g., "0.0") do not show promptText. This app uses pre-filled zeros for numeric fields (like in `SaleForm.fxml`), which prevents using placeholders for guidance.
**Action:** For numeric fields, consider removing default "0.0" and handling empty strings in the controller as zero to allow placeholders to show, or use tooltips/helper text instead of placeholders if pre-filling is required.
