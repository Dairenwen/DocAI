#ifndef SSECLIENT_H
#define SSECLIENT_H

#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkReply>

class SseClient : public QObject {
    Q_OBJECT
public:
    explicit SseClient(QObject *parent = nullptr);
    void start(const QString &message, int documentId = 0);
    void abort();
    bool isRunning() const { return m_running; }

signals:
    void textReceived(const QString &fullText);
    void completed(const QString &finalText, const QString &modifiedExcelUrl);
    void errorOccurred(const QString &error);

private slots:
    void onReadyRead();
    void onFinished();

private:
    void processBlock(const QString &block);
    QNetworkAccessManager *m_nam;
    QNetworkReply *m_reply = nullptr;
    QString m_buffer;
    QString m_finalText;
    QString m_modifiedExcelUrl;
    bool m_running = false;
};

#endif // SSECLIENT_H
