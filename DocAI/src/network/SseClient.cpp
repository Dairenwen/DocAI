#include "SseClient.h"
#include "../utils/TokenManager.h"
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>

SseClient::SseClient(QObject *parent) : QObject(parent),
    m_nam(new QNetworkAccessManager(this)) {}

void SseClient::start(const QString &message, int documentId) {
    if (m_running) abort();
    m_running = true;
    m_buffer.clear();
    m_finalText.clear();
    m_modifiedExcelUrl.clear();

    QString baseUrl = "http://docai.sa1.tunnelfrp.com/api/v1/ai/chat/stream";
    QUrl requestUrl(baseUrl);
    QNetworkRequest req(requestUrl);
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
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
    if (m_reply->error() != QNetworkReply::NoError && m_reply->error() != QNetworkReply::OperationCanceledError) {
        if (status == 401) {
            emit errorOccurred("登录已过期，请重新登录");
        } else {
            emit errorOccurred(m_reply->errorString());
        }
    }
    if (m_running && !m_finalText.isEmpty()) {
        emit completed(m_finalText, m_modifiedExcelUrl);
    } else if (m_running && m_finalText.isEmpty()) {
        emit completed("请求已完成，但未收到完整的AI响应。", "");
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

    if (payload.contains("error")) {
        emit errorOccurred(payload["error"].toString());
        return;
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
