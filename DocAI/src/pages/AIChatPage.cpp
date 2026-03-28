#include "AIChatPage.h"
#include "../network/ApiClient.h"
#include "../utils/TokenManager.h"
#include "../utils/IconHelper.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QSplitter>
#include <QScrollArea>
#include <QScrollBar>
#include <QJsonArray>
#include <QGraphicsDropShadowEffect>
#include <QDialog>
#include <QTableWidget>
#include <QHeaderView>
#include <QFileDialog>
#include <QStandardPaths>
#include <QMessageBox>
#include <QInputDialog>
#include <QTextBrowser>
#include <QTextDocument>
#include <QCheckBox>
#include <QDateTime>
#include <QTimer>
#include <QApplication>
#include <QDir>
#include <QEvent>
#include <QKeyEvent>
#include <QClipboard>
#include <QRegularExpression>
#include <QPainter>
#include <QFrame>

AIChatPage::AIChatPage(QWidget *parent) : QWidget(parent), m_sse(new SseClient(this)) {
    setupUI();
    loadConversationList();

    connect(m_sse, &SseClient::textReceived, this, &AIChatPage::onSseText);
    connect(m_sse, &SseClient::completed, this, &AIChatPage::onSseComplete);
    connect(m_sse, &SseClient::errorOccurred, this, &AIChatPage::onSseError);
}

