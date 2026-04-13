package com.axiom.launcher.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Minimal CSV reader/writer for CLI import/export commands.
 *
 * Assumptions: no quoted fields containing commas, UTF-8 encoding,
 * first row is the header. Sufficient for launcher's controlled CSV format.
 */
final class CsvUtil
{
    private CsvUtil() {}

    /**
     * Parses a CSV file and returns each data row as a header→value map.
     * Empty lines are skipped. Values are trimmed.
     */
    static List<Map<String, String>> read(File file) throws IOException
    {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty()) return Collections.emptyList();

        String[] headers = splitTrim(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++)
        {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] values = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.length; j++)
                row.put(headers[j], j < values.length ? values[j].trim() : "");
            rows.add(row);
        }

        return rows;
    }

    /**
     * Writes a CSV file with the given headers and rows.
     * Existing file is overwritten.
     */
    static void write(File file, List<String> headers, List<String[]> rows) throws IOException
    {
        file.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)))
        {
            pw.println(String.join(",", headers));
            for (String[] row : rows)
            {
                // Escape commas inside cell values with double-quotes
                String[] escaped = new String[row.length];
                for (int i = 0; i < row.length; i++)
                    escaped[i] = row[i] != null && row[i].contains(",")
                        ? "\"" + row[i] + "\"" : (row[i] != null ? row[i] : "");
                pw.println(String.join(",", escaped));
            }
        }
    }

    /** Returns the value or "" if null/absent. */
    static String get(Map<String, String> row, String key)
    {
        String v = row.get(key);
        return v != null ? v : "";
    }

    /** Parses an int from a map value, returning {@code defaultVal} on blank/error. */
    static int getInt(Map<String, String> row, String key, int defaultVal)
    {
        String v = get(row, key);
        if (v.isEmpty()) return defaultVal;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private static String[] splitTrim(String line)
    {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }
}
