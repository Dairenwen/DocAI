#include "SseClient.h"
#include "../utils/TokenManager.h"
#include "../network/ApiClient.h"
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QSslConfiguration>

SseClient::SseClient(QObject *parent) : QObject(parent),
    m_nam(new QNetworkAccessManager(this)) {}

void SseClient::start(const QString &message, int documentId) {
    if (m_running) abort();
    m_running = true;
    m_buffer.clear();
    m_finalText.clear();
    m_modifiedExcelUrl.clear();

    QString baseUrl = ApiClient::instance().baseUrl() + "/ai/chat/stream";
    QUrl requestUrl(baseUrl);
    QNetworkRequest req(requestUrl);
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    req.setSslConfiguration(QSslConfiguration::defaultConfiguration());
    QString token = TokenManager::instance().token();
    if (!token.isEmpty())
        req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());

    QJsonObject body;
    body["userInput"] = message;
    if (documentId > 0)
        body["fileId"] = documentId;
    else
        body["fileId"] = QJsonValue::Null;

    QByteArray data = QJsonDocument(body).toJson(QJsonDocument::Compact);
    m_reply = m_nam->post(req, data);
    m_reply->ignoreSslErrors();
    connect(m_reply, &QNetworkReply::readyRead, this, &SseClient::onReadyRead);
    connect(m_reply, &QNetworkReply::finished, this, &SseClient::onFinished);
}

void SseClient::abort() {
    if (m_reply) {
        m_reply->abort();
        m_reply->deleteLater();
        m_reply = nullptr;
    }
    m_running = false;
}

void SseClient::onReadyRead() {
    if (!m_reply) return;
    m_buffer += QString::fromUtf8(m_reply->readAll());
    int idx = m_buffer.indexOf("\n\n");
    while (idx != -1) {
        QString block = m_buffer.left(idx);
        m_buffer = m_buffer.mid(idx + 2);
        processBlock(block);
        idx = m_buffer.indexOf("\n\n");
    }
}

void SseClient::onFinished() {
    if (!m_reply) return;
    int status = m_reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    bool hadError = false;
    if (m_reply->error() != QNetworkReply::NoError && m_reply->error() != QNetworkReply::OperationCanceledError) {
        hadError = true;
        if (status == 401) {
            emit errorOccurred(QString::fromUtf8("\xe7\x99\xbb\xe5\xbd\x95\xe5\xb7\xb2\xe8\xbf\x87\xe6\x9c\x9f\xef\xbc\x8c\xe8\xaf\xb7\xe9\x87\x8d\xe6\x96\xb0\xe7\x99\xbb\xe5\xbd\x95"));
        } else if (status == 0) {
            emit errorOccurred(QString::fromUtf8("\xe6\x97\xa0\xe6\xb3\x95\xe8\xbf\x9e\xe6\x8e\xa5\xe6\x9c\x8d\xe5\x8a\xa1\xe5\x99\xa8\xef\xbc\x8c\xe8\xaf\xb7\xe6\xa3\x80\xe6\x9f\xa5\xe7\xbd\x91\xe7\xbb\x9c\xe8\xbf\x9e\xe6\x8e\xa5"));
        } else {
            emit errorOccurred(QString::fromUtf8("\xe8\xaf\xb7\xe6\xb1\x82\xe5\xa4\xb1\xe8\xb4\xa5(HTTP %1): %2").arg(status).arg(m_reply->errorString()));
        }
    }
    if (!hadError && m_running) {
        if (!m_finalText.isEmpty()) {
            emit completed(m_finalText, m_modifiedExcelUrl);
        } else {
            emit errorOccurred(QString::fromUtf8("\xe6\x9c\xaa\xe6\x94\xb6\xe5\x88\xb0" "AI\xe5\x93\x8d\xe5\xba\x94\xef\xbc\x8c\xe5\x8f\xaf\xe8\x83\xbd\xe6\x98\xaf\xe6\x9c\x8d\xe5\x8a\xa1\xe7\xab\xaf\xe5\xbc\x82\xe5\xb8\xb8\xef\xbc\x8c\xe8\xaf\xb7\xe7\xa8\x8d\xe5\x90\x8e\xe9\x87\x8d\xe8\xaf\x95"));
        }
    }
    m_reply->deleteLater();
    m_reply = nullptr;
    m_running = false;
}

void SseClient::processBlock(const QString &block) {
    QStringList lines = block.split('\n');
    QString eventName, dataLine;
    for (const QString &line : lines) {
        if (line.startsWith("event:")) eventName = line.mid(6).trimmed();
        if (line.startsWith("data:")) dataLine += line.mid(5).trimmed();
    }
    if (dataLine.isEmpty()) return;

    QJsonDocument doc = QJsonDocument::fromJson(dataLine.toUtf8());
    if (!doc.isObject()) return;
    QJsonObject payload = doc.object();

    if (payload.contains("error") && !payload["error"].isNull()) {
        QString errMsg = payload["error"].toString();
        if (!errMsg.isEmpty()) {
            emit errorOccurred(errMsg);
            return;
        }
    }

    if (eventName == "complete" || payload["eventType"].toString() == "complete") {
        QJsonObject result = payload["result"].toObject();
        m_finalText = result["aiResponse"].toString();
        if (m_finalText.isEmpty())
            m_finalText = payload["aiResponseContent"].toString();
        m_modifiedExcelUrl = result["modifiedExcelUrl"].toString();
        if (m_finalText.isEmpty()) {
            QJsonArray arr = result["resultData"].toArray();
            if (!arr.isEmpty())
                m_finalText = QJsonDocument(arr).toJson(QJsonDocument::Indented);
        }
        if (m_finalText.isEmpty())
            m_finalText = "AI 已完成处理，但未返回可展示文本。";
        emit textReceived(m_finalText);
    }

    // Progress events with partial content
    if (m_finalText.isEmpty() && payload.contains("aiResponseContent")) {
        QString partial = payload["aiResponseContent"].toString();
        if (!partial.isEmpty()) {
            m_finalText = partial;
            emit textReceived(partial);
        }
    }
}