void AIChatPage::setupUI() {
    QHBoxLayout *mainLayout = new QHBoxLayout(this);
    mainLayout->setContentsMargins(0, 0, 0, 0);
    mainLayout->setSpacing(0);

    // ===== Left Sidebar =====
    m_sidebar = new QWidget;
    m_sidebar->setFixedWidth(280);
    m_sidebar->setObjectName("chatSidebar");
    m_sidebar->setStyleSheet(
        "#chatSidebar { background: white; border-right: 1px solid #F3F4F6; }"
    );
    QVBoxLayout *sideLayout = new QVBoxLayout(m_sidebar);
    sideLayout->setContentsMargins(16, 16, 16, 16);
    sideLayout->setSpacing(12);

    // Document section
    QLabel *docSectionTitle = new QLabel("\xe5\x85\xb3\xe8\x81\x94\xe6\x96\x87\xe6\xa1\xa3");
    docSectionTitle->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");

    m_linkedDocList = new QListWidget;
    m_linkedDocList->setStyleSheet(
        "QListWidget { border: 1px solid #E5E7EB; border-radius: 8px; font-size: 12px; background: #F9FAFB; }"
        "QListWidget::item { padding: 0; border-bottom: 1px solid #F3F4F6; }"
        "QListWidget::item:hover { background: #EEF2FF; }");
    m_linkedDocList->setVisible(false);
    m_linkedDocList->setSizePolicy(QSizePolicy::Preferred, QSizePolicy::Minimum);

    m_selectDocBtn = new QPushButton("\xe9\x80\x89\xe6\x8b\xa9\xe6\x96\x87\xe6\xa1\xa3");
    m_selectDocBtn->setMinimumHeight(32);
    m_selectDocBtn->setCursor(Qt::PointingHandCursor);
    m_selectDocBtn->setStyleSheet(
        "QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 6px; font-size: 12px; padding: 0 12px; }"
        "QPushButton:hover { background: #BAE7FF; }");

    // Quick commands (3x3 grid)
    QLabel *cmdTitle = new QLabel("\xe6\x96\x87\xe6\xa1\xa3\xe6\x93\x8d\xe4\xbd\x9c");
    cmdTitle->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    m_commandGrid = new QWidget;
    QGridLayout *gridLayout = new QGridLayout(m_commandGrid);
    gridLayout->setContentsMargins(0, 0, 0, 0);
    gridLayout->setSpacing(6);

    struct Cmd { QString label; QString prompt; QPixmap icon; };
    QList<Cmd> cmds = {
        {"\xe6\x91\x98\xe8\xa6\x81", "\xe6\x80\xbb\xe7\xbb\x93\xe8\xbf\x99\xe7\xaf\x87\xe6\x96\x87\xe6\xa1\xa3\xe7\x9a\x84\xe6\xa0\xb8\xe5\xbf\x83\xe5\x86\x85\xe5\xae\xb9\xef\xbc\x8c\xe6\x8f\x90\xe7\x82\xbc\xe5\x85\xb3\xe9\x94\xae\xe4\xbf\xa1\xe6\x81\xaf", IconHelper::document(14, QColor("#4F46E5"))},
        {"\xe6\x8f\x90\xe5\x8f\x96", "\xe6\x8f\x90\xe5\x8f\x96\xe6\x96\x87\xe6\xa1\xa3\xe4\xb8\xad\xe6\x89\x80\xe6\x9c\x89\xe5\x85\xb3\xe9\x94\xae\xe6\x95\xb0\xe6\x8d\xae\xef\xbc\x8c\xe5\x8c\x85\xe6\x8b\xac\xe6\x95\xb0\xe5\xad\x97\xe3\x80\x81\xe6\x97\xa5\xe6\x9c\x9f\xe3\x80\x81\xe4\xba\xba\xe5\x90\x8d\xe3\x80\x81\xe6\x9c\xba\xe6\x9e\x84\xe7\xad\x89\xe5\xae\x9e\xe4\xbd\x93\xe4\xbf\xa1\xe6\x81\xaf\xef\xbc\x8c\xe4\xbb\xa5\xe7\xbb\x93\xe6\x9e\x84\xe5\x8c\x96\xe5\xbd\xa2\xe5\xbc\x8f\xe8\xbe\x93\xe5\x87\xba", IconHelper::search(14, QColor("#4F46E5"))},
        {"\xe6\xb6\xa6\xe8\x89\xb2", "\xe8\xaf\xb7\xe5\xaf\xb9\xe6\x96\x87\xe6\xa1\xa3\xe5\x86\x85\xe5\xae\xb9\xe8\xbf\x9b\xe8\xa1\x8c\xe6\xb6\xa6\xe8\x89\xb2\xe5\x92\x8c\xe4\xbc\x98\xe5\x8c\x96\xef\xbc\x8c\xe4\xbd\xbf\xe8\xaf\xad\xe8\xa8\x80\xe6\x9b\xb4\xe8\xa7\x84\xe8\x8c\x83\xe6\xb5\x81\xe7\x95\x85", IconHelper::pencil(14, QColor("#4F46E5"))},
        {"\xe6\xa0\xbc\xe5\xbc\x8f", "\xe8\xaf\xb7\xe8\xb0\x83\xe6\x95\xb4\xe6\x96\x87\xe6\xa1\xa3\xe7\x9a\x84\xe6\xa0\xbc\xe5\xbc\x8f\xe7\xbb\x93\xe6\x9e\x84\xef\xbc\x8c\xe4\xbc\x98\xe5\x8c\x96\xe6\xa0\x87\xe9\xa2\x98\xe5\xb1\x82\xe7\xba\xa7\xe3\x80\x81\xe6\xae\xb5\xe8\x90\xbd\xe5\x88\x92\xe5\x88\x86", IconHelper::ruler(14, QColor("#4F46E5"))},
        {"\xe5\x88\x86\xe6\x9e\x90", "\xe8\xaf\xb7\xe5\x88\x86\xe6\x9e\x90\xe6\x96\x87\xe6\xa1\xa3\xe4\xb8\xad\xe7\x9a\x84\xe6\x95\xb0\xe6\x8d\xae\xef\xbc\x8c\xe7\xbb\x99\xe5\x87\xba\xe8\xb6\x8b\xe5\x8a\xbf\xe5\x88\x86\xe6\x9e\x90\xe5\x92\x8c\xe5\x85\xb3\xe9\x94\xae\xe5\x8f\x91\xe7\x8e\xb0", IconHelper::chart(14, QColor("#4F46E5"))},
        {"\xe7\xb2\xbe\xe7\xae\x80", "\xe8\xaf\xb7\xe5\x88\xa0\xe9\x99\xa4\xe6\x96\x87\xe6\xa1\xa3\xe4\xb8\xad\xe4\xb8\x8d\xe5\xbf\x85\xe8\xa6\x81\xe7\x9a\x84\xe5\x86\x97\xe4\xbd\x99\xe5\x86\x85\xe5\xae\xb9\xe5\x92\x8c\xe9\x87\x8d\xe5\xa4\x8d\xe6\xae\xb5\xe8\x90\xbd", IconHelper::trash(14, QColor("#4F46E5"))},
        {"\xe8\xa1\xa5\xe5\x85\x85", "\xe8\xaf\xb7\xe4\xb8\xba\xe6\x96\x87\xe6\xa1\xa3\xe8\xa1\xa5\xe5\x85\x85\xe7\xbc\xba\xe5\xa4\xb1\xe7\x9a\x84\xe7\xab\xa0\xe8\x8a\x82\xe5\x86\x85\xe5\xae\xb9\xef\xbc\x8c\xe5\xae\x8c\xe5\x96\x84\xe6\x96\x87\xe6\xa1\xa3\xe7\xbb\x93\xe6\x9e\x84", IconHelper::plus(14, QColor("#4F46E5"))},
        {"\xe7\xbf\xbb\xe8\xaf\x91", "\xe8\xaf\xb7\xe5\xb0\x86\xe6\x96\x87\xe6\xa1\xa3\xe7\xbf\xbb\xe8\xaf\x91\xe4\xb8\xba\xe8\x8b\xb1\xe6\x96\x87\xef\xbc\x8c\xe4\xbf\x9d\xe6\x8c\x81\xe5\x8e\x9f\xe6\x96\x87\xe6\xa0\xbc\xe5\xbc\x8f\xe4\xb8\x8d\xe5\x8f\x98", IconHelper::globe(14, QColor("#4F46E5"))},
        {"\xe5\xaf\xb9\xe6\xaf\x94", "\xe8\xaf\xb7\xe5\xaf\xb9\xe6\xaf\x94\xe5\x88\x86\xe6\x9e\x90\xe6\x96\x87\xe6\xa1\xa3\xe4\xb8\xad\xe7\x9a\x84\xe4\xb8\x8d\xe5\x90\x8c\xe8\xa7\x82\xe7\x82\xb9\xe6\x88\x96\xe6\x96\xb9\xe6\xa1\x88\xef\xbc\x8c\xe5\x88\x97\xe5\x87\xba\xe4\xbc\x98\xe5\x8a\xa3", IconHelper::table(14, QColor("#4F46E5"))},
    };
    for (int i = 0; i < cmds.size(); i++) {
        QPushButton *cmdBtn = new QPushButton(cmds[i].label);
        cmdBtn->setIcon(QIcon(cmds[i].icon));
        cmdBtn->setIconSize(QSize(14, 14));
        cmdBtn->setCursor(Qt::PointingHandCursor);
        cmdBtn->setStyleSheet(
            "QPushButton { background: #F9FAFB; border: 1px solid #E5E7EB; border-radius: 8px;"
            "  padding: 6px 4px; font-size: 11px; color: #4F46E5; text-align: center; }"
            "QPushButton:hover { background: #EEF2FF; border-color: #818CF8; }");
        QString prompt = cmds[i].prompt;
        connect(cmdBtn, &QPushButton::clicked, [this, prompt]() { sendCommand(prompt); });
        gridLayout->addWidget(cmdBtn, i / 3, i % 3);
    }

    // Spacing between sections (no divider line)
    QWidget *spacer = new QWidget;
    spacer->setFixedHeight(8);

    // Conversations
    QHBoxLayout *convoHeader = new QHBoxLayout;
    QLabel *convoTitle = new QLabel("\xe5\x8e\x86\xe5\x8f\xb2\xe5\xaf\xb9\xe8\xaf\x9d");
    convoTitle->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    m_newConvoBtn = new QPushButton("+ 新对话");
    m_newConvoBtn->setMinimumHeight(28);
    m_newConvoBtn->setCursor(Qt::PointingHandCursor);
    m_newConvoBtn->setStyleSheet(
        "QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; font-size: 12px; padding: 0 12px; }"
        "QPushButton:hover { background: #3730A3; }");
    convoHeader->addWidget(convoTitle);
    convoHeader->addStretch();
    convoHeader->addWidget(m_newConvoBtn);

    m_convoSearchEdit = new QLineEdit;
    m_convoSearchEdit->setPlaceholderText("搜索历史对话");
    m_convoSearchEdit->setMinimumHeight(30);
    m_convoSearchEdit->setStyleSheet(
        "QLineEdit { border: 1px solid #D1D5DB; border-radius: 6px; padding: 0 10px; font-size: 12px; }"
        "QLineEdit:focus { border-color: #818CF8; }");

    m_convoList = new QListWidget;
    m_convoList->setStyleSheet(
        "QListWidget { border: none; background: transparent; font-size: 12px; }"
        "QListWidget::item { padding: 8px 10px; border-radius: 6px; margin: 2px 0; }"
        "QListWidget::item:selected { background: #EEF2FF; color: #4F46E5; }"
        "QListWidget::item:hover { background: #F9FAFB; }"
    );

    // Helper to create collapsible section header
    auto makeToggle = [](const QString &title, QWidget *content, bool startExpanded = true) -> QPushButton* {
        QPushButton *btn = new QPushButton((startExpanded ? QString::fromUtf8("\u25BC ") : QString::fromUtf8("\u25B6 ")) + title);
        btn->setStyleSheet("QPushButton { background: transparent; border: none; font-size: 13px; font-weight: 600; color: #6B7280; text-align: left; padding: 4px 0; }"
                           "QPushButton:hover { color: #4F46E5; }");
        btn->setCursor(Qt::PointingHandCursor);
        content->setVisible(startExpanded);
        QObject::connect(btn, &QPushButton::clicked, [btn, content, title]() {
            bool vis = !content->isVisible();
            content->setVisible(vis);
            btn->setText((vis ? QString::fromUtf8("\u25BC ") : QString::fromUtf8("\u25B6 ")) + title);
        });
        return btn;
    };

    // Wrap doc section
    QWidget *docContainer = new QWidget;
    QVBoxLayout *docContLay = new QVBoxLayout(docContainer);
    docContLay->setContentsMargins(0, 0, 0, 0);
    docContLay->setSpacing(6);
    docContLay->addWidget(m_linkedDocList);
    docContLay->addWidget(m_selectDocBtn);
    QPushButton *docToggle = makeToggle(QString::fromUtf8("\xe5\x85\xb3\xe8\x81\x94\xe6\x96\x87\xe6\xa1\xa3"), docContainer);

    // Wrap cmd section
    QWidget *cmdContainer = new QWidget;
    QVBoxLayout *cmdContLay = new QVBoxLayout(cmdContainer);
    cmdContLay->setContentsMargins(0, 0, 0, 0);
    cmdContLay->addWidget(m_commandGrid);
    QPushButton *cmdToggle = makeToggle(QString::fromUtf8("\xe6\x96\x87\xe6\xa1\xa3\xe6\x93\x8d\xe4\xbd\x9c"), cmdContainer);

    // Wrap convo section
    QWidget *convoContainer = new QWidget;
    QVBoxLayout *convoContLay = new QVBoxLayout(convoContainer);
    convoContLay->setContentsMargins(0, 0, 0, 0);
    convoContLay->setSpacing(6);
    convoContLay->addWidget(m_convoSearchEdit);
    convoContLay->addWidget(m_convoList, 1);

    sideLayout->addWidget(docToggle);
    sideLayout->addWidget(docContainer);
    sideLayout->addSpacing(4);
    sideLayout->addWidget(cmdToggle);
    sideLayout->addWidget(cmdContainer);
    sideLayout->addWidget(spacer);
    sideLayout->addLayout(convoHeader);
    sideLayout->addWidget(convoContainer, 1);

    // ===== Main Chat Area =====
    QWidget *chatArea = new QWidget;
    QVBoxLayout *chatLayout = new QVBoxLayout(chatArea);
    chatLayout->setContentsMargins(0, 0, 0, 0);
    chatLayout->setSpacing(0);

    // Top bar
    QWidget *chatTopBar = new QWidget;
    chatTopBar->setStyleSheet("background: white; border-bottom: 1px solid #F3F4F6;");
    chatTopBar->setFixedHeight(48);
    QHBoxLayout *chatTopLayout = new QHBoxLayout(chatTopBar);
    chatTopLayout->setContentsMargins(16, 0, 16, 0);

    // Logo + title
    QWidget *titleRow = new QWidget;
    QHBoxLayout *titleLayout = new QHBoxLayout(titleRow);
    titleLayout->setContentsMargins(0, 0, 0, 0);
    titleLayout->setSpacing(8);

    QLabel *logoLabel = new QLabel;
    logoLabel->setFixedSize(28, 28);
    QPixmap logoPm(28, 28);
    logoPm.fill(Qt::transparent);
    { QPainter p(&logoPm);
      p.setRenderHint(QPainter::Antialiasing);
      p.setPen(QPen(QColor("#4F46E5"), 2));
      p.drawEllipse(QPointF(14, 14), 11, 11);
      p.setBrush(QColor("#4F46E5"));
      p.setPen(Qt::NoPen);
      p.drawEllipse(QPointF(14, 14), 4, 4);
      p.setPen(QPen(QColor("#4F46E5"), 2));
      p.drawLine(14, 1, 14, 5); p.drawLine(14, 23, 14, 27);
      p.drawLine(1, 14, 5, 14); p.drawLine(23, 14, 27, 14);
    }
    logoLabel->setPixmap(logoPm);

    m_chatTitleLabel = new QLabel("AI \xe6\x99\xba\xe8\x83\xbd\xe5\xaf\xb9\xe8\xaf\x9d");
    m_chatTitleLabel->setStyleSheet("font-size: 15px; font-weight: 600; color: #111827;");

    QLabel *modelTag = new QLabel;
    modelTag->setStyleSheet("font-size: 11px; color: #4F46E5; background: #EEF2FF; padding: 2px 8px; border-radius: 4px;");
    modelTag->setText("AI");

    titleLayout->addWidget(logoLabel);
    titleLayout->addWidget(m_chatTitleLabel);
    titleLayout->addWidget(modelTag);
    titleLayout->addStretch();

    m_modelCombo = new QComboBox;
    m_modelCombo->setMinimumWidth(220);
    m_modelCombo->setMinimumHeight(32);
    // Save chevron icon for combobox dropdown arrow
    QPixmap chevron = IconHelper::chevronDown(12, QColor("#6B7280"));
    QString chevronPath = QDir::temp().filePath("docai_chevron.png");
    chevron.save(chevronPath);
    m_modelCombo->setStyleSheet(
        "QComboBox { border: 1px solid #E5E7EB; border-radius: 8px; padding: 4px 12px; font-size: 12px; color: #374151; background: #F9FAFB; }"
        "QComboBox:hover { border-color: #818CF8; background: #EEF2FF; }"
        "QComboBox:focus { border-color: #4F46E5; background: white; }"
        "QComboBox::drop-down { border: none; width: 28px; subcontrol-position: right center; }"
        "QComboBox::down-arrow { image: url(" + chevronPath + "); width: 12px; height: 12px; }"
        "QComboBox QAbstractItemView { border: 1px solid #E5E7EB; border-radius: 8px; padding: 4px; background: white; selection-background-color: #EEF2FF; selection-color: #4F46E5; outline: none; }"
        "QComboBox QAbstractItemView::item { padding: 6px 10px; border-radius: 4px; min-height: 28px; }"
        "QComboBox QAbstractItemView::item:hover { background: #F3F4F6; }");

    QPushButton *clearChatBtn = new QPushButton;
    clearChatBtn->setFixedSize(32, 32);
    clearChatBtn->setCursor(Qt::PointingHandCursor);
    clearChatBtn->setToolTip("\xe6\xb8\x85\xe7\xa9\xba\xe5\xaf\xb9\xe8\xaf\x9d");
    clearChatBtn->setStyleSheet(
        "QPushButton { background: transparent; border: none; border-radius: 6px; }"
        "QPushButton:hover { background: #FEE2E2; }");
    clearChatBtn->setIcon(QIcon(IconHelper::trash(18, QColor("#6B7280"))));
    clearChatBtn->setIconSize(QSize(18, 18));

    QPushButton *exportBtn = new QPushButton("\xe5\xaf\xbc\xe5\x87\xba");
    exportBtn->setMinimumHeight(30);
    exportBtn->setStyleSheet(
        "QPushButton { background: #D1FAE5; color: #10B981; border: none; border-radius: 6px; padding: 0 12px; font-size: 12px; }"
        "QPushButton:hover { background: #D9F7BE; }");

    chatTopLayout->addWidget(titleRow, 1);
    chatTopLayout->addWidget(m_modelCombo);
    chatTopLayout->addWidget(clearChatBtn);
    chatTopLayout->addWidget(exportBtn);

    // Chat scroll area with message widgets
    m_chatScroll = new QScrollArea;
    m_chatScroll->setWidgetResizable(true);
    m_chatScroll->setFrameShape(QFrame::NoFrame);
    m_chatScroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_chatScroll->setStyleSheet(
        "QScrollArea { background: white; border: none; }"
        "QScrollBar:vertical { width: 6px; background: transparent; }"
        "QScrollBar::handle:vertical { background: #D1D5DB; border-radius: 3px; }"
        "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }");

    m_chatContainer = new QWidget;
    m_chatContainer->setStyleSheet("background: transparent;");
    m_chatMsgLayout = new QVBoxLayout(m_chatContainer);
    m_chatMsgLayout->setContentsMargins(24, 20, 24, 20);
    m_chatMsgLayout->setSpacing(16);

    // Welcome widget
    m_welcomeWidget = new QWidget;
    QVBoxLayout *welcomeLayout = new QVBoxLayout(m_welcomeWidget);
    welcomeLayout->setAlignment(Qt::AlignCenter);
    welcomeLayout->setSpacing(12);

    QLabel *welcomeIcon = new QLabel;
    welcomeIcon->setFixedSize(48, 48);
    welcomeIcon->setAlignment(Qt::AlignCenter);
    QPixmap wPm(48, 48);
    wPm.fill(Qt::transparent);
    { QPainter p(&wPm);
      p.setRenderHint(QPainter::Antialiasing);
      p.setPen(QPen(QColor("#4F46E5"), 1.5));
      p.drawEllipse(QPointF(24, 24), 20, 20);
      p.setBrush(QColor("#4F46E5"));
      p.setPen(Qt::NoPen);
      p.drawEllipse(QPointF(24, 24), 7, 7);
      p.setPen(QPen(QColor("#4F46E5"), 1.5));
      p.drawLine(24, 2, 24, 8); p.drawLine(24, 40, 24, 46);
      p.drawLine(2, 24, 8, 24); p.drawLine(40, 24, 46, 24);
    }
    welcomeIcon->setPixmap(wPm);

    QLabel *welcomeTitle = new QLabel("\xe6\xac\xa2\xe8\xbf\x8e\xe4\xbd\xbf\xe7\x94\xa8 DocAI \xe6\x99\xba\xe8\x83\xbd\xe5\x8a\xa9\xe6\x89\x8b");
    welcomeTitle->setStyleSheet("font-size: 20px; font-weight: 700; color: #111827;");
    welcomeTitle->setAlignment(Qt::AlignCenter);

    QLabel *welcomeDesc = new QLabel("\xe6\x94\xaf\xe6\x8c\x81\xe6\x96\x87\xe6\xa1\xa3\xe5\x88\x86\xe6\x9e\x90\xe3\x80\x81\xe5\x86\x85\xe5\xae\xb9\xe6\x80\xbb\xe7\xbb\x93\xe3\x80\x81\xe6\x99\xba\xe8\x83\xbd\xe9\x97\xae\xe7\xad\x94\xe3\x80\x81\xe6\x96\x87\xe6\xa1\xa3\xe7\xbc\x96\xe8\xbe\x91\xe7\xad\x89\xe8\x83\xbd\xe5\x8a\x9b");
    welcomeDesc->setStyleSheet("font-size: 13px; color: #9CA3AF;");
    welcomeDesc->setAlignment(Qt::AlignCenter);

    // Welcome feature tags
    QWidget *wfRow = new QWidget;
    QHBoxLayout *wfLayout = new QHBoxLayout(wfRow);
    wfLayout->setAlignment(Qt::AlignCenter);
    wfLayout->setSpacing(16);

    struct WF { QString icon; QString text; };
    QList<WF> wfItems = {
        {"\xe6\x96\x87", "\xe6\x96\x87\xe6\xa1\xa3\xe5\x86\x85\xe5\xae\xb9\xe7\x90\x86\xe8\xa7\xa3\xe4\xb8\x8e\xe6\x8f\x90\xe9\x97\xae"},
        {"\xe6\x95\xb0", "\xe6\x95\xb0\xe6\x8d\xae\xe5\x88\x86\xe6\x9e\x90\xe4\xb8\x8e\xe4\xbf\xa1\xe6\x81\xaf\xe6\x8f\x90\xe5\x8f\x96"},
        {"\xe7\xbc\x96", "\xe6\x96\x87\xe6\xa1\xa3\xe7\xbc\x96\xe8\xbe\x91\xe3\x80\x81\xe6\xb6\xa6\xe8\x89\xb2\xe4\xb8\x8e\xe6\xa0\xbc\xe5\xbc\x8f\xe8\xb0\x83\xe6\x95\xb4"},
    };
    for (const auto &wf : wfItems) {
        QWidget *item = new QWidget;
        QHBoxLayout *il = new QHBoxLayout(item);
        il->setContentsMargins(12, 8, 12, 8);
        il->setSpacing(8);
        item->setStyleSheet("background: #EEF2FF; border-radius: 8px;");

        QLabel *iconLbl = new QLabel(wf.icon);
        iconLbl->setFixedSize(20, 20);
        iconLbl->setAlignment(Qt::AlignCenter);
        iconLbl->setStyleSheet("font-size: 12px; color: #4F46E5; background: white; border-radius: 10px; font-weight: bold;");

        QLabel *textLbl = new QLabel(wf.text);
        textLbl->setStyleSheet("font-size: 12px; color: #4F46E5;");

        il->addWidget(iconLbl);
        il->addWidget(textLbl);
        wfLayout->addWidget(item);
    }

    welcomeLayout->addStretch();
    welcomeLayout->addWidget(welcomeIcon, 0, Qt::AlignCenter);
    welcomeLayout->addWidget(welcomeTitle);
    welcomeLayout->addWidget(welcomeDesc);
    welcomeLayout->addSpacing(8);
    welcomeLayout->addWidget(wfRow);
    welcomeLayout->addStretch();

    m_chatMsgLayout->addWidget(m_welcomeWidget);
    m_chatMsgLayout->addStretch();

    m_chatScroll->setWidget(m_chatContainer);

    // Typing indicator (hidden by default)
    m_typingWidget = new QWidget;
    m_typingWidget->setVisible(false);
    QHBoxLayout *typingLayout = new QHBoxLayout(m_typingWidget);
    typingLayout->setContentsMargins(24, 4, 24, 4);
    typingLayout->setAlignment(Qt::AlignLeft);

    QLabel *typingAvatar = new QLabel("AI");
    typingAvatar->setFixedSize(32, 32);
    typingAvatar->setAlignment(Qt::AlignCenter);
    typingAvatar->setStyleSheet("background: #EEF2FF; border-radius: 16px; font-size: 11px; color: #4F46E5; font-weight: bold;");

    QLabel *typingDots = new QLabel("\xe2\x80\xa2 \xe2\x80\xa2 \xe2\x80\xa2");
    typingDots->setStyleSheet("font-size: 18px; color: #818CF8; background: white; border: 1px solid #F3F4F6; border-radius: 12px; padding: 8px 16px;");

    typingLayout->addWidget(typingAvatar);
    typingLayout->addWidget(typingDots);
    typingLayout->addStretch();

    // Status
    m_statusLabel = new QLabel;
    m_statusLabel->setVisible(false);
    m_statusLabel->setStyleSheet("padding: 8px 20px; font-size: 12px; color: #4F46E5; background: #EEF2FF;");

    // Input area
    QWidget *inputArea = new QWidget;
    inputArea->setStyleSheet("background: white; border-top: 1px solid #F3F4F6;");
    QVBoxLayout *inputOuterLayout = new QVBoxLayout(inputArea);
    inputOuterLayout->setContentsMargins(20, 12, 20, 8);
    inputOuterLayout->setSpacing(4);

    QWidget *inputRow = new QWidget;
    QHBoxLayout *inputLayout = new QHBoxLayout(inputRow);
    inputLayout->setContentsMargins(0, 0, 0, 0);
    inputLayout->setSpacing(8);

    m_inputEdit = new QTextEdit;
    m_inputEdit->setFixedHeight(48);
    m_inputEdit->setPlaceholderText(QString::fromUtf8("\xe8\xbe\x93\xe5\x85\xa5\xe6\xb6\x88\xe6\x81\xaf\xef\xbc\x8c") + "Ctrl+Enter " + QString::fromUtf8("\xe5\x8f\x91\xe9\x80\x81\xef\xbc\x8c") + "Enter " + QString::fromUtf8("\xe6\x8d\xa2\xe8\xa1\x8c") + "...");
    m_inputEdit->installEventFilter(this);
    m_inputEdit->setVerticalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_inputEdit->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_inputEdit->setStyleSheet(
        "QTextEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 10px 14px; font-size: 14px; }"
        "QTextEdit:focus { border-color: #818CF8; }");

    m_sendBtn = new QPushButton("\xe5\x8f\x91\xe9\x80\x81");
    m_sendBtn->setFixedSize(72, 48);
    m_sendBtn->setCursor(Qt::PointingHandCursor);
    m_sendBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #818CF8, stop:1 #4F46E5);"
        "  color: white; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; }"
        "QPushButton:hover { background: #4F46E5; }"
        "QPushButton:disabled { background: #C7D2FE; }");

    m_stopBtn = new QPushButton("\xe5\x81\x9c\xe6\xad\xa2");
    m_stopBtn->setFixedSize(72, 48);
    m_stopBtn->setVisible(false);
    m_stopBtn->setCursor(Qt::PointingHandCursor);
    m_stopBtn->setStyleSheet(
        "QPushButton { background: #EF4444; color: white; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; }"
        "QPushButton:hover { background: #DC2626; }");

    inputLayout->addWidget(m_inputEdit, 1);
    inputLayout->addWidget(m_sendBtn);
    inputLayout->addWidget(m_stopBtn);

    // Footer with char count
    QWidget *inputFooter = new QWidget;
    QHBoxLayout *footerLayout = new QHBoxLayout(inputFooter);
    footerLayout->setContentsMargins(0, 0, 0, 0);
    QLabel *footerHint = new QLabel(QString::fromUtf8("\xe2\x9a\xa0 AI \xe7\x94\x9f\xe6\x88\x90\xe5\x86\x85\xe5\xae\xb9\xe4\xbb\x85\xe4\xbe\x9b\xe5\x8f\x82\xe8\x80\x83\xef\xbc\x8c\xe8\xaf\xb7\xe6\x82\xa8\xe6\xa0\xb8\xe5\xae\x9e\xe4\xbf\xa1\xe6\x81\xaf\xe5\x87\x86\xe7\xa1\xae\xe6\x80\xa7"));
    footerHint->setStyleSheet("font-size: 11px; color: #9CA3AF;");
    m_charCountLabel = new QLabel;
    m_charCountLabel->setStyleSheet("font-size: 11px; color: #9CA3AF;");
    m_charCountLabel->setVisible(false);
    footerLayout->addWidget(footerHint);
    footerLayout->addStretch();
    footerLayout->addWidget(m_charCountLabel);

    inputOuterLayout->addWidget(inputRow);
    inputOuterLayout->addWidget(inputFooter);

    chatLayout->addWidget(chatTopBar);
    chatLayout->addWidget(m_chatScroll, 1);
    chatLayout->addWidget(m_typingWidget);
    chatLayout->addWidget(m_statusLabel);
    chatLayout->addWidget(inputArea);

    mainLayout->addWidget(m_sidebar);
    mainLayout->addWidget(chatArea, 1);

    // Connections
    connect(m_sendBtn, &QPushButton::clicked, this, &AIChatPage::sendMessage);
    connect(m_stopBtn, &QPushButton::clicked, this, &AIChatPage::stopGeneration);
    connect(m_selectDocBtn, &QPushButton::clicked, this, &AIChatPage::selectDocument);
    connect(m_newConvoBtn, &QPushButton::clicked, this, &AIChatPage::createNewConversation);
    connect(exportBtn, &QPushButton::clicked, this, &AIChatPage::exportResult);
    connect(clearChatBtn, &QPushButton::clicked, this, &AIChatPage::clearChat);

    connect(m_convoList, &QListWidget::currentRowChanged, [this](int row) {
        if (row >= 0 && row < m_conversations.size()) {
            loadConversation(m_conversations[row].id);
        }
    });

    connect(m_convoSearchEdit, &QLineEdit::textChanged, [this](const QString &text) {
        for (int i = 0; i < m_convoList->count() && i < m_conversations.size(); i++) {
            bool match = text.isEmpty() || m_conversations[i].title.contains(text, Qt::CaseInsensitive);
            m_convoList->item(i)->setHidden(!match);
        }
    });

    connect(m_modelCombo, QOverload<int>::of(&QComboBox::currentIndexChanged), [this](int idx) {
        if (idx < 0) return;
        QString providerModel = m_modelCombo->currentData().toString();
        ApiClient::instance().switchLlmProvider(providerModel, [this](bool ok, const QJsonObject &, const QString &err) {
            if (ok) {
                m_statusLabel->setText(QString::fromUtf8("\xe5\xb7\xb2\xe5\x88\x87\xe6\x8d\xa2\xe6\xa8\xa1\xe5\x9e\x8b: ") + m_modelCombo->currentText());
                m_statusLabel->setVisible(true);
                QTimer::singleShot(2000, [this]() { if (!m_sse->isRunning()) m_statusLabel->setVisible(false); });
            }
        });
    });

    // Char count on input change
    connect(m_inputEdit, &QTextEdit::textChanged, [this]() {
        int len = m_inputEdit->toPlainText().length();
        if (len > 0) {
            m_charCountLabel->setText(QString::number(len));
            m_charCountLabel->setVisible(true);
        } else {
            m_charCountLabel->setVisible(false);
        }
    });

    // Load models
    ApiClient::instance().getLlmProviders([this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) {
            m_modelCombo->addItem("dashscope / qwen-plus", "dashscope:qwen-plus");
            return;
        }
        QJsonArray arr = data["data"].toArray();
        if (arr.isEmpty()) {
            m_modelCombo->addItem("dashscope / qwen-plus", "dashscope:qwen-plus");
            return;
        }
        m_modelCombo->blockSignals(true);
        for (const auto &v : arr) {
            QJsonObject obj = v.toObject();
            QString providerName = obj["name"].toString();
            bool available = obj["available"].toBool(true);
            QJsonArray models = obj["models"].toArray();
            QString defaultModel = obj["defaultModel"].toString();
            if (models.isEmpty() && !defaultModel.isEmpty()) {
                // Just add the default model
                QString label = providerName + " / " + defaultModel;
                if (!available) label += QString::fromUtf8(" [\xe4\xb8\x8d\xe5\x8f\xaf\xe7\x94\xa8]");
                m_modelCombo->addItem(label, providerName + ":" + defaultModel);
            } else {
                for (const auto &mv : models) {
                    QString modelName = mv.toString();
                    QString label = providerName + " / " + modelName;
                    if (!available) label += QString::fromUtf8(" [\xe4\xb8\x8d\xe5\x8f\xaf\xe7\x94\xa8]");
                    m_modelCombo->addItem(label, providerName + ":" + modelName);
                }
            }
        }
        m_modelCombo->blockSignals(false);
    });
    // Also get current active provider
    ApiClient::instance().getCurrentLlmProvider([this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        QString currentProvider = data["data"].toObject()["currentProvider"].toString();
        if (currentProvider.isEmpty()) currentProvider = data["data"].toObject()["name"].toString();
        QString currentModel = data["data"].toObject()["currentModel"].toString();
        QString matchKey = currentProvider + ":" + currentModel;
        if (matchKey == ":") return;
        m_modelCombo->blockSignals(true);
        for (int i = 0; i < m_modelCombo->count(); i++) {
            if (m_modelCombo->itemData(i).toString() == matchKey) {
                m_modelCombo->setCurrentIndex(i);
                break;
            }
        }
        m_modelCombo->blockSignals(false);
    });
}

