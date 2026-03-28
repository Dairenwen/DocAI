#ifndef DATAMODELS_H
#define DATAMODELS_H

#include <QString>
#include <QDateTime>
#include <QJsonObject>
#include <QJsonArray>
#include <QList>

struct SourceDocument {
    int id = 0;
    QString fileName;
    QString fileType;
    int fileSize = 0;
    QString status; // "uploaded","extracting","extracted","failed"
    QString uploadTime;
    QJsonObject extractedFields;

    static SourceDocument fromJson(const QJsonObject &obj) {
        SourceDocument d;
        d.id = obj["id"].toInt();
        d.fileName = obj["fileName"].toString();
        d.fileType = obj["fileType"].toString();
        d.fileSize = obj["fileSize"].toInt();
        d.status = obj["uploadStatus"].toString();
        d.uploadTime = obj["createdAt"].toString();
        return d;
    }
};

struct Conversation {
    int id = 0;
    QString title;
    QString createdAt;
    QString updatedAt;

    static Conversation fromJson(const QJsonObject &obj) {
        Conversation c;
        c.id = obj["id"].toInt();
        c.title = obj["title"].toString();
        c.createdAt = obj["createdAt"].toString();
        c.updatedAt = obj["updatedAt"].toString();
        return c;
    }
};

struct ChatMessage {
    int id = 0;
    QString role;    // "user" or "assistant"
    QString content;
    QString createdAt;

    static ChatMessage fromJson(const QJsonObject &obj) {
        ChatMessage m;
        m.id = obj["id"].toInt();
        m.role = obj["role"].toString();
        m.content = obj["content"].toString();
        m.createdAt = obj["createdAt"].toString();
        return m;
    }
};

struct TemplateFile {
    int id = 0;
    QString fileName;
    QString status;
    QString uploadTime;

    static TemplateFile fromJson(const QJsonObject &obj) {
        TemplateFile t;
        t.id = obj["id"].toInt();
        t.fileName = obj["fileName"].toString();
        t.status = obj["status"].toString();
        t.uploadTime = obj["uploadTime"].toString();
        return t;
    }
};

struct LlmProvider {
    QString name;
    QString displayName;
    bool active = false;

    static LlmProvider fromJson(const QJsonObject &obj) {
        LlmProvider p;
        p.name = obj["name"].toString();
        p.displayName = obj["displayName"].toString();
        p.active = obj["active"].toBool();
        return p;
    }
};

#endif // DATAMODELS_H
