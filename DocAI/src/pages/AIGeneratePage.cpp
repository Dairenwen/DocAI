#include "AIGeneratePage.h"
#include "../network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QScrollArea>
#include <QGraphicsDropShadowEffect>
#include <QFileDialog>
#include <QStandardPaths>
#include <QMessageBox>
#include <QApplication>
#include <QClipboard>
#include <QDialog>
#include "../utils/IconHelper.h"

AIGeneratePage::AIGeneratePage(QWidget *parent) : QWidget(parent), m_sse(new SseClient(this)) {
    m_selectedType = "通知";
    setupUI();

    connect(m_sse, &SseClient::textReceived, [this](const QString &text) {
        m_generatedContent = text;
        m_resultDisplay->setPlainText(text);
    });
    connect(m_sse, &SseClient::completed, [this](const QString &text, const QString &) {
        m_generatedContent = text;
        m_resultDisplay->setPlainText(text);
        m_generating = false;
        m_generateBtn->setEnabled(true);
        m_generateBtn->setText("立即生成");
        m_emptyState->setVisible(false);
        m_loadingState->setVisible(false);
        m_resultDisplay->setVisible(true);
        m_resultToolbar->setVisible(true);
        m_wordCountLabel->setText(QString("约 %1 字").arg(text.length()));
    });
    connect(m_sse, &SseClient::errorOccurred, [this](const QString &err) {
        m_generating = false;
        m_generateBtn->setEnabled(true);
        m_generateBtn->setText("立即生成");
        m_loadingState->setVisible(false);
        QMessageBox::warning(this, "生成失败", err);
    });
}