void AIChatPage::sendMessage() {
    QString text = m_inputEdit->toPlainText().trimmed();
    if (text.isEmpty() || m_sse->isRunning()) return;
    m_lastUserPrompt = text;
    m_inputEdit->clear();

    // Hide welcome, show messages
    m_welcomeWidget->setVisible(false);

    appendMessage("user", text);
    saveMessageToConversation("user", text);

    m_sendBtn->setVisible(false);
    m_stopBtn->setVisible(true);
    m_statusLabel->setText("AI \xe6\xad\xa3\xe5\x9c\xa8\xe6\x80\x9d\xe8\x80\x83...");
    m_statusLabel->setVisible(true);
    m_typingWidget->setVisible(true);

    m_sse->start(text, m_linkedDocIds.isEmpty() ? 0 : m_linkedDocIds.first());
}

void AIChatPage::sendCommand(const QString &cmd) {
    m_inputEdit->setPlainText(cmd);
    sendMessage();
}

void AIChatPage::stopGeneration() {
    m_sse->abort();
    m_sendBtn->setVisible(true);
    m_stopBtn->setVisible(false);
    m_statusLabel->setVisible(false);
}

void AIChatPage::onSseText(const QString &text) {
    Q_UNUSED(text);
    m_statusLabel->setText("AI \xe6\xad\xa3\xe5\x9c\xa8\xe5\x9b\x9e\xe5\xa4\x8d...");
}

