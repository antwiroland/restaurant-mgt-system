package com.restaurantmanager.core.hr.report;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class HrReportCsvTest {
    @Test
    void cell_quotesWhenNeeded_andDefusesSpreadsheetFormulas() {
        assertThat(HrReportService.cell(null)).isEmpty();
        assertThat(HrReportService.cell("Ama Owusu")).isEqualTo("Ama Owusu");
        assertThat(HrReportService.cell("Osu, Accra")).isEqualTo("\"Osu, Accra\"");
        assertThat(HrReportService.cell("He said \"hi\"")).isEqualTo("\"He said \"\"hi\"\"\"");
        assertThat(HrReportService.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(HrReportService.cell("+233201000001")).isEqualTo("\"'+233201000001\"");
        assertThat(HrReportService.cell("@SUM(A1)")).startsWith("\"'@");
        assertThat(HrReportService.cell(new BigDecimal("-12.50"))).isEqualTo("-12.50"); // numbers stay numbers
        assertThat(HrReportService.cell(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(HrReportService.cell(LocalDate.of(2026, 11, 12))).isEqualTo("2026-11-12");
    }
}
