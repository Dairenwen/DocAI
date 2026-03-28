#include "ApiClient.h"
#include "../utils/TokenManager.h"
#include <QFile>
#include <QFileInfo>
#include <QMimeDatabase>
#include <QUrlQuery>

#include <QDebug>

ApiClient& ApiClient::instance() {
    static ApiClient inst;
    return inst;
}

ApiClient::ApiClient(QObject *parent)
    : QObject(parent)
    , m_nam(new QNetworkAccessManager(this))
    , m_baseUrl("http://docai.sa1.tunnelfrp.com/api/v1")
{
}

QNetworkRequest ApiClient::makeRequest(const QString &path, bool isJson) {
    QUrl requestUrl(m_baseUrl + path);
    QNetworkRequest req(requestUrl);
    if (isJson) {
        req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    }
    QString token = TokenManager::instance().token();
    if (!token.isEmpty()) {
        req.setRawHeader("Authorization", ("Bearer " + token).toUtf8());
    }
    return req;
}

void ApiClient::handleReply(QNetworkReply *reply, Callback cb) {
    connect(reply, &QNetworkReply::finished, this, [this, reply, cb]() {
        reply->deleteLater();
        qDebug() << "[API]" << reply->request().url().toString()
                 << "status:" << reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt()
                 << "error:" << reply->error();
        if (reply->error() != QNetworkReply::NoError) {
            int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
            if (status == 401) {
                emit tokenExpired();
                cb(false, {}, "登录已过期，请重新登录");
                return;
            }
            QByteArray body = reply->readAll();
            QJsonObject obj = QJsonDocument::fromJson(body).object();
            QString msg = obj["message"].toString();
            if (msg.isEmpty()) msg = reply->errorString();
            if (status == 502) msg = QString("后端服务未响应(502)，请检查后端网关是否已启动 [%1]").arg(reply->request().url().toString());
            cb(false, {}, msg);
            return;
        }
        QByteArray body = reply->readAll();
        QJsonDocument doc = QJsonDocument::fromJson(body);
        QJsonObject obj = doc.object();
        int code = obj["code"].toInt(200);
        if (code != 200) {
            QString msg = obj["message"].toString("请求失败");
            if (code == 401) {
                emit tokenExpired();
            }
            cb(false, obj, msg);
            return;
        }
        cb(true, obj, "");
    });
}

void ApiClient::handleBlobReply(QNetworkReply *reply, BlobCallback cb) {
    connect(reply, &QNetworkReply::finished, this, [this, reply, cb]() {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            cb(false, {}, "", reply->errorString());
            return;
        }
        QByteArray data = reply->readAll();
        QString disposition = reply->rawHeader("Content-Disposition");
        QString filename;
        if (disposition.contains("filename=")) {
            int idx = disposition.indexOf("filename=");
            filename = disposition.mid(idx + 9).trimmed();
            filename.remove('"');
            // Decode URL-encoded filename
            filename = QUrl::fromPercentEncoding(filename.toUtf8());
        }
        cb(true, data, filename, "");
    });
}

void ApiClient::doGet(const QString &path, Callback cb) {
    QNetworkReply *reply = m_nam->get(makeRequest(path));
    handleReply(reply, cb);
}

void ApiClient::doPost(const QString &path, const QJsonObject &body, Callback cb) {
    QByteArray data = QJsonDocument(body).toJson(QJsonDocument::Compact);
    QNetworkReply *reply = m_nam->post(makeRequest(path), data);
    handleReply(reply, cb);
}

void ApiClient::doPut(const QString &path, const QJsonObject &body, Callback cb) {
    QByteArray data = QJsonDocument(body).toJson(QJsonDocument::Compact);
    QNetworkReply *reply = m_nam->put(makeRequest(path), data);
    handleReply(reply, cb);
}

void ApiClient::doDelete(const QString &path, Callback cb) {
    QNetworkReply *reply = m_nam->deleteResource(makeRequest(path));
    handleReply(reply, cb);
}