void AIChatPage::onSseComplete(const QString &text, const QString &excelUrl) {
    appendMessage("assistant", text, excelUrl);
    saveMessageToConversation("assistant", text);
    m_sendBtn->setVisible(true);
    m_stopBtn->setVisible(false);
    m_statusLabel->setVisible(false);
    m_typingWidget->setVisible(false);
}

void AIChatPage::onSseError(const QString &err) {
    appendMessage("system", "\xe9\x94\x99\xe8\xaf\xaf: " + err);
    m_sendBtn->setVisible(true);
    m_stopBtn->setVisible(false);
    m_statusLabel->setVisible(false);
    m_typingWidget->setVisible(false);
}

void AIChatPage::appendMessage(const QString &role, const QString &content, const QString &excelUrl) {
    // Remove the stretch at the end, we'll re-add it
    QLayoutItem *lastItem = m_chatMsgLayout->itemAt(m_chatMsgLayout->count() - 1);
    if (lastItem && lastItem->spacerItem()) {
        m_chatMsgLayout->removeItem(lastItem);
        delete lastItem;
    }

    QWidget *msgRow = new QWidget;
    QHBoxLayout *rowLayout = new QHBoxLayout(msgRow);
    rowLayout->setContentsMargins(0, 0, 0, 0);
    rowLayout->setSpacing(8);

    if (role == "user") {
        // User message: bubble on right, avatar always visible
        rowLayout->addStretch(1);

        QWidget *bubble = new QWidget;
        bubble->setMaximumWidth(500);
        bubble->setSizePolicy(QSizePolicy::Preferred, QSizePolicy::Preferred);
        bubble->setStyleSheet(
            "background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #818CF8, stop:1 #4F46E5);"
            "border-radius: 10px 10px 4px 10px; padding: 0;");
        QVBoxLayout *bubbleLayout = new QVBoxLayout(bubble);
        bubbleLayout->setContentsMargins(16, 12, 16, 12);
        QLabel *textLabel = new QLabel(content.toHtmlEscaped().replace("\n", "<br>"));
        textLabel->setWordWrap(true);
        textLabel->setTextFormat(Qt::RichText);
        textLabel->setTextInteractionFlags(Qt::TextSelectableByMouse);
        textLabel->setStyleSheet("font-size: 14px; color: white; background: transparent;");
        bubbleLayout->addWidget(textLabel);

        // User avatar
        QString uname = TokenManager::instance().username();
        QString avatarChar = uname.isEmpty() ? "U" : uname.left(1).toUpper();
        QLabel *avatar = new QLabel(avatarChar);
        avatar->setFixedSize(32, 32);
        avatar->setAlignment(Qt::AlignCenter);
        avatar->setStyleSheet(
            "background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #818CF8, stop:1 #4F46E5);"
            "border-radius: 16px; font-size: 13px; color: white; font-weight: bold;");

        rowLayout->addWidget(bubble);
        rowLayout->addWidget(avatar, 0, Qt::AlignTop);

    } else if (role == "assistant") {
        // AI avatar
        QLabel *avatar = new QLabel("AI");
        avatar->setFixedSize(32, 32);
        avatar->setAlignment(Qt::AlignCenter);
        avatar->setStyleSheet("background: #EEF2FF; border-radius: 16px; font-size: 11px; color: #4F46E5; font-weight: bold;");

        // Bubble with markdown content + action buttons
        QWidget *bubbleCol = new QWidget;
        bubbleCol->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Preferred);
        QVBoxLayout *colLayout = new QVBoxLayout(bubbleCol);
        colLayout->setContentsMargins(0, 0, 0, 0);
        colLayout->setSpacing(4);

        QTextBrowser *textBrowser = new QTextBrowser;
        textBrowser->setOpenExternalLinks(true);
        textBrowser->setFrameShape(QFrame::NoFrame);
        textBrowser->setVerticalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
        textBrowser->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
        textBrowser->document()->setMarkdown(content, QTextDocument::MarkdownDialectGitHub);
        textBrowser->setStyleSheet(
            "QTextBrowser { background: #F3F4F6; border: none; border-radius: 10px 10px 10px 4px; padding: 12px 16px; font-size: 14px; color: #1F2937; }");
        textBrowser->setMinimumHeight(48);
        textBrowser->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Preferred);

        // Two-stage deferred height: first let layout run, then calc
        QTimer::singleShot(50, [textBrowser]() {
            int w = textBrowser->viewport()->width();
            if (w <= 0) w = textBrowser->width() - 32;
            if (w > 0) textBrowser->document()->setTextWidth(w);
            int h = static_cast<int>(textBrowser->document()->size().height()) + 32;
            textBrowser->setFixedHeight(qMax(h, 48));
        });

        colLayout->addWidget(textBrowser);

        // Action bar
        QWidget *actionBar = createMessageActionBar(content, excelUrl);
        colLayout->addWidget(actionBar);

        rowLayout->addWidget(avatar, 0, Qt::AlignTop);
        rowLayout->addWidget(bubbleCol, 1);
        rowLayout->addSpacing(60);

    } else {
        // System message
        rowLayout->addStretch();
        QLabel *sysLabel = new QLabel(content.toHtmlEscaped());
        sysLabel->setStyleSheet(
            "font-size: 12px; color: #9CA3AF; background: #F9FAFB; padding: 4px 12px; border-radius: 12px;");
        rowLayout->addWidget(sysLabel);
        rowLayout->addStretch();
    }

    m_chatMsgLayout->addWidget(msgRow);
    m_chatMsgLayout->addStretch();

    scrollToBottom();
}

