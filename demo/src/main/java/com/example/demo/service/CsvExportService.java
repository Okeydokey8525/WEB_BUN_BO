package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.TemporalAccessor;
import java.util.List;

@Service
public class CsvExportService {
    public byte[] export(List<String> header, List<? extends List<?>> rows) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        writeRow(csv, header);
        for (List<?> row : rows) writeRow(csv, row);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private void writeRow(StringBuilder csv, List<?> row) {
        for (int index = 0; index < row.size(); index++) { if (index > 0) csv.append(','); csv.append(field(row.get(index))); }
        csv.append("\r\n");
    }
    private String field(Object value) {
        if (value == null) return "";
        String text = value instanceof BigDecimal || value instanceof Number || value instanceof TemporalAccessor ? value.toString() : String.valueOf(value);
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !(value instanceof Number)) text = "'" + text;
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
