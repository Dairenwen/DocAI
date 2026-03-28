#ifndef APICLIENT_H
#define APICLIENT_H

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QJsonObject>
#include <QJsonArray>
#include <QJsonDocument>
#include <QHttpMultiPart>
#include <functional>

class ApiClient : public QObject {
    Q_OBJECT
public:
    static ApiClient& instance();

    using Callback = std::function<void(bool ok, const QJsonObject &data, const QString &error)>;
    using BlobCallback = std::function<void(bool ok, const QByteArray &data, const QString &filename, const QString &error)>;

    // Auth
    void login(const QString &username, const QString &password, Callback cb);
    void registerUser(const QString &username, const QString &password, const QString &nickname, Callback cb);
    void emailAuth(const QString &email, const QString &code, Callback cb);
    void sendVerificationCode(const QString &email, Callback cb);
    void resetPassword(const QString &email, const QString &code, const QString &newPassword, Callback cb);
    void changePassword(const QString &currentPwd, const QString &newPwd, Callback cb);
    void getCurrentUser(Callback cb);
    void logout(Callback cb);

    // Source Documents
    void uploadSourceDocument(const QString &filePath, std::function<void(int)> progressCb, Callback cb);
    void getSourceDocuments(Callback cb);
    void getDocumentStatuses(Callback cb);
    void getDocument(int id, Callback cb);
    void getDocumentFields(int id, Callback cb);
    void downloadSourceDocument(int docId, BlobCallback cb);
    void deleteDocument(int docId, Callback cb);
    void batchDeleteDocuments(const QList<int> &docIds, Callback cb);

    // Template
    void uploadTemplateFile(const QString &filePath, std::function<void(int)> progressCb, Callback cb);
    void parseTemplateSlots(int templateId, Callback cb);
    void fillTemplate(int templateId, const QList<int> &docIds, const QString &userRequirement, Callback cb);
    void listTemplateFiles(Callback cb);
    void getTemplateAudit(int templateId, Callback cb);
    void getTemplateDecisions(int templateId, Callback cb);
    void downloadTemplateResult(int templateId, BlobCallback cb);
    void sendTemplateResultEmail(int templateId, const QString &email, Callback cb);

    // AI
    void sendAiResultEmail(const QJsonObject &data, Callback cb);
    void sendContentEmail(const QJsonObject &data, Callback cb);

    // LLM
    void getLlmProviders(Callback cb);
    void getCurrentLlmProvider(Callback cb);
    void switchLlmProvider(const QString &providerName, Callback cb);

    // Conversations
    void listConversations(Callback cb);
    void createConversation(const QJsonObject &data, Callback cb);
    void updateConversation(int id, const QJsonObject &data, Callback cb);
    void deleteConversation(int id, Callback cb);
    void getConversationMessages(int id, Callback cb);
    void addConversationMessage(int id, const QJsonObject &data, Callback cb);

    QString baseUrl() const { return m_baseUrl; }

signals:
    void tokenExpired();

private:
    explicit ApiClient(QObject *parent = nullptr);
    void doGet(const QString &path, Callback cb);
    void doPost(const QString &path, const QJsonObject &body, Callback cb);
    void doPut(const QString &path, const QJsonObject &body, Callback cb);
    void doDelete(const QString &path, Callback cb);
    void doDeleteWithBody(const QString &path, const QJsonObject &body, Callback cb);
    void doDownload(const QString &path, BlobCallback cb);
    void doUpload(const QString &path, const QString &filePath, const QString &fieldName,
                  std::function<void(int)> progressCb, Callback cb);
    QNetworkRequest makeRequest(const QString &path, bool isJson = true);
    void handleReply(QNetworkReply *reply, Callback cb);
    void handleBlobReply(QNetworkReply *reply, BlobCallback cb);

    QNetworkAccessManager *m_nam;
    QString m_baseUrl;
};

#endif // APICLIENT_H