QWidget *AIChatPage::createMessageActionBar(const QString &content, const QString &excelUrl) {
    QWidget *bar = new QWidget;
    QHBoxLayout *layout = new QHBoxLayout(bar);
    layout->setContentsMargins(0, 2, 0, 0);
    layout->setSpacing(4);

    auto makeBtn = [](const QString &tooltip, const QColor &iconColor) -> QPushButton* {
        QPushButton *btn = new QPushButton;
        btn->setFixedSize(28, 28);
        btn->setCursor(Qt::PointingHandCursor);
        btn->setToolTip(tooltip);
        btn->setStyleSheet(
            "QPushButton { background: transparent; border: none; border-radius: 6px; }"
            "QPushButton:hover { background: #EEF2FF; }");
        Q_UNUSED(iconColor);
        return btn;
    };

    // Regenerate
    QPushButton *regenBtn = makeBtn("\xe9\x87\x8d\xe6\x96\xb0\xe7\x94\x9f\xe6\x88\x90", QColor("#6B7280"));
    regenBtn->setIcon(QIcon(IconHelper::refresh(14, QColor("#6B7280"))));
    regenBtn->setIconSize(QSize(14, 14));
    connect(regenBtn, &QPushButton::clicked, this, &AIChatPage::regenerateLastPrompt);

    // Continue
    QPushButton *contBtn = makeBtn("\xe7\xbb\xa7\xe7\xbb\xad\xe5\xaf\xb9\xe8\xaf\x9d", QColor("#6B7280"));
    contBtn->setIcon(QIcon(IconHelper::chat(14, QColor("#6B7280"))));
    contBtn->setIconSize(QSize(14, 14));
    connect(contBtn, &QPushButton::clicked, this, &AIChatPage::continueDialogue);

    // Copy
    QPushButton *copyBtn = makeBtn("\xe5\xa4\x8d\xe5\x88\xb6", QColor("#6B7280"));
    copyBtn->setIcon(QIcon(IconHelper::copy(14, QColor("#6B7280"))));
    copyBtn->setIconSize(QSize(14, 14));
    QString capturedContent = content;
    connect(copyBtn, &QPushButton::clicked, [capturedContent, copyBtn]() {
        QApplication::clipboard()->setText(capturedContent);
        copyBtn->setToolTip("\xe5\xb7\xb2\xe5\xa4\x8d\xe5\x88\xb6");
        QTimer::singleShot(1500, [copyBtn]() { copyBtn->setToolTip("\xe5\xa4\x8d\xe5\x88\xb6"); });
    });

    // Export to Word
    QPushButton *exportMsgBtn = makeBtn("\xe5\xaf\xbc\xe5\x87\xba\xe6\x96\x87\xe6\xa1\xa3", QColor("#6B7280"));
    exportMsgBtn->setIcon(QIcon(IconHelper::document(14, QColor("#6B7280"))));
    exportMsgBtn->setIconSize(QSize(14, 14));
    connect(exportMsgBtn, &QPushButton::clicked, [this, capturedContent]() {
        QString path = QFileDialog::getSaveFileName(this, "\xe5\xaf\xbc\xe5\x87\xba\xe6\x96\x87\xe6\xa1\xa3",
            QStandardPaths::writableLocation(QStandardPaths::DesktopLocation) + "/docai_output.md",
            "Markdown (*.md);;文本文件 (*.txt)");
        if (path.isEmpty()) return;
        QFile f(path);
        if (f.open(QIODevice::WriteOnly | QIODevice::Text)) {
            f.write(capturedContent.toUtf8());
            f.close();
            QMessageBox::information(this, "\xe6\x88\x90\xe5\x8a\x9f", "\xe5\xb7\xb2\xe5\xaf\xbc\xe5\x87\xba\xe5\x88\xb0: " + path);
        }
    });

    // Preview
    QPushButton *previewBtn = makeBtn("\xe9\xa2\x84\xe8\xa7\x88", QColor("#6B7280"));
    previewBtn->setIcon(QIcon(IconHelper::eye(14, QColor("#6B7280"))));
    previewBtn->setIconSize(QSize(14, 14));
    connect(previewBtn, &QPushButton::clicked, [this, capturedContent]() { previewContent(capturedContent); });

    // Send to Email
    QPushButton *emailBtn = makeBtn("\xe5\x8f\x91\xe9\x80\x81\xe8\x87\xb3\xe9\x82\xae\xe7\xae\xb1", QColor("#6B7280"));
    emailBtn->setIcon(QIcon(IconHelper::mail(14, QColor("#6B7280"))));
    emailBtn->setIconSize(QSize(14, 14));
    connect(emailBtn, &QPushButton::clicked, [this, capturedContent]() { sendToEmail(capturedContent); });

    // Download Excel (if available)
    if (!excelUrl.isEmpty()) {
        QPushButton *dlBtn = makeBtn("\xe4\xb8\x8b\xe8\xbd\xbd\xe6\x96\x87\xe4\xbb\xb6", QColor("#6B7280"));
        dlBtn->setIcon(QIcon(IconHelper::download(14, QColor("#6B7280"))));
        dlBtn->setIconSize(QSize(14, 14));
        layout->addWidget(dlBtn);
    }

    layout->addWidget(regenBtn);
    layout->addWidget(contBtn);
    layout->addWidget(copyBtn);
    layout->addWidget(previewBtn);
    layout->addWidget(exportMsgBtn);
    layout->addWidget(emailBtn);
    layout->addStretch();

    return bar;
}

