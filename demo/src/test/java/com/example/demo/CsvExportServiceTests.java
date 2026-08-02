package com.example.demo;

import com.example.demo.service.CsvExportService;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CsvExportServiceTests {
    private final CsvExportService csv = new CsvExportService();
    @Test void writesBomHeaderAndCrLfForEmptyReport() { String text=new String(csv.export(List.of("Tên"),List.of()), StandardCharsets.UTF_8); assertTrue(text.startsWith("\uFEFF\"Tên\"\r\n")); }
    @Test void escapesQuotesCommaAndFormulaInjection() { String text=new String(csv.export(List.of("Name"),List.of(new ArrayList<>(List.of("=SUM(1,2) \"Bún\"")))), StandardCharsets.UTF_8); assertTrue(text.contains("\"'=SUM(1,2) \"\"Bún\"\"\"")); }
    @Test void keepsNumbersAsNumbers() { String text=new String(csv.export(List.of("Amount"),List.of(new ArrayList<>(List.of(123)))), StandardCharsets.UTF_8); assertTrue(text.contains("\"123\"")); }
}
