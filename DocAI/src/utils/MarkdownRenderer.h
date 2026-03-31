#ifndef MARKDOWNRENDERER_H
#define MARKDOWNRENDERER_H

#include <QString>
#include <QStringList>
#include <QRegExp>

/**
 * MarkdownRenderer - 将 Markdown 文本转换为 Qt QTextBrowser 可完美渲染的 HTML。
 * 支持：h1-h6 标题、加粗/斜体/行内代码、有序/无序列表、围栏代码块、表格、引用块、分隔线、链接。
 */
class MarkdownRenderer {
public:
    static QString toHtml(const QString &markdown) {
        QStringList lines = markdown.split('\n');
        QString html;
        bool inCodeBlock = false;
        bool inTable = false;
        bool inOrderedList = false;
        bool inUnorderedList = false;
        bool inBlockquote = false;

        auto closeList = [&]() {
            if (inOrderedList)   { html += "</ol>\n"; inOrderedList = false; }
            if (inUnorderedList) { html += "</ul>\n"; inUnorderedList = false; }
        };
        auto closeBlockquote = [&]() {
            if (inBlockquote) { html += "</blockquote>\n"; inBlockquote = false; }
        };
        auto closeTable = [&]() {
            if (inTable) { html += "</tbody></table>\n"; inTable = false; }
        };

        for (int i = 0; i < lines.size(); ++i) {
            QString line = lines[i];

            // ---- Fenced code block ----
            if (line.trimmed().startsWith("```")) {
                if (!inCodeBlock) {
                    closeList(); closeBlockquote(); closeTable();
                    inCodeBlock = true;
                    html += "<pre style=\"background:#F3F4F6;border:1px solid #E5E7EB;border-radius:6px;"
                            "padding:12px 16px;font-family:'Consolas','Courier New',monospace;"
                            "font-size:13px;color:#1F2937;overflow-x:auto;margin:8px 0;\">";
                } else {
                    inCodeBlock = false;
                    html += "</pre>\n";
                }
                continue;
            }
            if (inCodeBlock) {
                html += escapeHtml(line) + "\n";
                continue;
            }

            QString trimmed = line.trimmed();

            // ---- Blank line ----
            if (trimmed.isEmpty()) {
                closeList(); closeBlockquote(); closeTable();
                continue;
            }

            // ---- Horizontal rule ----
            if (QRegExp("^[-*_]{3,}$").exactMatch(trimmed)) {
                closeList(); closeBlockquote(); closeTable();
                html += "<hr style=\"border:none;border-top:1px solid #E5E7EB;margin:12px 0;\">\n";
                continue;
            }

            // ---- Headings (# to ######) ----
            QRegExp hRx("^(#{1,6})\\s+(.+)$");
            if (hRx.exactMatch(trimmed)) {
                closeList(); closeBlockquote(); closeTable();
                int level = hRx.cap(1).length();
                QString text = processInline(hRx.cap(2));
                int fontSize = 22 - (level - 1) * 2; // h1=22, h2=20, h3=18, h4=16, h5=14, h6=13
                if (fontSize < 13) fontSize = 13;
                QString weight = (level <= 3) ? "700" : "600";
                QString margin = (level <= 2) ? "16px 0 8px 0" : "12px 0 6px 0";
                QString color = (level <= 2) ? "#111827" : "#374151";
                html += QString("<p style=\"font-size:%1px;font-weight:%2;color:%3;margin:%4;\">%5</p>\n")
                        .arg(fontSize).arg(weight, color, margin, text);
                continue;
            }

            // ---- Blockquote ----
            if (trimmed.startsWith(">")) {
                closeList(); closeTable();
                QString qText = trimmed.mid(1).trimmed();
                if (qText.startsWith(" ")) qText = qText.mid(1);
                if (!inBlockquote) {
                    inBlockquote = true;
                    html += "<blockquote style=\"border-left:3px solid #818CF8;padding:8px 12px;"
                            "margin:8px 0;background:#F9FAFB;color:#4B5563;font-size:14px;\">\n";
                }
                html += processInline(qText) + "<br>\n";
                continue;
            } else {
                closeBlockquote();
            }

            // ---- Table ----
            QRegExp tableRx("^\\|(.+)\\|$");
            if (tableRx.exactMatch(trimmed)) {
                closeList(); closeBlockquote();
                // Check if this is a separator row (|---|---|)
                QRegExp sepRx("^\\|[\\s:|-]+\\|$");
                if (sepRx.exactMatch(trimmed)) {
                    continue; // skip separator row
                }
                QStringList cells = trimmed.mid(1, trimmed.length() - 2).split('|');
                if (!inTable) {
                    inTable = true;
                    html += "<table style=\"border-collapse:collapse;width:100%;margin:8px 0;font-size:13px;\">"
                            "<thead><tr>";
                    for (const QString &cell : cells) {
                        html += QString("<th style=\"border:1px solid #E5E7EB;padding:6px 10px;"
                                        "background:#F3F4F6;font-weight:600;text-align:left;\">%1</th>")
                                .arg(processInline(cell.trimmed()));
                    }
                    html += "</tr></thead><tbody>\n";
                } else {
                    html += "<tr>";
                    for (const QString &cell : cells) {
                        html += QString("<td style=\"border:1px solid #E5E7EB;padding:6px 10px;\">%1</td>")
                                .arg(processInline(cell.trimmed()));
                    }
                    html += "</tr>\n";
                }
                continue;
            } else {
                closeTable();
            }

            // ---- Ordered list ----
            QRegExp olRx("^(\\d+)[.)\\]]\\s+(.+)$");
            if (olRx.exactMatch(trimmed)) {
                closeUnordered(html, inUnorderedList); closeBlockquote(); closeTable();
                if (!inOrderedList) {
                    inOrderedList = true;
                    html += "<ol style=\"margin:6px 0;padding-left:24px;font-size:14px;color:#374151;\">\n";
                }
                html += "<li style=\"margin:3px 0;\">" + processInline(olRx.cap(2)) + "</li>\n";
                continue;
            }

            // ---- Unordered list ----
            QRegExp ulRx("^[-*+]\\s+(.+)$");
            if (ulRx.exactMatch(trimmed)) {
                closeOrdered(html, inOrderedList); closeBlockquote(); closeTable();
                if (!inUnorderedList) {
                    inUnorderedList = true;
                    html += "<ul style=\"margin:6px 0;padding-left:24px;font-size:14px;color:#374151;\">\n";
                }
                html += "<li style=\"margin:3px 0;\">" + processInline(ulRx.cap(1)) + "</li>\n";
                continue;
            }

            // ---- Normal paragraph ----
            closeList(); closeBlockquote(); closeTable();
            html += "<p style=\"margin:4px 0;font-size:14px;color:#1F2937;line-height:1.6;text-align:left;\">"
                  + processInline(trimmed) + "</p>\n";
        }

        // Close any open blocks
        if (inCodeBlock) html += "</pre>\n";
        closeList(); closeBlockquote(); closeTable();

        return wrapHtml(html);
    }

private:
    static void closeOrdered(QString &html, bool &flag) {
        if (flag) { html += "</ol>\n"; flag = false; }
    }
    static void closeUnordered(QString &html, bool &flag) {
        if (flag) { html += "</ul>\n"; flag = false; }
    }

