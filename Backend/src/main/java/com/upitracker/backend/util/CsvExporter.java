package com.upitracker.backend.util;

import com.upitracker.backend.model.Expense;
import java.util.List;

public class CsvExporter {

    public static String export(List<Expense> expenses) {
        String header = "Date,Payee,Amount,Category,UPIApp,Note,UPIRef\n";
        StringBuilder csv = new StringBuilder(header);

        for (Expense e : expenses) {
            String date = e.getDate() != null ? e.getDate().toDate().toInstant().toString().split("T")[0] : "";
            csv.append(escape(date)).append(",")
               .append(escape(e.getPayee())).append(",")
               .append(escape(String.valueOf(e.getAmount()))).append(",")
               .append(escape(e.getCategory())).append(",")
               .append(escape(e.getUpiApp())).append(",")
               .append(escape(e.getNote())).append(",")
               .append(escape(e.getUpiRef())).append("\n");
        }
        return csv.toString();
    }

    private static String escape(String val) {
        if (val == null || val.equals("null")) return "";
        String str = val;
        if (str.matches("^[=+\\-@].*")) {
            str = "'" + str;
        }
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