void AIChatPage::scrollToBottom() {
    QTimer::singleShot(50, [this]() {
        QScrollBar *bar = m_chatScroll->verticalScrollBar();
        bar->setValue(bar->maximum());
    });
}

void AIChatPage::showWelcomeScreen() {
    // Remove all messages, show welcome
    while (m_chatMsgLayout->count() > 0) {
        QLayoutItem *item = m_chatMsgLayout->takeAt(0);
        if (item->widget() && item->widget() != m_welcomeWidget) {
            item->widget()->deleteLater();
        }
        delete item;
    }
    m_welcomeWidget->setVisible(true);
    m_chatMsgLayout->addWidget(m_welcomeWidget);
    m_chatMsgLayout->addStretch();
}

void AIChatPage::clearChat() {
    if (m_activeConversationId <= 0) return;
    int ret = QMessageBox::question(this, "\xe6\xb8\x85\xe7\xa9\xba\xe5\xaf\xb9\xe8\xaf\x9d",
        "\xe7\xa1\xae\xe5\xae\x9a\xe8\xa6\x81\xe6\xb8\x85\xe7\xa9\xba\xe5\xbd\x93\xe5\x89\x8d\xe5\xaf\xb9\xe8\xaf\x9d\xe5\x90\x97\xef\xbc\x9f",
        QMessageBox::Yes | QMessageBox::No);
    if (ret != QMessageBox::Yes) return;
    showWelcomeScreen();
}

void AIChatPage::regenerateLastPrompt() {
    if (m_lastUserPrompt.isEmpty() || m_sse->isRunning()) return;
    m_inputEdit->setPlainText(m_lastUserPrompt);
    sendMessage();
}

void AIChatPage::continueDialogue() {
    if (m_sse->isRunning()) return;
    m_inputEdit->setPlainText("\xe8\xaf\xb7\xe7\xbb\xa7\xe7\xbb\xad");
    sendMessage();
}

void AIChatPage::previewContent(const QString &content) {
    QDialog dlg(this);
    dlg.setWindowTitle("\xe5\x86\x85\xe5\xae\xb9\xe9\xa2\x84\xe8\xa7\x88");
    dlg.resize(800, 600);
    QVBoxLayout *lay = new QVBoxLayout(&dlg);
    lay->setContentsMargins(0, 0, 0, 0);
    QTextBrowser *browser = new QTextBrowser;
    browser->setOpenExternalLinks(true);
    browser->setStyleSheet("QTextBrowser { border: none; padding: 24px; font-size: 14px; }");
    browser->document()->setMarkdown(content, QTextDocument::MarkdownDialectGitHub);
    lay->addWidget(browser);
    dlg.exec();
}

void AIChatPage::sendToEmail(const QString &content) {
    if (content.isEmpty()) return;
    bool ok = false;
    QString email = QInputDialog::getText(this,
        "\xe5\x8f\x91\xe9\x80\x81\xe8\x87\xb3\xe9\x82\xae\xe7\xae\xb1",
        "\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe6\x8e\xa5\xe6\x94\xb6\xe9\x82\xae\xe7\xae\xb1:",
        QLineEdit::Normal, QString(), &ok);
    if (!ok || email.trimmed().isEmpty()) return;
    email = email.trimmed();
    QRegularExpression emailRe("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    if (!emailRe.match(email).hasMatch()) {
        QMessageBox::warning(this, "\xe9\x94\x99\xe8\xaf\xaf", "\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe6\x9c\x89\xe6\x95\x88\xe7\x9a\x84\xe9\x82\xae\xe7\xae\xb1\xe5\x9c\xb0\xe5\x9d\x80");
        return;
    }
    QString docNames;
    for (const auto &d : m_linkedDocs) { if (!docNames.isEmpty()) docNames += ", "; docNames += d.second; }
    QString subject = docNames.isEmpty()
        ? "DocAI - AI\xe7\x94\x9f\xe6\x88\x90\xe5\x86\x85\xe5\xae\xb9"
        : "DocAI - " + docNames + " AI\xe5\xa4\x84\xe7\x90\x86\xe7\xbb\x93\xe6\x9e\x9c";
    QJsonObject body;
    body["email"] = email;
    body["content"] = content;
    body["subject"] = subject;
    ApiClient::instance().sendContentEmail(body, [this](bool ok, const QJsonObject &, const QString &err) {
        if (ok) {
            QMessageBox::information(this, "\xe6\x88\x90\xe5\x8a\x9f", "\xe9\x82\xae\xe4\xbb\xb6\xe5\x8f\x91\xe9\x80\x81\xe6\x88\x90\xe5\x8a\x9f");
        } else {
            QMessageBox::warning(this, "\xe5\xa4\xb1\xe8\xb4\xa5", "\xe9\x82\xae\xe4\xbb\xb6\xe5\x8f\x91\xe9\x80\x81\xe5\xa4\xb1\xe8\xb4\xa5: " + err);
        }
    });
}

void AIChatPage::setLinkedDocument(int docId, const QString &docName) {
    if (!m_linkedDocIds.contains(docId)) {
        m_linkedDocIds.append(docId);
        m_linkedDocs.append({docId, docName});
        rebuildLinkedDocList();
    }
}

void AIChatPage::linkDocumentAndOpenChat(int docId, const QString &docName) {
    setLinkedDocument(docId, docName);
    // Search existing conversations for one matching this document name
    for (int i = 0; i < m_conversations.size(); i++) {
        if (m_conversations[i].title.contains(docName, Qt::CaseInsensitive)) {
            m_convoList->setCurrentRow(i);
            loadConversation(m_conversations[i].id);
            return;
        }
    }
    // No existing conversation found, create a new one with document name
    QJsonObject body;
    body["title"] = docName + " - AI\xe5\xaf\xb9\xe8\xaf\x9d";
    ApiClient::instance().createConversation(body, [this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        QJsonObject d = data["data"].toObject();
        m_activeConversationId = d["id"].toInt();
        loadConversationList();
        showWelcomeScreen();
    });
}

void AIChatPage::saveMessageToConversation(const QString &role, const QString &content) {
    if (m_activeConversationId <= 0) return;
    QJsonObject body;
    body["role"] = role;
    body["content"] = content;
    ApiClient::instance().addConversationMessage(m_activeConversationId, body, [](bool, const QJsonObject &, const QString &) {});
}