    static QString escapeHtml(const QString &text) {
        QString s = text;
        s.replace('&', "&amp;");
        s.replace('<', "&lt;");
        s.replace('>', "&gt;");
        s.replace('"', "&quot;");
        return s;
    }

    static QString processInline(const QString &text) {
        QString s = escapeHtml(text);

        // Inline code: `code`
        s.replace(QRegExp("`([^`]+)`"),
                  "<code style=\"background:#F3F4F6;padding:2px 6px;border-radius:4px;"
                  "font-family:'Consolas','Courier New',monospace;font-size:13px;color:#E11D48;\">\\1</code>");

        // Bold+italic: ***text*** or ___text___
        s.replace(QRegExp("\\*\\*\\*([^*]+)\\*\\*\\*"), "<b><i>\\1</i></b>");
        s.replace(QRegExp("___([^_]+)___"), "<b><i>\\1</i></b>");

        // Bold: **text** or __text__
        s.replace(QRegExp("\\*\\*([^*]+)\\*\\*"), "<b>\\1</b>");
        s.replace(QRegExp("__([^_]+)__"), "<b>\\1</b>");

        // Italic: *text* or _text_
        s.replace(QRegExp("(?<!\\w)\\*([^*]+)\\*(?!\\w)"), "<i>\\1</i>");
        s.replace(QRegExp("(?<!\\w)_([^_]+)_(?!\\w)"), "<i>\\1</i>");

        // Strikethrough: ~~text~~
        s.replace(QRegExp("~~([^~]+)~~"), "<s>\\1</s>");

        // Links: [text](url)
        s.replace(QRegExp("\\[([^\\]]+)\\]\\(([^)]+)\\)"),
                  "<a href=\"\\2\" style=\"color:#4F46E5;text-decoration:underline;\">\\1</a>");

        return s;
    }

    static QString wrapHtml(const QString &body) {
        return QString("<html><body style=\"text-align:left;font-family:'Microsoft YaHei',sans-serif;\">%1</body></html>").arg(body);
    }
};

#endif // MARKDOWNRENDERER_H