void ApiClient::doDeleteWithBody(const QString &path, const QJsonObject &body, Callback cb) {
    QNetworkRequest req = makeRequest(path);
    QByteArray data = QJsonDocument(body).toJson(QJsonDocument::Compact);
    QNetworkReply *reply = m_nam->sendCustomRequest(req, "DELETE", data);
    handleReply(reply, cb);
}

void ApiClient::doDownload(const QString &path, BlobCallback cb) {
    QNetworkRequest req = makeRequest(path, false);
    QNetworkReply *reply = m_nam->get(req);
    handleBlobReply(reply, cb);
}

void ApiClient::doUpload(const QString &path, const QString &filePath, const QString &fieldName,
                          std::function<void(int)> progressCb, Callback cb) {
    QFile *file = new QFile(filePath);
    if (!file->open(QIODevice::ReadOnly)) {
        delete file;
        cb(false, {}, "无法打开文件: " + filePath);
        return;
    }

    QHttpMultiPart *multiPart = new QHttpMultiPart(QHttpMultiPart::FormDataType);
    QHttpPart filePart;
    QFileInfo fi(filePath);
    QMimeDatabase mimeDb;
    QString mimeType = mimeDb.mimeTypeForFile(fi).name();
    filePart.setHeader(QNetworkRequest::ContentTypeHeader, mimeType);
    filePart.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QString("form-data; name=\"%1\"; filename=\"%2\"")
                       .arg(fieldName, fi.fileName()));
    filePart.setBodyDevice(file);
    file->setParent(multiPart);
    multiPart->append(filePart);

    QNetworkRequest req = makeRequest(path, false);
    QNetworkReply *reply = m_nam->post(req, multiPart);
    multiPart->setParent(reply);

    if (progressCb) {
        connect(reply, &QNetworkReply::uploadProgress, this, [progressCb](qint64 sent, qint64 total) {
            if (total > 0) progressCb(static_cast<int>(sent * 100 / total));
        });
    }
    handleReply(reply, cb);
}

// ============== Auth ==============
void ApiClient::login(const QString &username, const QString &password, Callback cb) {
    QJsonObject body;
    body["username"] = username;
    body["password"] = password;
    body["action"] = "login";
    doPost("/users/auth", body, cb);
}

void ApiClient::registerUser(const QString &username, const QString &password, const QString &nickname, Callback cb) {
    QJsonObject body;
    body["username"] = username;
    body["password"] = password;
    body["nickname"] = nickname;
    body["action"] = "register";
    doPost("/users/auth", body, cb);
}

void ApiClient::emailAuth(const QString &email, const QString &code, Callback cb) {
    QJsonObject body;
    body["email"] = email;
    body["verificationCode"] = code;
    body["action"] = "email_auth";
    doPost("/users/auth", body, cb);
}

void ApiClient::sendVerificationCode(const QString &email, Callback cb) {
    QJsonObject body;
    body["email"] = email;
    doPost("/users/verification-code", body, cb);
}

void ApiClient::resetPassword(const QString &email, const QString &code, const QString &newPassword, Callback cb) {
    QJsonObject body;
    body["email"] = email;
    body["verificationCode"] = code;
    body["newPassword"] = newPassword;
    doPost("/users/password/reset-by-email", body, cb);
}

void ApiClient::changePassword(const QString &currentPwd, const QString &newPwd, Callback cb) {
    QJsonObject body;
    body["currentPassword"] = currentPwd;
    body["newPassword"] = newPwd;
    doPost("/users/change-password", body, cb);
}

void ApiClient::getCurrentUser(Callback cb) { doGet("/users/info", cb); }
void ApiClient::logout(Callback cb) { doPost("/users/logout", {}, cb); }

// ============== Source Documents ==============
void ApiClient::uploadSourceDocument(const QString &filePath, std::function<void(int)> progressCb, Callback cb) {
    doUpload("/source/upload", filePath, "file", progressCb, cb);
}