void AIGeneratePage::setupUI() {
    QHBoxLayout *mainLayout = new QHBoxLayout(this);
    mainLayout->setContentsMargins(32, 24, 32, 24);
    mainLayout->setSpacing(24);

    // ===== Left Config Panel =====
    QWidget *configPanel = new QWidget;
    configPanel->setObjectName("configPanel");
    configPanel->setFixedWidth(380);
    configPanel->setStyleSheet("#configPanel { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh1 = new QGraphicsDropShadowEffect;
    sh1->setBlurRadius(20); sh1->setColor(QColor(0,0,0,12)); sh1->setOffset(0, 4);
    configPanel->setGraphicsEffect(sh1);

    QVBoxLayout *cfgLayout = new QVBoxLayout(configPanel);
    cfgLayout->setContentsMargins(28, 28, 28, 28);
    cfgLayout->setSpacing(16);

    // Header
    QHBoxLayout *headerRow = new QHBoxLayout;
    QLabel *headerIcon = new QLabel;
    headerIcon->setFixedSize(28, 28);
    headerIcon->setPixmap(IconHelper::pencil(24, QColor("#4F46E5")));
    QVBoxLayout *headerText = new QVBoxLayout;
    QLabel *headerTitle = new QLabel("智能写作");
    headerTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QLabel *headerDesc = new QLabel("AI 驱动的公文生成与润色");
    headerDesc->setStyleSheet("font-size: 12px; color: #9CA3AF;");
    headerText->addWidget(headerTitle);
    headerText->addWidget(headerDesc);
    headerRow->addWidget(headerIcon);
    headerRow->addLayout(headerText);
    headerRow->addStretch();

    // Document types
    QLabel *typeLabel = new QLabel("公文类型");
    typeLabel->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    QGridLayout *typeGrid = new QGridLayout;
    typeGrid->setSpacing(8);
    QStringList types = {"\xe9\x80\x9a\xe7\x9f\xa5", "\xe6\x8a\xa5\xe5\x91\x8a", "\xe8\xaf\xb7\xe7\xa4\xba", "\xe5\x87\xbd\xe4\xbb\xb6", "\xe6\x84\x8f\xe8\xa7\x81", "\xe7\xba\xaa\xe8\xa6\x81", "\xe9\x80\x9a\xe6\x8a\xa5", "\xe5\x85\xac\xe5\x91\x8a"};
    for (int i = 0; i < types.size(); i++) {
        QPushButton *btn = new QPushButton(types[i]);
        btn->setMinimumHeight(40);
        btn->setCursor(Qt::PointingHandCursor);
        btn->setCheckable(true);
        btn->setChecked(types[i] == m_selectedType);
        btn->setProperty("typeName", types[i]);
        btn->setStyleSheet(
            "QPushButton { background: #F9FAFB; border: 1.5px solid #D1D5DB; border-radius: 6px; font-size: 13px; color: #6B7280; }"
            "QPushButton:hover { background: #EEF2FF; border-color: #C7D2FE; }"
            "QPushButton:checked { background: #EEF2FF; border-color: #818CF8; color: #4F46E5; font-weight: 600; }"
        );
        connect(btn, &QPushButton::clicked, [this, btn, types, i]() {
            m_selectedType = types[i];
            for (auto *b : m_typeBtns) b->setChecked(false);
            btn->setChecked(true);
        });
        m_typeBtns.append(btn);
        typeGrid->addWidget(btn, i / 4, i % 4);
    }

    // Title input
    QLabel *titleLabel = new QLabel("标题");
    titleLabel->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    m_titleEdit = new QLineEdit;
    m_titleEdit->setPlaceholderText("例：关于开展XX工作的通知");
    m_titleEdit->setMinimumHeight(40);
    m_titleEdit->setStyleSheet(
        "QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 0 14px; font-size: 13px; }"
        "QLineEdit:focus { border-color: #818CF8; }");

    // Requirement
    QLabel *reqLabel = new QLabel("需求描述");
    reqLabel->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    m_requirementEdit = new QTextEdit;
    m_requirementEdit->setPlaceholderText("详细描述公文的主题、背景、目的和核心内容要点...");
    m_requirementEdit->setMinimumHeight(150);
    m_requirementEdit->setStyleSheet(
        "QTextEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 10px 14px; font-size: 13px; }"
        "QTextEdit:focus { border-color: #818CF8; }");

    // Generate button
    m_generateBtn = new QPushButton("\xe7\xab\x8b\xe5\x8d\xb3\xe7\x94\x9f\xe6\x88\x90");
    m_generateBtn->setMinimumHeight(44);
    m_generateBtn->setCursor(Qt::PointingHandCursor);
    m_generateBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #818CF8, stop:1 #4F46E5);"
        "  color: white; border: none; border-radius: 6px; font-size: 15px; font-weight: 600; }"
        "QPushButton:hover { background: #4F46E5; }"
        "QPushButton:disabled { background: #C7D2FE; }");
    connect(m_generateBtn, &QPushButton::clicked, this, &AIGeneratePage::generate);

    cfgLayout->addLayout(headerRow);
    cfgLayout->addWidget(typeLabel);
    cfgLayout->addLayout(typeGrid);
    cfgLayout->addWidget(titleLabel);
    cfgLayout->addWidget(m_titleEdit);
    cfgLayout->addWidget(reqLabel);
    cfgLayout->addWidget(m_requirementEdit, 1);
    cfgLayout->addWidget(m_generateBtn);

    // ===== Right Preview Panel =====
    QWidget *previewPanel = new QWidget;
    QVBoxLayout *prevLayout = new QVBoxLayout(previewPanel);
    prevLayout->setContentsMargins(0, 0, 0, 0);
    prevLayout->setSpacing(0);

    // Empty state
    m_emptyState = new QWidget;
    QVBoxLayout *emptyLayout = new QVBoxLayout(m_emptyState);
    emptyLayout->setAlignment(Qt::AlignCenter);
    QLabel *emptyIcon = new QLabel;
    emptyIcon->setFixedSize(48, 48);
    emptyIcon->setPixmap(IconHelper::pencil(40, QColor("#9CA3AF")));
    emptyIcon->setAlignment(Qt::AlignCenter);
    QLabel *emptyTitle = new QLabel("AI 智能写作");
    emptyTitle->setStyleSheet("font-size: 18px; font-weight: 600; color: #9CA3AF;");
    emptyTitle->setAlignment(Qt::AlignCenter);
    QLabel *emptyDesc = new QLabel("在左侧填写需求，一键生成规范公文");
    emptyDesc->setStyleSheet("font-size: 13px; color: #D1D5DB;");
    emptyDesc->setAlignment(Qt::AlignCenter);
    emptyLayout->addWidget(emptyIcon);
    emptyLayout->addWidget(emptyTitle);
    emptyLayout->addWidget(emptyDesc);

    // Loading state
    m_loadingState = new QWidget;
    m_loadingState->setVisible(false);
    QVBoxLayout *loadLayout = new QVBoxLayout(m_loadingState);
    loadLayout->setAlignment(Qt::AlignCenter);
    QLabel *loadLabel = new QLabel("AI \xe6\xad\xa3\xe5\x9c\xa8\xe6\x9e\x84\xe6\x80\x9d\xe5\x86\x85\xe5\xae\xb9...");
    loadLabel->setStyleSheet("font-size: 16px; color: #818CF8;");
    loadLabel->setAlignment(Qt::AlignCenter);
    loadLayout->addWidget(loadLabel);

    // Result toolbar
    m_resultToolbar = new QWidget;
    m_resultToolbar->setVisible(false);
    m_resultToolbar->setStyleSheet("background: white; border-bottom: 1px solid #F3F4F6;");
    QHBoxLayout *toolbarLayout = new QHBoxLayout(m_resultToolbar);
    toolbarLayout->setContentsMargins(16, 8, 16, 8);
    QLabel *doneTag = new QLabel("\xe5\xb7\xb2\xe7\x94\x9f\xe6\x88\x90");
    doneTag->setStyleSheet("font-size: 12px; color: #10B981; background: #D1FAE5; padding: 3px 10px; border-radius: 16px;");
    m_wordCountLabel = new QLabel;
    m_wordCountLabel->setStyleSheet("font-size: 12px; color: #9CA3AF;");
    QPushButton *copyBtn = new QPushButton("\xe5\xa4\x8d\xe5\x88\xb6");
    copyBtn->setMinimumHeight(30);
    copyBtn->setStyleSheet("QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 6px; padding: 0 12px; font-size: 12px; }"
                           "QPushButton:hover { background: #BAE7FF; }");
    QPushButton *exportToolBtn = new QPushButton("\xe5\xaf\xbc\xe5\x87\xba");
    exportToolBtn->setMinimumHeight(30);
    exportToolBtn->setStyleSheet("QPushButton { background: #D1FAE5; color: #10B981; border: none; border-radius: 6px; padding: 0 12px; font-size: 12px; }"
                                 "QPushButton:hover { background: #D9F7BE; }");
    QPushButton *emailBtn = new QPushButton("\xe5\x8f\x91\xe9\x80\x81\xe9\x82\xae\xe4\xbb\xb6");
    emailBtn->setMinimumHeight(30);
    emailBtn->setStyleSheet("QPushButton { background: #FEF3C7; color: #F59E0B; border: none; border-radius: 6px; padding: 0 12px; font-size: 12px; }"
                            "QPushButton:hover { background: #FFFB8F; }");
    connect(copyBtn, &QPushButton::clicked, this, &AIGeneratePage::copyResult);
    connect(exportToolBtn, &QPushButton::clicked, this, &AIGeneratePage::exportResult);
    connect(emailBtn, &QPushButton::clicked, this, &AIGeneratePage::sendEmail);

    toolbarLayout->addWidget(doneTag);
    toolbarLayout->addWidget(m_wordCountLabel);
    toolbarLayout->addStretch();
    toolbarLayout->addWidget(copyBtn);
    toolbarLayout->addWidget(exportToolBtn);
    toolbarLayout->addWidget(emailBtn);

    // Result text
    m_resultDisplay = new QTextEdit;
    m_resultDisplay->setReadOnly(true);
    m_resultDisplay->setVisible(false);
    m_resultDisplay->setStyleSheet(
        "QTextEdit { border: none; background: white; padding: 24px; font-size: 14px; line-height: 1.8; }"
        "QScrollBar:vertical { width: 6px; }"
        "QScrollBar::handle:vertical { background: #D1D5DB; border-radius: 3px; }");

    prevLayout->addWidget(m_emptyState, 1);
    prevLayout->addWidget(m_loadingState, 1);
    prevLayout->addWidget(m_resultToolbar);
    prevLayout->addWidget(m_resultDisplay, 1);

    QWidget *previewCard = new QWidget;
    previewCard->setObjectName("previewCard");
    previewCard->setStyleSheet("#previewCard { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh2 = new QGraphicsDropShadowEffect;
    sh2->setBlurRadius(20); sh2->setColor(QColor(0,0,0,12)); sh2->setOffset(0, 4);
    previewCard->setGraphicsEffect(sh2);
    QVBoxLayout *previewCardLayout = new QVBoxLayout(previewCard);
    previewCardLayout->setContentsMargins(0, 0, 0, 0);
    previewCardLayout->addWidget(previewPanel);

    mainLayout->addWidget(configPanel);
    mainLayout->addWidget(previewCard, 1);
}

void AIGeneratePage::generate() {
    QString title = m_titleEdit->text().trimmed();
    QString req = m_requirementEdit->toPlainText().trimmed();
    if (req.isEmpty()) {
        QMessageBox::warning(this, "提示", "请填写需求描述");
        return;
    }

    m_generating = true;
    m_generatedContent.clear();
    m_generateBtn->setEnabled(false);
    m_generateBtn->setText("生成中...");
    m_emptyState->setVisible(false);
    m_loadingState->setVisible(true);
    m_resultDisplay->setVisible(false);
    m_resultToolbar->setVisible(false);

    QString prompt = QString("请撰写一篇%1类型的公文。").arg(m_selectedType);
    if (!title.isEmpty()) prompt += QString("标题：%1。").arg(title);
    prompt += QString("具体需求：%1").arg(req);

    m_sse->start(prompt, 0);
}

void AIGeneratePage::copyResult() {
    QApplication::clipboard()->setText(m_generatedContent);
    QMessageBox::information(this, "成功", "内容已复制到剪贴板");
}

void AIGeneratePage::exportResult() {
    QString path = QFileDialog::getSaveFileName(this, "导出文档",
        QStandardPaths::writableLocation(QStandardPaths::DesktopLocation) + "/ai_document.txt",
        "文本文件 (*.txt);;Markdown (*.md)");
    if (path.isEmpty()) return;
    QFile f(path);
    if (f.open(QIODevice::WriteOnly | QIODevice::Text)) {
        f.write(m_generatedContent.toUtf8());
        f.close();
        QMessageBox::information(this, "成功", "已导出到: " + path);
    }
}

void AIGeneratePage::sendEmail() {
    QDialog dlg(this);
    dlg.setWindowTitle("发送邮件");
    dlg.setFixedSize(400, 180);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(24, 20, 24, 20);

    QLabel *lbl = new QLabel("收件人邮箱:");
    lbl->setStyleSheet("font-size: 13px; color: #6B7280;");
    QLineEdit *emailEdit = new QLineEdit;
    emailEdit->setPlaceholderText("请输入收件人邮箱");
    emailEdit->setMinimumHeight(38);
    emailEdit->setStyleSheet("QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 0 12px; font-size: 13px; }");

    QPushButton *sendBtn = new QPushButton("\xe5\x8f\x91\xe9\x80\x81");
    sendBtn->setMinimumHeight(40);
    sendBtn->setStyleSheet(
        "QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; font-size: 14px; }"
        "QPushButton:hover { background: #3730A3; }");

    layout->addWidget(lbl);
    layout->addWidget(emailEdit);
    layout->addSpacing(8);
    layout->addWidget(sendBtn);

    connect(sendBtn, &QPushButton::clicked, [this, &dlg, emailEdit]() {
        QString email = emailEdit->text().trimmed();
        if (email.isEmpty()) return;
        QJsonObject body;
        body["email"] = email;
        body["content"] = m_generatedContent;
        body["subject"] = m_titleEdit->text().isEmpty() ? "AI智能写作结果" : m_titleEdit->text();
        ApiClient::instance().sendContentEmail(body, [this, &dlg](bool ok, const QJsonObject &, const QString &err) {
            if (ok) {
                QMessageBox::information(this, "成功", "邮件发送成功");
                dlg.accept();
            } else {
                QMessageBox::warning(this, "失败", err);
            }
        });
    });

    dlg.exec();
}
