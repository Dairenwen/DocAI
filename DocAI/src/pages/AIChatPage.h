#ifndef AICHATPAGE_H
#define AICHATPAGE_H

#include <QWidget>
#include <QTextEdit>
#include <QLineEdit>
#include <QPushButton>
#include <QListWidget>
#include <QSplitter>
#include <QLabel>
#include <QComboBox>
#include <QScrollArea>
#include <QVBoxLayout>
#include <QTextBrowser>
#include "../network/SseClient.h"
#include "../models/DataModels.h"

class AIChatPage : public QWidget {
    Q_OBJECT
public:
    explicit AIChatPage(QWidget *parent = nullptr);

public slots:
    void setLinkedDocument(int docId, const QString &docName);
    void linkDocumentAndOpenChat(int docId, const QString &docName);

private slots:
    void sendMessage();
    void sendCommand(const QString &cmd);
    void stopGeneration();
    void createNewConversation();
    void loadConversation(int convId);
    void deleteConversation(int convId);
    void selectDocument();
    void onSseText(const QString &text);
    void onSseComplete(const QString &text, const QString &excelUrl);
    void onSseError(const QString &err);
    void exportResult();
    void loadConversationList();
    void clearChat();
    void regenerateLastPrompt();
    void continueDialogue();
    void previewContent(const QString &content);
    void sendToEmail(const QString &content);

private:
    void setupUI();
    void appendMessage(const QString &role, const QString &content, const QString &excelUrl = QString());
    void createStreamingBubble();
    void saveMessageToConversation(const QString &role, const QString &content);
    void scrollToBottom();
    void showWelcomeScreen();
    QWidget *createMessageActionBar(const QString &content, const QString &excelUrl);
    void rebuildLinkedDocList();
    void rebuildConvoListUI();
    bool eventFilter(QObject *obj, QEvent *event) override;

    // Left sidebar
    QWidget *m_sidebar;
    QListWidget *m_linkedDocList;
    QPushButton *m_selectDocBtn;
    QWidget *m_commandGrid;
    QLineEdit *m_convoSearchEdit;
    QListWidget *m_convoList;
    QPushButton *m_newConvoBtn;

    // Chat area
    QScrollArea *m_chatScroll;
    QWidget *m_chatContainer;
    QVBoxLayout *m_chatMsgLayout;
    QWidget *m_welcomeWidget;
    QTextEdit *m_inputEdit;
    QPushButton *m_sendBtn;
    QPushButton *m_stopBtn;
    QLabel *m_statusLabel;
    QLabel *m_charCountLabel;

    // Top bar
    QLabel *m_chatTitleLabel;
    QComboBox *m_modelCombo;

    // Typing indicator
    QWidget *m_typingWidget;

    SseClient *m_sse;
    QList<int> m_linkedDocIds;
    QList<QPair<int,QString>> m_linkedDocs; // id, name
    int m_activeConversationId = 0;
    QList<Conversation> m_conversations;
    bool m_sidebarCollapsed = false;
    QString m_lastUserPrompt;

    // Streaming AI response
    QTextBrowser *m_streamingBrowser = nullptr;
    QWidget *m_streamingRow = nullptr;
};

#endif // AICHATPAGE_H