void AIChatPage::createNewConversation() {
    QJsonObject body;
    body["title"] = "\xe6\x96\xb0\xe5\xaf\xb9\xe8\xaf\x9d " + QDateTime::currentDateTime().toString("MM-dd HH:mm");
    ApiClient::instance().createConversation(body, [this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        loadConversationList();
        QJsonObject d = data["data"].toObject();
        m_activeConversationId = d["id"].toInt();
        showWelcomeScreen();
    });
}

void AIChatPage::loadConversation(int convId) {
    m_activeConversationId = convId;
    showWelcomeScreen();

    // Update title
    for (const auto &c : m_conversations) {
        if (c.id == convId) {
            m_chatTitleLabel->setText(c.title);
            break;
        }
    }

    ApiClient::instance().getConversationMessages(convId, [this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        QJsonArray msgs = data["data"].toArray();
        if (msgs.isEmpty()) return;

        m_welcomeWidget->setVisible(false);
        for (const auto &v : msgs) {
            ChatMessage msg = ChatMessage::fromJson(v.toObject());
            appendMessage(msg.role, msg.content);
        }
    });
}

void AIChatPage::deleteConversation(int convId) {
    int ret = QMessageBox::question(this,
        QString::fromUtf8("\xe5\x88\xa0\xe9\x99\xa4\xe5\xaf\xb9\xe8\xaf\x9d"),
        QString::fromUtf8("\xe7\xa1\xae\xe5\xae\x9a\xe8\xa6\x81\xe5\x88\xa0\xe9\x99\xa4\xe8\xaf\xa5\xe5\xaf\xb9\xe8\xaf\x9d\xe5\x90\x97\xef\xbc\x9f"),
        QMessageBox::Yes | QMessageBox::No);
    if (ret != QMessageBox::Yes) return;
    ApiClient::instance().deleteConversation(convId, [this, convId](bool ok, const QJsonObject &, const QString &) {
        if (!ok) return;
        if (m_activeConversationId == convId) {
            m_activeConversationId = 0;
            showWelcomeScreen();
        }
        loadConversationList();
    });
}

void AIChatPage::loadConversationList() {
    ApiClient::instance().listConversations([this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        m_conversations.clear();
        m_convoList->clear();
        QJsonArray arr = data["data"].toArray();
        for (const auto &v : arr) {
            Conversation c = Conversation::fromJson(v.toObject());
            m_conversations.append(c);

            // Create rich list item with title + time + hover buttons
            QWidget *itemWidget = new QWidget;
            itemWidget->setObjectName("convoItem");
            itemWidget->setStyleSheet("#convoItem { background: transparent; }");
            QHBoxLayout *itemOuterLayout = new QHBoxLayout(itemWidget);
            itemOuterLayout->setContentsMargins(4, 4, 4, 4);
            itemOuterLayout->setSpacing(4);

            QWidget *textCol = new QWidget;
            QVBoxLayout *itemLayout = new QVBoxLayout(textCol);
            itemLayout->setContentsMargins(0, 0, 0, 0);
            itemLayout->setSpacing(2);

            QLabel *titleLabel = new QLabel(c.title);
            titleLabel->setStyleSheet("font-size: 12px; color: #111827; font-weight: 500;");
            titleLabel->setWordWrap(false);
            titleLabel->setMaximumWidth(190);
            titleLabel->setToolTip(c.title);

            // Format time
            QString timeStr;
            QDateTime dt = QDateTime::fromString(c.updatedAt, Qt::ISODate);
            if (dt.isValid()) {
                QDateTime now = QDateTime::currentDateTime();
                if (dt.date() == now.date()) {
                    timeStr = dt.toString("HH:mm");
                } else if (dt.daysTo(now) <= 7) {
                    timeStr = dt.toString("ddd HH:mm");
                } else {
                    timeStr = dt.toString("MM-dd");
                }
            }
            QLabel *metaLabel = new QLabel(timeStr);
            metaLabel->setStyleSheet("font-size: 10px; color: #9CA3AF;");

            itemLayout->addWidget(titleLabel);
            itemLayout->addWidget(metaLabel);

            // Hover action buttons (pin + delete)
            QWidget *btnCol = new QWidget;
            btnCol->setFixedWidth(44);
            QHBoxLayout *btnLayout = new QHBoxLayout(btnCol);
            btnLayout->setContentsMargins(0, 0, 0, 0);
            btnLayout->setSpacing(2);

            QPushButton *pinBtn = new QPushButton;
            pinBtn->setFixedSize(20, 20);
            pinBtn->setIcon(QIcon(IconHelper::pin(14, QColor("#9CA3AF"))));
            pinBtn->setIconSize(QSize(14, 14));
            pinBtn->setCursor(Qt::PointingHandCursor);
            pinBtn->setToolTip(QString::fromUtf8("\xe7\xbd\xae\xe9\xa1\xb6"));
            pinBtn->setStyleSheet("QPushButton { background: transparent; border: none; border-radius: 4px; } QPushButton:hover { background: #EEF2FF; }");

            QPushButton *delBtn = new QPushButton;
            delBtn->setFixedSize(20, 20);
            delBtn->setIcon(QIcon(IconHelper::trash(14, QColor("#9CA3AF"))));
            delBtn->setIconSize(QSize(14, 14));
            delBtn->setCursor(Qt::PointingHandCursor);
            delBtn->setToolTip(QString::fromUtf8("\xe5\x88\xa0\xe9\x99\xa4"));
            delBtn->setStyleSheet("QPushButton { background: transparent; border: none; border-radius: 4px; } QPushButton:hover { background: #FEE2E2; }");

            btnLayout->addWidget(pinBtn);
            btnLayout->addWidget(delBtn);
            btnCol->setVisible(false);

            itemOuterLayout->addWidget(textCol, 1);
            itemOuterLayout->addWidget(btnCol, 0, Qt::AlignTop);

            // Show/hide buttons on hover via event filter
            int convoId = c.id;
            itemWidget->installEventFilter(this);
            itemWidget->setProperty("btnCol", QVariant::fromValue<QWidget*>(btnCol));

            connect(pinBtn, &QPushButton::clicked, [this, convoId]() {
                // Move conversation to top of list
                for (int j = 0; j < m_conversations.size(); j++) {
                    if (m_conversations[j].id == convoId) {
                        Conversation cv = m_conversations.takeAt(j);
                        m_conversations.prepend(cv);
                        loadConversationList();
                        break;
                    }
                }
            });
            connect(delBtn, &QPushButton::clicked, [this, convoId]() {
                ApiClient::instance().deleteConversation(convoId, [this](bool ok, const QJsonObject &, const QString &) {
                    if (ok) loadConversationList();
                });
            });

            QListWidgetItem *item = new QListWidgetItem;
            item->setSizeHint(QSize(0, 56));
            m_convoList->addItem(item);
            m_convoList->setItemWidget(item, itemWidget);
        }
        // Select active
        for (int i = 0; i < m_conversations.size(); i++) {
            if (m_conversations[i].id == m_activeConversationId) {
                m_convoList->setCurrentRow(i);
                break;
            }
        }
    });
}

