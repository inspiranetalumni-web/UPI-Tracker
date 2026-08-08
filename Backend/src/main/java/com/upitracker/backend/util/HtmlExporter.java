package com.upitracker.backend.util;

import com.upitracker.backend.model.Expense;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class HtmlExporter {

    public static String export(List<Expense> expenses) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("    <title>Bank Statement</title>\n");
        sb.append("    <style>\n");
        sb.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }\n");
        sb.append("        .container { max-width: 900px; margin: 0 auto; background: #fff; padding: 30px; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); }\n");
        sb.append("        h1 { color: #1a73e8; text-align: center; margin-bottom: 20px; }\n");
        sb.append("        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        sb.append("        th, td { padding: 12px 15px; border-bottom: 1px solid #e0e0e0; text-align: left; }\n");
        sb.append("        th { background-color: #1a73e8; color: white; text-transform: uppercase; font-size: 14px; }\n");
        sb.append("        tr:hover { background-color: #f1f5f9; }\n");
        sb.append("        .credit { color: #2e7d32; font-weight: bold; }\n");
        sb.append("        .debit { color: #d32f2f; font-weight: bold; }\n");
        sb.append("        .footer { text-align: center; margin-top: 30px; font-size: 12px; color: #777; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("    <div class=\"container\">\n");
        sb.append("        <h1>Expense Tracker - Bank Statement</h1>\n");
        sb.append("        <table>\n");
        sb.append("            <thead>\n");
        sb.append("                <tr>\n");
        sb.append("                    <th>Date</th>\n");
        sb.append("                    <th>Payee</th>\n");
        sb.append("                    <th>Category</th>\n");
        sb.append("                    <th>Mode</th>\n");
        sb.append("                    <th>Type</th>\n");
        sb.append("                    <th style=\"text-align: right;\">Amount (₹)</th>\n");
        sb.append("                </tr>\n");
        sb.append("            </thead>\n");
        sb.append("            <tbody>\n");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

        for (Expense e : expenses) {
            sb.append("                <tr>\n");
            sb.append("                    <td>").append(e.getDate() != null ? e.getDate().format(formatter) : "").append("</td>\n");
            sb.append("                    <td>").append(escapeHtml(e.getName())).append("</td>\n");
            sb.append("                    <td>").append(escapeHtml(e.getCategory())).append("</td>\n");
            sb.append("                    <td>").append(escapeHtml(e.getUpiApp())).append("</td>\n");
            sb.append("                    <td>").append(escapeHtml(e.getType())).append("</td>\n");
            
            String amountClass = "debit".equalsIgnoreCase(e.getType()) ? "debit" : "credit";
            String prefix = "debit".equalsIgnoreCase(e.getType()) ? "- " : "+ ";
            
            sb.append("                    <td class=\"").append(amountClass).append("\" style=\"text-align: right;\">")
              .append(prefix).append(String.format("%.2f", e.getAmount()))
              .append("</td>\n");
            sb.append("                </tr>\n");
        }

        sb.append("            </tbody>\n");
        sb.append("        </table>\n");
        sb.append("        <div class=\"footer\">\n");
        sb.append("            Generated automatically by UPI Tracker App\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        
        return sb.toString();
    }
    
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}