void ApiClient::getSourceDocuments(Callback cb) { doGet("/source/documents", cb); }
void ApiClient::getDocumentStatuses(Callback cb) { doGet("/source/documents/status", cb); }
void ApiClient::getDocument(int id, Callback cb) { doGet(QString("/source/%1").arg(id), cb); }
void ApiClient::getDocumentFields(int id, Callback cb) { doGet(QString("/source/%1/fields").arg(id), cb); }

void ApiClient::downloadSourceDocument(int docId, BlobCallback cb) {
    doDownload(QString("/source/%1/download").arg(docId), cb);
}

void ApiClient::deleteDocument(int docId, Callback cb) {
    doDelete(QString("/source/%1").arg(docId), cb);
}

void ApiClient::batchDeleteDocuments(const QList<int> &docIds, Callback cb) {
    QJsonArray arr;
    for (int id : docIds) arr.append(id);
    QJsonObject body;
    body["docIds"] = arr;
    doPost("/source/batch-delete", body, cb);
}

// ============== Template ==============
void ApiClient::uploadTemplateFile(const QString &filePath, std::function<void(int)> progressCb, Callback cb) {
    doUpload("/template/upload", filePath, "file", progressCb, cb);
}

void ApiClient::parseTemplateSlots(int templateId, Callback cb) {
    doPost(QString("/template/%1/parse").arg(templateId), {}, cb);
}

void ApiClient::fillTemplate(int templateId, const QList<int> &docIds, const QString &userRequirement, Callback cb) {
    QJsonArray arr;
    for (int id : docIds) arr.append(id);
    QJsonObject body;
    body["docIds"] = arr;
    body["userRequirement"] = userRequirement;
    doPost(QString("/template/%1/fill").arg(templateId), body, cb);
}

void ApiClient::listTemplateFiles(Callback cb) { doGet("/template/list", cb); }
void ApiClient::getTemplateAudit(int templateId, Callback cb) { doGet(QString("/template/%1/audit").arg(templateId), cb); }
void ApiClient::getTemplateDecisions(int templateId, Callback cb) { doGet(QString("/template/%1/decisions").arg(templateId), cb); }

void ApiClient::downloadTemplateResult(int templateId, BlobCallback cb) {
    doDownload(QString("/template/%1/download").arg(templateId), cb);
}

void ApiClient::sendTemplateResultEmail(int templateId, const QString &email, Callback cb) {
    QJsonObject body;
    body["email"] = email;
    doPost(QString("/template/%1/send-email").arg(templateId), body, cb);
}

// ============== AI ==============
void ApiClient::sendAiResultEmail(const QJsonObject &data, Callback cb) { doPost("/ai/send-email", data, cb); }
void ApiClient::sendContentEmail(const QJsonObject &data, Callback cb) { doPost("/ai/send-content-email", data, cb); }

// ============== LLM ==============
void ApiClient::getLlmProviders(Callback cb) { doGet("/llm/providers/list", cb); }
void ApiClient::getCurrentLlmProvider(Callback cb) { doGet("/llm/providers/current", cb); }
void ApiClient::switchLlmProvider(const QString &providerName, Callback cb) {
    QJsonObject body;
    body["providerName"] = providerName;
    doPost("/llm/providers/switch", body, cb);
}

// ============== Conversations ==============
void ApiClient::listConversations(Callback cb) { doGet("/ai/conversations", cb); }
void ApiClient::createConversation(const QJsonObject &data, Callback cb) { doPost("/ai/conversations", data, cb); }
void ApiClient::updateConversation(int id, const QJsonObject &data, Callback cb) {
    doPut(QString("/ai/conversations/%1").arg(id), data, cb);
}
void ApiClient::deleteConversation(int id, Callback cb) { doDelete(QString("/ai/conversations/%1").arg(id), cb); }
void ApiClient::getConversationMessages(int id, Callback cb) { doGet(QString("/ai/conversations/%1/messages").arg(id), cb); }
void ApiClient::addConversationMessage(int id, const QJsonObject &data, Callback cb) {
    doPost(QString("/ai/conversations/%1/messages").arg(id), data, cb);
}