void AIChatPage::selectDocument() {
    QDialog dlg(this);
    dlg.setWindowTitle(QString::fromUtf8("\xe9\x80\x89\xe6\x8b\xa9\xe5\x85\xb3\xe8\x81\x94\xe6\x96\x87\xe6\xa1\xa3"));
    dlg.resize(550, 420);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(20, 16, 20, 16);

    QLabel *hint = new QLabel(QString::fromUtf8("\xe5\x8b\xbe\xe9\x80\x89\xe8\xa6\x81\xe5\x85\xb3\xe8\x81\x94\xe7\x9a\x84\xe6\x96\x87\xe6\xa1\xa3\xef\xbc\x88\xe4\xbb\x85\xe5\xb7\xb2\xe6\x8f\x90\xe5\x8f\x96\xe7\x9a\x84\xe6\x96\x87\xe6\xa1\xa3\xe5\x8f\xaf\xe7\x94\xa8\xef\xbc\x89\xef\xbc\x9a"));
    hint->setStyleSheet("font-size: 13px; color: #6B7280;");
    layout->addWidget(hint);

    QTableWidget *table = new QTableWidget;
    table->setColumnCount(4);
    table->setHorizontalHeaderLabels({"\xe2\x98\x90", QString::fromUtf8("\xe6\x96\x87\xe4\xbb\xb6\xe5\x90\x8d"), QString::fromUtf8("\xe7\xb1\xbb\xe5\x9e\x8b"), QString::fromUtf8("\xe7\x8a\xb6\xe6\x80\x81")});
    table->horizontalHeader()->setStretchLastSection(true);
    table->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    table->setColumnWidth(0, 40);
    table->setColumnWidth(2, 80);
    table->setColumnWidth(3, 100);
    table->setSelectionBehavior(QAbstractItemView::SelectRows);
    table->verticalHeader()->setVisible(false);
    table->setShowGrid(false);
    table->setStyleSheet(
        "QTableWidget { border: 1px solid #E5E7EB; border-radius: 8px; font-size: 13px; }"
        "QTableWidget::item { padding: 6px 8px; border-bottom: 1px solid #F3F4F6; }"
        "QTableWidget::item:hover { background: #F9FAFB; }"
        "QHeaderView::section { background: #F9FAFB; border: none; border-bottom: 1px solid #E5E7EB; padding: 8px; font-weight: 600; color: #6B7280; }"
        "QCheckBox::indicator { width: 12px; height: 12px; border: 1.5px solid #D1D5DB; border-radius: 2px; background: white; }"
        "QCheckBox::indicator:hover { border-color: #818CF8; }"
        "QCheckBox::indicator:checked { background: #4F46E5; border-color: #4F46E5; image: url(:/check.png); }"
        "QCheckBox::indicator:disabled { background: #F3F4F6; border-color: #E5E7EB; }");
    layout->addWidget(table);

    QHBoxLayout *btnRow = new QHBoxLayout;
    QPushButton *cancelBtn = new QPushButton(QString::fromUtf8("\xe5\x8f\x96\xe6\xb6\x88"));
    cancelBtn->setMinimumHeight(36);
    QPushButton *confirmBtn = new QPushButton(QString::fromUtf8("\xe7\xa1\xae\xe8\xae\xa4\xe5\x85\xb3\xe8\x81\x94"));
    confirmBtn->setMinimumHeight(36);
    confirmBtn->setStyleSheet(
        "QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; padding: 0 20px; font-size: 13px; }"
        "QPushButton:hover { background: #3730A3; }");
    btnRow->addStretch();
    btnRow->addWidget(cancelBtn);
    btnRow->addWidget(confirmBtn);
    layout->addLayout(btnRow);

    connect(cancelBtn, &QPushButton::clicked, &dlg, &QDialog::reject);

    // Load docs
    struct DocInfo { int id; QString name; QString type; QString status; };
    QList<DocInfo> *docs = new QList<DocInfo>;
    ApiClient::instance().getSourceDocuments([this, table, docs](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        QJsonArray arr = data["data"].toArray();
        table->setRowCount(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            SourceDocument doc = SourceDocument::fromJson(arr[i].toObject());
            docs->append({doc.id, doc.fileName, doc.fileType, doc.status});

            QCheckBox *cb = new QCheckBox;
            cb->setProperty("docId", QVariant(doc.id));
            bool isParsed = (doc.status == "parsed");
            cb->setEnabled(isParsed);
            // Pre-check if already linked
            if (m_linkedDocIds.contains(doc.id)) cb->setChecked(true);

            QWidget *cbWrapper = new QWidget;
            QHBoxLayout *cbLay = new QHBoxLayout(cbWrapper);
            cbLay->setContentsMargins(0, 0, 0, 0);
            cbLay->setAlignment(Qt::AlignCenter);
            cbLay->addWidget(cb);
            table->setCellWidget(i, 0, cbWrapper);

            table->setItem(i, 1, new QTableWidgetItem(doc.fileName));
            table->setItem(i, 2, new QTableWidgetItem(doc.fileType.toUpper()));

            QString statusText, statusColor;
            if (isParsed) { statusText = QString::fromUtf8("\xe5\xb7\xb2\xe6\x8f\x90\xe5\x8f\x96"); statusColor = "#10B981"; }
            else if (doc.status == "parsing") { statusText = QString::fromUtf8("\xe6\x8f\x90\xe5\x8f\x96\xe4\xb8\xad"); statusColor = "#3B82F6"; }
            else if (doc.status == "failed") { statusText = QString::fromUtf8("\xe5\xa4\xb1\xe8\xb4\xa5"); statusColor = "#EF4444"; }
            else { statusText = doc.status; statusColor = "#9CA3AF"; }
            QLabel *statusLbl = new QLabel(statusText);
            statusLbl->setAlignment(Qt::AlignCenter);
            statusLbl->setStyleSheet(QString("font-size: 12px; color: %1; font-weight: 500;").arg(statusColor));
            QWidget *sw = new QWidget;
            QHBoxLayout *sl = new QHBoxLayout(sw);
            sl->setContentsMargins(0, 0, 0, 0);
            sl->setAlignment(Qt::AlignCenter);
            sl->addWidget(statusLbl);
            table->setCellWidget(i, 3, sw);
        }
    });

    connect(confirmBtn, &QPushButton::clicked, [this, &dlg, table, docs]() {
        m_linkedDocs.clear();
        m_linkedDocIds.clear();
        for (int i = 0; i < table->rowCount(); i++) {
            QWidget *w = table->cellWidget(i, 0);
            QCheckBox *cb = w ? w->findChild<QCheckBox*>() : nullptr;
            if (cb && cb->isChecked() && i < docs->size()) {
                int docId = (*docs)[i].id;
                m_linkedDocIds.append(docId);
                m_linkedDocs.append({docId, (*docs)[i].name});
            }
        }
        rebuildLinkedDocList();
        delete docs;
        dlg.accept();
    });

    dlg.exec();
}

bool AIChatPage::eventFilter(QObject *obj, QEvent *event) {
    // Ctrl+Enter to send from input edit
    if (obj == m_inputEdit && event->type() == QEvent::KeyPress) {
        QKeyEvent *ke = static_cast<QKeyEvent*>(event);
        if ((ke->key() == Qt::Key_Return || ke->key() == Qt::Key_Enter) && (ke->modifiers() & Qt::ControlModifier)) {
            sendMessage();
            return true;
        }
    }
    // Hover show/hide for conversation items
    QWidget *w = qobject_cast<QWidget*>(obj);
    if (w) {
        QWidget *btnCol = w->property("btnCol").value<QWidget*>();
        if (btnCol) {
            if (event->type() == QEvent::Enter) {
                btnCol->setVisible(true);
            } else if (event->type() == QEvent::Leave) {
                btnCol->setVisible(false);
            }
        }
    }
    return QWidget::eventFilter(obj, event);
}

void AIChatPage::rebuildLinkedDocList() {
    m_linkedDocList->clear();
    if (m_linkedDocs.isEmpty()) {
        m_linkedDocList->setVisible(false);
        m_linkedDocList->setFixedHeight(0);
        return;
    }
    m_linkedDocList->setVisible(true);
    for (const auto &doc : m_linkedDocs) {
        QWidget *itemW = new QWidget;
        QHBoxLayout *itemLay = new QHBoxLayout(itemW);
        itemLay->setContentsMargins(8, 4, 8, 4);
        itemLay->setSpacing(6);

        QLabel *icon = new QLabel;
        icon->setFixedSize(14, 14);
        icon->setPixmap(IconHelper::document(12, QColor("#4F46E5")));
        QLabel *nameL = new QLabel(doc.second);
        nameL->setStyleSheet("font-size: 11px; color: #111827;");
        nameL->setToolTip(doc.second);

        QPushButton *removeBtn = new QPushButton(QString::fromUtf8("\xe2\x9c\x95"));
        removeBtn->setFixedSize(18, 18);
        removeBtn->setCursor(Qt::PointingHandCursor);
        removeBtn->setStyleSheet("QPushButton { background: transparent; border: none; color: #9CA3AF; font-size: 10px; }"
                                 "QPushButton:hover { color: #EF4444; }");
        int removeId = doc.first;
        connect(removeBtn, &QPushButton::clicked, [this, removeId]() {
            m_linkedDocIds.removeAll(removeId);
            for (int i = 0; i < m_linkedDocs.size(); i++) {
                if (m_linkedDocs[i].first == removeId) {
                    m_linkedDocs.removeAt(i);
                    break;
                }
            }
            rebuildLinkedDocList();
        });

        itemLay->addWidget(icon);
        itemLay->addWidget(nameL, 1);
        itemLay->addWidget(removeBtn);

        QListWidgetItem *listItem = new QListWidgetItem;
        listItem->setSizeHint(QSize(0, 28));
        m_linkedDocList->addItem(listItem);
        m_linkedDocList->setItemWidget(listItem, itemW);
    }
    // Dynamic height: 28px per doc + 4px padding, max 120px
    int h = qMin(m_linkedDocs.size() * 30 + 4, 120);
    m_linkedDocList->setFixedHeight(h);
}

void AIChatPage::exportResult() {
    QString path = QFileDialog::getSaveFileName(this, "\xe5\xaf\xbc\xe5\x87\xba\xe5\xaf\xb9\xe8\xaf\x9d",
        QStandardPaths::writableLocation(QStandardPaths::DesktopLocation) + "/chat_export.txt",
        "\xe6\x96\x87\xe6\x9c\xac\xe6\x96\x87\xe4\xbb\xb6 (*.txt);;Markdown (*.md)");
    if (path.isEmpty()) return;
    QFile f(path);
    if (f.open(QIODevice::WriteOnly | QIODevice::Text)) {
        // Collect text from all message labels
        for (int i = 0; i < m_chatMsgLayout->count(); i++) {
            QLayoutItem *item = m_chatMsgLayout->itemAt(i);
            if (!item || !item->widget()) continue;
            QWidget *w = item->widget();
            QList<QLabel*> labels = w->findChildren<QLabel*>();
            for (QLabel *lbl : labels) {
                QString text = lbl->text();
                if (!text.isEmpty() && text.length() > 1) {
                    f.write(text.toUtf8() + "\n\n");
                }
            }
        }
        f.close();
        QMessageBox::information(this, "\xe6\x88\x90\xe5\x8a\x9f", "\xe5\xaf\xb9\xe8\xaf\x9d\xe5\xb7\xb2\xe5\xaf\xbc\xe5\x87\xba\xe5\x88\xb0: " + path);
    }
}
