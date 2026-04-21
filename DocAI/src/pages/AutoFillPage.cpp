#include "AutoFillPage.h"
#include "../network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFileDialog>
#include <QFileInfo>
#include <QStandardPaths>
#include <QGraphicsDropShadowEffect>
#include <QJsonArray>
#include <QJsonDocument>
#include <QMessageBox>
#include <QDialog>
#include <QTableWidget>
#include <QHeaderView>
#include <QCheckBox>
#include <QScrollArea>
#include <QMouseEvent>
#include <QTimer>
#include <QMimeData>
#include <algorithm>
#include "../utils/IconHelper.h"
#include "../utils/Toast.h"

AutoFillPage::AutoFillPage(QWidget *parent) : QWidget(parent) {
    setAcceptDrops(true);
    setupUI();
    loadDocStats();
}

void AutoFillPage::setupUI() {
    setObjectName("autoFillPage");
    setStyleSheet("#autoFillPage { background: white; }");
    QVBoxLayout *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(32, 24, 32, 24);
    mainLayout->setSpacing(20);

    // Steps header
    QWidget *stepsBar = new QWidget;
    stepsBar->setObjectName("stepsBar");
    stepsBar->setStyleSheet("#stepsBar { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh = new QGraphicsDropShadowEffect;
    sh->setBlurRadius(12); sh->setColor(QColor(0,0,0,8)); sh->setOffset(0, 2);
    stepsBar->setGraphicsEffect(sh);

    QHBoxLayout *stepsLayout = new QHBoxLayout(stepsBar);
    stepsLayout->setContentsMargins(32, 16, 32, 16);
    stepsLayout->setSpacing(0);

    struct StepDef { QString title; QString desc; };
    QList<StepDef> steps = {
        {"上传模板", "上传Word/Excel模板文件"},
        {"智能填充", "AI自动匹配数据库文档数据"},
        {"结果预览", "预览/下载填充结果"}
    };

    for (int i = 0; i < steps.size(); i++) {
        QWidget *stepWidget = new QWidget;
        stepWidget->setCursor(Qt::PointingHandCursor);
        stepWidget->setProperty("stepIndex", i);
        stepWidget->installEventFilter(this);
        QHBoxLayout *stepLayout = new QHBoxLayout(stepWidget);
        stepLayout->setContentsMargins(0, 0, 0, 0);
        stepLayout->setSpacing(12);

        QLabel *dot = new QLabel(QString::number(i + 1));
        dot->setFixedSize(32, 32);
        dot->setAlignment(Qt::AlignCenter);
        dot->setStyleSheet(i == 0 ?
            "background: #4F46E5; color: white; border-radius: 16px; font-size: 14px; font-weight: 700;" :
            "background: #E5E7EB; color: #9CA3AF; border-radius: 16px; font-size: 14px; font-weight: 700;");
        m_stepDots.append(dot);

        QLabel *titleLbl = new QLabel(steps[i].title);
        titleLbl->setStyleSheet(i == 0 ?
            "font-size: 14px; font-weight: 600; color: #111827;" :
            "font-size: 14px; font-weight: 600; color: #9CA3AF;");
        m_stepLabels.append(titleLbl);

        stepLayout->addWidget(dot);
        stepLayout->addWidget(titleLbl);

        stepsLayout->addWidget(stepWidget);
        if (i < steps.size() - 1) {
            stepsLayout->addSpacing(8);
            QFrame *line = new QFrame;
            line->setFrameShape(QFrame::HLine);
            line->setFixedHeight(2);
            line->setStyleSheet("background: transparent; border: none; border-top: 2px dashed #D1D5DB;");
            m_stepLines.append(line);
            stepsLayout->addWidget(line, 1);
            stepsLayout->addSpacing(8);
        }
    }

    // Step content
    m_stepStack = new QStackedWidget;
    m_stepStack->setStyleSheet("QStackedWidget { background: white; border-radius: 10px; }");
    m_stepStack->addWidget(createStep1());
    m_stepStack->addWidget(createStep2());
    m_stepStack->addWidget(createStep3());

    mainLayout->addWidget(stepsBar);
    mainLayout->addWidget(m_stepStack, 1);
}

QWidget* AutoFillPage::createStep1() {
    QScrollArea *scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setStyleSheet("QScrollArea { background: white; border: none; }");

    QWidget *content = new QWidget;
    QVBoxLayout *layout = new QVBoxLayout(content);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(16);

    // Source info card
    QWidget *sourceCard = new QWidget;
    sourceCard->setObjectName("sourceCard");
    sourceCard->setStyleSheet("#sourceCard { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh1 = new QGraphicsDropShadowEffect;
    sh1->setBlurRadius(12); sh1->setColor(QColor(0,0,0,8)); sh1->setOffset(0,2);
    sourceCard->setGraphicsEffect(sh1);
    QVBoxLayout *sourceLayout = new QVBoxLayout(sourceCard);
    sourceLayout->setContentsMargins(24, 20, 24, 20);

    QHBoxLayout *sourceInfoRow = new QHBoxLayout;
    QLabel *infoIcon = new QLabel;
    infoIcon->setFixedSize(18, 18);
    infoIcon->setPixmap(IconHelper::document(16, QColor("#4F46E5")));
    m_docCountLabel = new QLabel("数据库中已有 0 个文档可用作数据源");
    m_docCountLabel->setStyleSheet("font-size: 13px; color: #6B7280;");
    m_extractedCountLabel = new QLabel;
    m_extractedCountLabel->setStyleSheet("font-size: 12px; color: #9CA3AF;");
    m_selectSourceBtn = new QPushButton("\xe9\x80\x89\xe6\x8b\xa9\xe6\x95\xb0\xe6\x8d\xae\xe6\xba\x90\xe6\x96\x87\xe6\xa1\xa3");
    m_selectSourceBtn->setMinimumHeight(32);
    m_selectSourceBtn->setCursor(Qt::PointingHandCursor);
    m_selectSourceBtn->setStyleSheet(
        "QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 6px; font-size: 12px; padding: 0 14px; }"
        "QPushButton:hover { background: #BAE7FF; }");
    connect(m_selectSourceBtn, &QPushButton::clicked, this, &AutoFillPage::openSourceDialog);
    sourceInfoRow->addWidget(infoIcon);
    sourceInfoRow->addWidget(m_docCountLabel, 1);
    sourceInfoRow->addWidget(m_selectSourceBtn);
    sourceLayout->addLayout(sourceInfoRow);
    sourceLayout->addWidget(m_extractedCountLabel);

    // Selected sources list
    m_selectedSourceList = new QListWidget;
    m_selectedSourceList->setMaximumHeight(120);
    m_selectedSourceList->setStyleSheet(
        "QListWidget { border: 1px solid #E5E7EB; border-radius: 8px; font-size: 12px; background: white; }"
        "QListWidget::item { padding: 0; border-bottom: 1px solid #F3F4F6; }"
        "QListWidget::item:hover { background: #EEF2FF; }");
    m_selectedSourceList->setVisible(false);
    sourceLayout->addWidget(m_selectedSourceList);

    // Template upload area
    QWidget *templateCard = new QWidget;
    templateCard->setObjectName("templateCard");
    templateCard->setStyleSheet("#templateCard { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh2 = new QGraphicsDropShadowEffect;
    sh2->setBlurRadius(12); sh2->setColor(QColor(0,0,0,8)); sh2->setOffset(0,2);
    templateCard->setGraphicsEffect(sh2);
    QVBoxLayout *templateLayout = new QVBoxLayout(templateCard);
    templateLayout->setContentsMargins(24, 20, 24, 20);

    QLabel *templateTitle = new QLabel("\xe4\xb8\x8a\xe4\xbc\xa0\xe6\xa8\xa1\xe6\x9d\xbf\xe6\x96\x87\xe4\xbb\xb6");
    templateTitle->setStyleSheet("font-size: 16px; font-weight: 600; color: #111827;");
    QLabel *templateHint = new QLabel("上传模板后，系统将自动从数据库中匹配已上传的文档数据进行智能填充");
    templateHint->setStyleSheet("font-size: 12px; color: #9CA3AF;");
    templateHint->setWordWrap(true);

    m_addTemplateBtn = new QPushButton(QString::fromUtf8("\xe7\x82\xb9\xe5\x87\xbb\xe6\x88\x96\xe6\x8b\x96\xe6\x8b\xbd\xe6\xa8\xa1\xe6\x9d\xbf\xe6\x96\x87\xe4\xbb\xb6\xef\xbc\x88\xe6\x94\xaf\xe6\x8c\x81 .docx / .xlsx\xef\xbc\x89"));
    m_addTemplateBtn->setMinimumHeight(100);
    m_addTemplateBtn->setSizePolicy(QSizePolicy::Expanding, QSizePolicy::Expanding);
    m_addTemplateBtn->setCursor(Qt::PointingHandCursor);
    m_addTemplateBtn->setStyleSheet(
        "QPushButton { background: white; border: 2px dashed #C7D2FE; border-radius: 10px;"
        "  font-size: 14px; color: #818CF8; }"
        "QPushButton:hover { background: #EEF2FF; border-color: #818CF8; }");
    connect(m_addTemplateBtn, &QPushButton::clicked, this, &AutoFillPage::selectTemplates);

    m_templateFileList = new QListWidget;
    m_templateFileList->setMaximumHeight(140);
    m_templateFileList->setVisible(false);
    m_templateFileList->setStyleSheet(
        "QListWidget { border: 1px solid #E5E7EB; border-radius: 8px; font-size: 13px; background: white; }"
        "QListWidget::item { padding: 0; border-bottom: 1px solid #F3F4F6; }"
        "QListWidget::item:hover { background: #EEF2FF; }");

    m_clearTemplateBtn = new QPushButton("清空");
    m_clearTemplateBtn->setVisible(false);
    m_clearTemplateBtn->setStyleSheet("QPushButton { color: #EF4444; border: none; font-size: 12px; }");
    connect(m_clearTemplateBtn, &QPushButton::clicked, [this]() {
        m_templateFilePaths.clear();
        m_templateFileList->clear();
        m_templateFileList->setVisible(false);
        m_clearTemplateBtn->setVisible(false);
        m_addTemplateBtn->setVisible(true);
    });

    // User requirement
    QHBoxLayout *reqHeaderRow = new QHBoxLayout;
    QLabel *reqLabel = new QLabel("\xe7\x94\xa8\xe6\x88\xb7\xe9\x9c\x80\xe6\xb1\x82\xef\xbc\x88\xe5\x8f\xaf\xe9\x80\x89\xef\xbc\x89");
    reqLabel->setStyleSheet("font-size: 13px; font-weight: 600; color: #6B7280;");
    QLabel *reqDesc = new QLabel("\xe5\x8f\xaf\xe6\x8c\x87\xe5\xae\x9a\xe6\x97\xb6\xe9\x97\xb4\xe3\x80\x81\xe5\x9c\xb0\xe7\x82\xb9\xe3\x80\x81\xe7\xb1\xbb\xe5\x9e\x8b\xe7\xad\x89\xe7\xba\xa6\xe6\x9d\x9f\xef\xbc\x8c\xe7\xb3\xbb\xe7\xbb\x9f\xe5\xb0\x86\xe4\xbc\x98\xe5\x85\x88\xe5\x8c\xb9\xe9\x85\x8d\xe7\xac\xa6\xe5\x90\x88\xe6\x9d\xa1\xe4\xbb\xb6\xe7\x9a\x84\xe6\x95\xb0\xe6\x8d\xae");
    reqDesc->setStyleSheet("font-size: 11px; color: #9CA3AF;");
    reqHeaderRow->addWidget(reqLabel);
    reqHeaderRow->addStretch();
    reqHeaderRow->addWidget(reqDesc);

    // Quick examples
    QHBoxLayout *exampleRow = new QHBoxLayout;
    QLabel *exLbl = new QLabel("\xe5\xbf\xab\xe6\x8d\xb7\xe7\xa4\xba\xe4\xbe\x8b\xef\xbc\x9a");
    exLbl->setStyleSheet("font-size: 11px; color: #9CA3AF;");
    exampleRow->addWidget(exLbl);
    for (const QString &ex : {
        QString::fromUtf8("\xe4\xbb\x85\xe5\xa1\xab\xe5\x86\x99") + "2024" + QString::fromUtf8("\xe5\xb9\xb4\xe5\x8c\x97\xe4\xba\xac\xe5\xb8\x82\xe6\x95\xb0\xe6\x8d\xae"),
        QString::fromUtf8("\xe4\xbb\x85\xe5\xa1\xab\xe5\x86\x99\xe5\x85\xac\xe5\xbc\x80\xe6\x8b\x9b\xe6\xa0\x87\xe7\xb1\xbb\xe5\x9e\x8b\xe9\xa1\xb9\xe7\x9b\xae"),
        QString::fromUtf8("\xe4\xbb\x85\xe5\xa1\xab\xe5\x86\x99\xe8\xbf\x91") + "30" + QString::fromUtf8("\xe5\xa4\xa9\xe5\x86\x85\xe6\x95\xb0\xe6\x8d\xae")
    }) {
        QPushButton *exBtn = new QPushButton(ex);
        exBtn->setCursor(Qt::PointingHandCursor);
        exBtn->setStyleSheet("QPushButton { background: #F3F4F6; color: #6B7280; border: none; border-radius: 10px; padding: 2px 10px; font-size: 11px; }"
                             "QPushButton:hover { background: #EEF2FF; color: #4F46E5; }");
        connect(exBtn, &QPushButton::clicked, [this, ex]() { m_userRequirementEdit->setText(ex); });
        exampleRow->addWidget(exBtn);
    }
    exampleRow->addStretch();
    m_userRequirementEdit = new QTextEdit;
    m_userRequirementEdit->setPlaceholderText("可选：输入额外的填充需求或说明，AI 会参考此信息进行填充...");
    m_userRequirementEdit->setMaximumHeight(80);
    m_userRequirementEdit->setStyleSheet(
        "QTextEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 10px 14px; font-size: 13px; }"
        "QTextEdit:focus { border-color: #818CF8; }");

    templateLayout->addWidget(templateTitle);
    templateLayout->addWidget(templateHint);
    templateLayout->addSpacing(4);

    // Template tips card
    QWidget *tipsCard = new QWidget;
    tipsCard->setObjectName("tplTips");
    tipsCard->setStyleSheet("#tplTips { background: #FFFBEB; border: 1px solid #FDE68A; border-radius: 10px; }");
    QVBoxLayout *tipsLayout = new QVBoxLayout(tipsCard);
    tipsLayout->setContentsMargins(16, 10, 16, 10);
    tipsLayout->setSpacing(4);
    QLabel *tipsT = new QLabel("\xe6\xa8\xa1\xe6\x9d\xbf\xe4\xbd\xbf\xe7\x94\xa8\xe6\x8f\x90\xe7\xa4\xba");
    tipsT->setStyleSheet("font-size: 12px; font-weight: 600; color: #92400E;");
    tipsLayout->addWidget(tipsT);
    for (const QString &tip : {
        "Word\xe6\xa8\xa1\xe6\x9d\xbf\xef\xbc\x9a\xe5\x9c\xa8\xe9\x9c\x80\xe8\xa6\x81\xe5\xa1\xab\xe5\x86\x99\xe7\x9a\x84\xe4\xbd\x8d\xe7\xbd\xae\xe4\xbd\xbf\xe7\x94\xa8 {{\xe5\xad\x97\xe6\xae\xb5\xe5\x90\x8d}} \xe5\x8d\xa0\xe4\xbd\x8d\xe7\xac\xa6\xef\xbc\x8c\xe6\x88\x96\xe7\x95\x99\xe7\xa9\xba\xe8\xa1\xa8\xe6\xa0\xbc\xe5\x8d\x95\xe5\x85\x83\xe6\xa0\xbc",
        "Excel\xe6\xa8\xa1\xe6\x9d\xbf\xef\xbc\x9a\xe7\xac\xac\xe4\xb8\x80\xe8\xa1\x8c\xe4\xbd\x9c\xe4\xb8\xba\xe8\xa1\xa8\xe5\xa4\xb4\xef\xbc\x8c\xe4\xb8\x8b\xe6\x96\xb9\xe7\xa9\xba\xe5\x8d\x95\xe5\x85\x83\xe6\xa0\xbc\xe5\xb0\x86\xe6\xa0\xb9\xe6\x8d\xae\xe8\xa1\xa8\xe5\xa4\xb4\xe5\x90\x8d\xe7\xa7\xb0\xe8\x87\xaa\xe5\x8a\xa8\xe5\xa1\xab\xe5\x85\x85"
    }) {
        QLabel *tipLbl = new QLabel(QString("\xe2\x80\xa2 ") + tip);
        tipLbl->setWordWrap(true);
        tipLbl->setStyleSheet("font-size: 11px; color: #92400E;");
        tipsLayout->addWidget(tipLbl);
    }
    templateLayout->addWidget(tipsCard);
    templateLayout->addSpacing(4);
    templateLayout->addWidget(m_addTemplateBtn, 1);
    templateLayout->addWidget(m_templateFileList);
    templateLayout->addWidget(m_clearTemplateBtn, 0, Qt::AlignRight);
    templateLayout->addSpacing(8);
    templateLayout->addLayout(reqHeaderRow);
    templateLayout->addLayout(exampleRow);
    templateLayout->addWidget(m_userRequirementEdit);

    // Next button
    m_nextBtn = new QPushButton("\xe5\xbc\x80\xe5\xa7\x8b\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe5\x85\x85");
    m_nextBtn->setMinimumHeight(48);
    m_nextBtn->setCursor(Qt::PointingHandCursor);
    m_nextBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #818CF8, stop:1 #4F46E5);"
        "  color: white; border: none; border-radius: 6px; font-size: 16px; font-weight: 600; }"
        "QPushButton:hover { background: #4F46E5; }"
        "QPushButton:disabled { background: #C7D2FE; }");
    connect(m_nextBtn, &QPushButton::clicked, this, &AutoFillPage::startFilling);

    layout->addWidget(sourceCard);
    layout->addWidget(templateCard, 1);
    layout->addWidget(m_nextBtn);

    scroll->setWidget(content);
    return scroll;
}

QWidget* AutoFillPage::createStep2() {
    QWidget *page = new QWidget;
    page->setStyleSheet("background: white;");
    QVBoxLayout *layout = new QVBoxLayout(page);
    layout->setAlignment(Qt::AlignCenter);

    QWidget *card = new QWidget;
    card->setObjectName("fillCard");
    card->setFixedWidth(480);
    card->setStyleSheet("#fillCard { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh = new QGraphicsDropShadowEffect;
    sh->setBlurRadius(20); sh->setColor(QColor(0,0,0,12)); sh->setOffset(0,4);
    card->setGraphicsEffect(sh);

    QVBoxLayout *cardLayout = new QVBoxLayout(card);
    cardLayout->setContentsMargins(40, 40, 40, 40);
    cardLayout->setAlignment(Qt::AlignCenter);
    cardLayout->setSpacing(20);

    QLabel *icon = new QLabel;
    icon->setFixedSize(48, 48);
    icon->setPixmap(IconHelper::robot(40, QColor("#4F46E5")));
    icon->setAlignment(Qt::AlignCenter);

    m_fillStatusLabel = new QLabel("正在智能填充...");
    m_fillStatusLabel->setStyleSheet("font-size: 18px; font-weight: 600; color: #111827;");
    m_fillStatusLabel->setAlignment(Qt::AlignCenter);

    m_fillProgress = new QProgressBar;
    m_fillProgress->setRange(0, 0); // indeterminate
    m_fillProgress->setFixedHeight(6);
    m_fillProgress->setTextVisible(false);
    m_fillProgress->setStyleSheet(
        "QProgressBar { background: #D1D5DB; border: none; border-radius: 3px; }"
        "QProgressBar::chunk { background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #818CF8, stop:1 #4F46E5); border-radius: 3px; }");

    m_fillDetailLabel = new QLabel("AI \xe6\xad\xa3\xe5\x9c\xa8\xe5\x88\x86\xe6\x9e\x90\xe6\xa8\xa1\xe6\x9d\xbf\xe5\xb9\xb6\xe5\x8c\xb9\xe9\x85\x8d\xe6\x96\x87\xe6\xa1\xa3\xe6\x95\xb0\xe6\x8d\xae\xef\xbc\x8c\xe8\xaf\xb7\xe7\xa8\x8d\xe5\x80\x99...");
    m_fillDetailLabel->setStyleSheet("font-size: 13px; color: #9CA3AF;");
    m_fillDetailLabel->setAlignment(Qt::AlignCenter);
    m_fillDetailLabel->setWordWrap(true);

    // Sub-steps
    QWidget *subStepsWidget = new QWidget;
    QHBoxLayout *subStepsLayout = new QHBoxLayout(subStepsWidget);
    subStepsLayout->setSpacing(16);
    subStepsLayout->setContentsMargins(0, 8, 0, 0);
    QStringList phaseNames = {
        "\xe8\xaf\xbb\xe5\x8f\x96\xe6\x95\xb0\xe6\x8d\xae\xe5\xba\x93\xe6\x96\x87\xe6\xa1\xa3",
        "\xe5\x88\x86\xe6\x9e\x90\xe6\xa8\xa1\xe6\x9d\xbf\xe7\xbb\x93\xe6\x9e\x84",
        "AI\xe6\x8f\x90\xe5\x8f\x96\xe5\x8c\xb9\xe9\x85\x8d\xe6\x95\xb0\xe6\x8d\xae",
        "\xe5\xa1\xab\xe5\x85\x85\xe6\xa8\xa1\xe6\x9d\xbf"
    };
    for (int i = 0; i < phaseNames.size(); i++) {
        QLabel *phaseLbl = new QLabel(phaseNames[i]);
        phaseLbl->setAlignment(Qt::AlignCenter);
        phaseLbl->setStyleSheet(i == 0 ?
            "font-size: 11px; font-weight: 600; color: #4F46E5; background: #EEF2FF; border-radius: 6px; padding: 4px 8px;" :
            "font-size: 11px; color: #9CA3AF; background: #F3F4F6; border-radius: 6px; padding: 4px 8px;");
        subStepsLayout->addWidget(phaseLbl);
    }

    cardLayout->addWidget(icon);
    cardLayout->addWidget(m_fillStatusLabel);
    cardLayout->addWidget(m_fillProgress);
    cardLayout->addWidget(m_fillDetailLabel);
    cardLayout->addWidget(subStepsWidget);

    layout->addWidget(card);
    return page;
}

QWidget* AutoFillPage::createStep3() {
    QWidget *page = new QWidget;
    page->setStyleSheet("background: white;");
    QVBoxLayout *layout = new QVBoxLayout(page);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(12);

    // Title + action buttons row
    QHBoxLayout *actionRow = new QHBoxLayout;
    QLabel *title = new QLabel("\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe8\xa1\xa8\xe5\xb7\xb2\xe5\xae\x8c\xe6\x88\x90");
    title->setStyleSheet("font-size: 20px; font-weight: 700; color: #111827;");

    QPushButton *dlAllBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd\xe5\x85\xa8\xe9\x83\xa8\xe7\xbb\x93\xe6\x9e\x9c");
    dlAllBtn->setMinimumHeight(36);
    dlAllBtn->setCursor(Qt::PointingHandCursor);
    dlAllBtn->setStyleSheet("QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; padding: 0 16px; font-size: 13px; font-weight: 600; }"
                            "QPushButton:hover { background: #3730A3; }");
    connect(dlAllBtn, &QPushButton::clicked, [this]() {
        for (const auto &r : m_results) { if (r.status == "success" && r.templateId > 0) downloadResult(r.templateId); }
    });
    QPushButton *continueBtn = new QPushButton("\xe7\xbb\xa7\xe7\xbb\xad\xe5\xa1\xab\xe8\xa1\xa8");
    continueBtn->setMinimumHeight(36);
    continueBtn->setCursor(Qt::PointingHandCursor);
    continueBtn->setStyleSheet("QPushButton { background: white; color: #4F46E5; border: 2px solid #C7D2FE; border-radius: 6px; padding: 0 16px; font-size: 13px; font-weight: 600; }"
                               "QPushButton:hover { background: #EEF2FF; }");
    connect(continueBtn, &QPushButton::clicked, [this]() { goToStep(0); });
    actionRow->addWidget(title);
    actionRow->addStretch();
    actionRow->addWidget(dlAllBtn);
    actionRow->addWidget(continueBtn);

    // Result summary banner
    m_resultSummary = new QWidget;
    m_resultSummary->setObjectName("resSummary");
    m_resultSummary->setStyleSheet("#resSummary { background: #EEF2FF; border: 1px solid #C7D2FE; border-radius: 10px; }");
    QHBoxLayout *summaryLayout = new QHBoxLayout(m_resultSummary);
    summaryLayout->setContentsMargins(16, 10, 16, 10);
    QLabel *checkIcon = new QLabel("\xe2\x9c\x85");
    checkIcon->setStyleSheet("font-size: 16px;");
    m_resultSummaryLabel = new QLabel;
    m_resultSummaryLabel->setStyleSheet("font-size: 13px; color: #4F46E5;");
    summaryLayout->addWidget(checkIcon);
    summaryLayout->addWidget(m_resultSummaryLabel, 1);

    // Compact result file list
    m_resultList = new QListWidget;
    m_resultList->setMaximumHeight(120);
    m_resultList->setStyleSheet(
        "QListWidget { border: 1px solid #E5E7EB; border-radius: 8px; background: white; }"
        "QListWidget::item { padding: 0; border-bottom: 1px solid #F3F4F6; }"
        "QListWidget::item:hover { background: #F9FAFB; }");

    // Tab widget for detail / analysis / preview
    m_resultTabs = new QTabWidget;
    m_resultTabs->setStyleSheet(
        "QTabWidget::pane { border: 1px solid #E5E7EB; border-radius: 8px; background: white; }"
        "QTabBar::tab { padding: 8px 20px; font-size: 13px; font-weight: 500; color: #6B7280; border: none; border-bottom: 2px solid transparent; }"
        "QTabBar::tab:selected { color: #4F46E5; border-bottom: 2px solid #4F46E5; }"
        "QTabBar::tab:hover { color: #4F46E5; background: #F5F3FF; }");

    // Detail tab
    QWidget *detailPage = new QWidget;
    QVBoxLayout *detailLayout = new QVBoxLayout(detailPage);
    detailLayout->setContentsMargins(0, 8, 0, 0);
    m_detailTable = new QTableWidget;
    m_detailTable->setColumnCount(5);
    m_detailTable->setHorizontalHeaderLabels({"\xe5\xad\x97\xe6\xae\xb5\xe5\x90\x8d\xe7\xa7\xb0", "\xe5\xa1\xab\xe5\x85\x85\xe5\x80\xbc", "\xe7\xbd\xae\xe4\xbf\xa1\xe5\xba\xa6", "\xe5\x86\xb3\xe7\xad\x96\xe6\x96\xb9\xe5\xbc\x8f", "\xe5\x86\xb3\xe7\xad\x96\xe5\x8e\x9f\xe5\x9b\xa0"});
    m_detailTable->horizontalHeader()->setStretchLastSection(true);
    m_detailTable->horizontalHeader()->setSectionResizeMode(0, QHeaderView::ResizeToContents);
    m_detailTable->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    m_detailTable->horizontalHeader()->setSectionResizeMode(2, QHeaderView::Fixed);
    m_detailTable->horizontalHeader()->setSectionResizeMode(3, QHeaderView::Fixed);
    m_detailTable->setColumnWidth(2, 140);
    m_detailTable->setColumnWidth(3, 100);
    m_detailTable->verticalHeader()->setVisible(false);
    m_detailTable->setShowGrid(false);
    m_detailTable->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_detailTable->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_detailTable->setAlternatingRowColors(true);
    m_detailTable->setStyleSheet(
        "QTableWidget { border: none; font-size: 12px; }"
        "QTableWidget::item { padding: 6px 8px; border-bottom: 1px solid #F3F4F6; }"
        "QTableWidget::item:hover { background: #F9FAFB; }"
        "QHeaderView::section { background: #F9FAFB; border: none; border-bottom: 2px solid #E5E7EB; padding: 8px; font-weight: 600; color: #374151; font-size: 12px; }");
    detailLayout->addWidget(m_detailTable);
    m_resultTabs->addTab(detailPage, "\xe5\xa1\xab\xe5\x85\x85\xe6\x98\x8e\xe7\xbb\x86");

    // Analysis tab
    m_analysisWidget = new QWidget;
    QScrollArea *analysisScroll = new QScrollArea;
    analysisScroll->setWidgetResizable(true);
    analysisScroll->setFrameShape(QFrame::NoFrame);
    analysisScroll->setWidget(m_analysisWidget);
    m_resultTabs->addTab(analysisScroll, "\xe5\x88\x86\xe6\x9e\x90\xe7\xbb\x93\xe6\x9e\x9c");

    // Preview tab (download-focused since Qt can't render docx/xlsx inline)
    QWidget *previewPage = new QWidget;
    QVBoxLayout *previewLayout = new QVBoxLayout(previewPage);
    previewLayout->setContentsMargins(24, 24, 24, 24);
    previewLayout->setAlignment(Qt::AlignTop);
    QLabel *previewHint = new QLabel("\xe7\x82\xb9\xe5\x87\xbb\xe4\xb8\x8a\xe6\x96\xb9\xe6\x96\x87\xe4\xbb\xb6\xe5\x88\x97\xe8\xa1\xa8\xe4\xb8\xad\xe7\x9a\x84\xe3\x80\x8c\xe4\xb8\x8b\xe8\xbd\xbd\xe3\x80\x8d\xe6\x8c\x89\xe9\x92\xae\xe4\xbf\x9d\xe5\xad\x98\xe7\xbb\x93\xe6\x9e\x9c\xe6\x96\x87\xe4\xbb\xb6\xef\xbc\x8c\xe7\x84\xb6\xe5\x90\x8e\xe4\xbd\xbf\xe7\x94\xa8 Office \xe6\x89\x93\xe5\xbc\x80\xe9\xa2\x84\xe8\xa7\x88\xe3\x80\x82\xe6\x88\x96\xe8\x80\x85\xe7\x82\xb9\xe5\x87\xbb\xe4\xb8\x8b\xe6\x96\xb9\xe6\x8c\x89\xe9\x92\xae\xe5\xbf\xab\xe9\x80\x9f\xe6\x89\x93\xe5\xbc\x80\xe3\x80\x82");
    previewHint->setWordWrap(true);
    previewHint->setStyleSheet("font-size: 13px; color: #6B7280;");
    QPushButton *openFileBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd\xe5\xb9\xb6\xe6\x89\x93\xe5\xbc\x80\xe7\xac\xac\xe4\xb8\x80\xe4\xb8\xaa\xe7\xbb\x93\xe6\x9e\x9c");
    openFileBtn->setMinimumHeight(44);
    openFileBtn->setCursor(Qt::PointingHandCursor);
    openFileBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0,y1:0,x2:1,y2:0, stop:0 #818CF8, stop:1 #4F46E5);"
        "  color: white; border: none; border-radius: 8px; font-size: 14px; font-weight: 600; padding: 0 24px; }"
        "QPushButton:hover { background: #4F46E5; }");
    connect(openFileBtn, &QPushButton::clicked, [this]() {
        for (const auto &r : m_results) {
            if (r.status == "success" && r.templateId > 0) {
                downloadResult(r.templateId);
                return;
            }
        }
    });
    previewLayout->addWidget(previewHint);
    previewLayout->addSpacing(16);
    previewLayout->addWidget(openFileBtn);
    previewLayout->addStretch();
    m_resultTabs->addTab(previewPage, "\xe7\xbb\x93\xe6\x9e\x9c\xe9\xa2\x84\xe8\xa7\x88");

    layout->addLayout(actionRow);
    layout->addWidget(m_resultSummary);
    layout->addWidget(m_resultList);
    layout->addWidget(m_resultTabs, 1);

    return page;
}

void AutoFillPage::goToStep(int step) {
    m_currentStep = step;
    m_stepStack->setCurrentIndex(step);
    for (int i = 0; i < m_stepDots.size(); i++) {
        QLabel *dot = qobject_cast<QLabel*>(m_stepDots[i]);
        if (!dot) continue;
        if (i < step) {
            dot->setText(QString::fromUtf8("\xe2\x9c\x93"));
            dot->setStyleSheet("background: #10B981; color: white; border-radius: 16px; font-size: 14px; font-weight: 700;");
            m_stepLabels[i]->setStyleSheet("font-size: 14px; font-weight: 600; color: #10B981;");
        } else if (i == step) {
            dot->setText(QString::number(i + 1));
            dot->setStyleSheet("background: #4F46E5; color: white; border-radius: 16px; font-size: 14px; font-weight: 700;");
            m_stepLabels[i]->setStyleSheet("font-size: 14px; font-weight: 600; color: #111827;");
        } else {
            dot->setText(QString::number(i + 1));
            dot->setStyleSheet("background: #D1D5DB; color: #9CA3AF; border-radius: 16px; font-size: 14px; font-weight: 700;");
            m_stepLabels[i]->setStyleSheet("font-size: 14px; font-weight: 600; color: #9CA3AF;");
        }
    }
    // Update step connector lines color
    for (int i = 0; i < m_stepLines.size(); i++) {
        if (i < step) {
            m_stepLines[i]->setStyleSheet("background: transparent; border: none; border-top: 2px solid #10B981;");
        } else {
            m_stepLines[i]->setStyleSheet("background: transparent; border: none; border-top: 2px dashed #D1D5DB;");
        }
    }
}

void AutoFillPage::loadDocStats() {
    ApiClient::instance().getSourceDocuments([this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        m_allDocs.clear();
        QJsonArray arr = data["data"].toArray();
        int extracted = 0;
        for (const auto &v : arr) {
            SourceDocument d = SourceDocument::fromJson(v.toObject());
            m_allDocs.append(d);
            if (d.status == "parsed") extracted++;
        }
        m_docCountLabel->setText(QString("数据库中已有 %1 个文档可用作数据源（其中 %2 个已成功提取内容）").arg(m_allDocs.size()).arg(extracted));
    });
}

void AutoFillPage::selectTemplates() {
    QStringList files = QFileDialog::getOpenFileNames(this, "选择模板文件",
        QStandardPaths::writableLocation(QStandardPaths::DocumentsLocation),
        "模板文件 (*.docx *.xlsx)");
    if (files.isEmpty()) return;
    for (const QString &f : files) {
        if (!m_templateFilePaths.contains(f))
            m_templateFilePaths.append(f);
    }
    rebuildTemplateList();
}

void AutoFillPage::rebuildTemplateList() {
    m_templateFileList->clear();
    for (int idx = 0; idx < m_templateFilePaths.size(); idx++) {
        QFileInfo fi(m_templateFilePaths[idx]);
        QWidget *itemW = new QWidget;
        QHBoxLayout *itemLay = new QHBoxLayout(itemW);
        itemLay->setContentsMargins(8, 4, 8, 4);
        itemLay->setSpacing(8);

        QLabel *icon = new QLabel;
        icon->setFixedSize(16, 16);
        icon->setPixmap(IconHelper::document(14, fi.suffix() == "xlsx" ? QColor("#10B981") : QColor("#3B82F6")));
        QLabel *nameL = new QLabel(fi.fileName());
        nameL->setStyleSheet("font-size: 12px; color: #111827;");
        QLabel *sizeL = new QLabel(QString::number(fi.size() / 1024.0, 'f', 1) + " KB");
        sizeL->setStyleSheet("font-size: 10px; color: #9CA3AF;");
        QLabel *extL = new QLabel(fi.suffix().toUpper());
        extL->setStyleSheet("font-size: 10px; color: #4F46E5; background: #EEF2FF; border-radius: 3px; padding: 1px 6px;");

        QPushButton *removeBtn = new QPushButton(QString::fromUtf8("\xe2\x9c\x95"));
        removeBtn->setFixedSize(20, 20);
        removeBtn->setCursor(Qt::PointingHandCursor);
        removeBtn->setStyleSheet("QPushButton { background: transparent; border: none; color: #9CA3AF; font-size: 11px; }"
                                 "QPushButton:hover { color: #EF4444; }");
        QString removePath = m_templateFilePaths[idx];
        connect(removeBtn, &QPushButton::clicked, [this, removePath]() {
            m_templateFilePaths.removeAll(removePath);
            if (m_templateFilePaths.isEmpty()) {
                m_templateFileList->clear();
                m_templateFileList->setVisible(false);
                m_clearTemplateBtn->setVisible(false);
                m_addTemplateBtn->setVisible(true);
            } else {
                // Rebuild list without opening file dialog
                rebuildTemplateList();
            }
        });

        itemLay->addWidget(icon);
        itemLay->addWidget(nameL, 1);
        itemLay->addWidget(sizeL);
        itemLay->addWidget(extL);
        itemLay->addWidget(removeBtn);

        QListWidgetItem *listItem = new QListWidgetItem;
        listItem->setSizeHint(QSize(0, 32));
        m_templateFileList->addItem(listItem);
        m_templateFileList->setItemWidget(listItem, itemW);
    }
    m_templateFileList->setVisible(true);
    m_clearTemplateBtn->setVisible(true);
    m_addTemplateBtn->setVisible(false);
}

void AutoFillPage::openSourceDialog() {
    QDialog dlg(this);
    dlg.setWindowTitle("选择数据源文档");
    dlg.resize(550, 420);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(20, 16, 20, 16);

    QLabel *hint = new QLabel("勾选要用作数据源的文档（仅已提取的文档可用）：");
    hint->setStyleSheet("font-size: 13px; color: #6B7280;");
    layout->addWidget(hint);

    QTableWidget *table = new QTableWidget;
    table->setColumnCount(4);
    table->setHorizontalHeaderLabels({"\xe2\x98\x90", "\xe6\x96\x87\xe4\xbb\xb6\xe5\x90\x8d", "\xe7\xb1\xbb\xe5\x9e\x8b", "\xe7\x8a\xb6\xe6\x80\x81"});
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

    // Select-all / deselect-all button row
    QHBoxLayout *selectAllRow = new QHBoxLayout;
    QPushButton *selectAllBtn = new QPushButton(QString::fromUtf8("\xe5\x85\xa8\xe9\x80\x89"));
    selectAllBtn->setMinimumHeight(28);
    selectAllBtn->setStyleSheet(
        "QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 6px; padding: 0 14px; font-size: 12px; }"
        "QPushButton:hover { background: #C7D2FE; }");
    QPushButton *deselectAllBtn = new QPushButton(QString::fromUtf8("\xe5\x8f\x96\xe6\xb6\x88\xe5\x85\xa8\xe9\x80\x89"));
    deselectAllBtn->setMinimumHeight(28);
    deselectAllBtn->setStyleSheet(
        "QPushButton { background: #F3F4F6; color: #6B7280; border: none; border-radius: 6px; padding: 0 14px; font-size: 12px; }"
        "QPushButton:hover { background: #E5E7EB; }");
    selectAllRow->addWidget(selectAllBtn);
    selectAllRow->addWidget(deselectAllBtn);
    selectAllRow->addStretch();
    layout->addLayout(selectAllRow);

    connect(selectAllBtn, &QPushButton::clicked, [table]() {
        for (int i = 0; i < table->rowCount(); i++) {
            QCheckBox *cb = qobject_cast<QCheckBox*>(table->cellWidget(i, 0));
            if (cb && cb->isEnabled()) cb->setChecked(true);
        }
    });
    connect(deselectAllBtn, &QPushButton::clicked, [table]() {
        for (int i = 0; i < table->rowCount(); i++) {
            QCheckBox *cb = qobject_cast<QCheckBox*>(table->cellWidget(i, 0));
            if (cb) cb->setChecked(false);
        }
    });

    table->setRowCount(m_allDocs.size());
    for (int i = 0; i < m_allDocs.size(); i++) {
        QCheckBox *cb = new QCheckBox;
        cb->setProperty("docId", QVariant(m_allDocs[i].id));
        if (m_selectedSourceIds.contains(m_allDocs[i].id)) cb->setChecked(true);
        bool isParsed = m_allDocs[i].status == "parsed";
        cb->setEnabled(isParsed);
        table->setCellWidget(i, 0, cb);
        table->setItem(i, 1, new QTableWidgetItem(m_allDocs[i].fileName));
        table->setItem(i, 2, new QTableWidgetItem(m_allDocs[i].fileType.toUpper()));
        QString statusText;
        QString statusColor;
        if (isParsed) { statusText = "\xe5\xb7\xb2\xe6\x8f\x90\xe5\x8f\x96"; statusColor = "#10B981"; }
        else if (m_allDocs[i].status == "parsing") { statusText = "\xe6\x8f\x90\xe5\x8f\x96\xe4\xb8\xad"; statusColor = "#3B82F6"; }
        else if (m_allDocs[i].status == "failed") { statusText = "\xe5\xa4\xb1\xe8\xb4\xa5"; statusColor = "#EF4444"; }
        else { statusText = m_allDocs[i].status; statusColor = "#9CA3AF"; }
        QLabel *statusLbl = new QLabel(statusText);
        statusLbl->setAlignment(Qt::AlignCenter);
        statusLbl->setStyleSheet(QString("font-size: 12px; color: %1; font-weight: 500;").arg(statusColor));
        QWidget *statusWrapper = new QWidget;
        QHBoxLayout *statusWrapLay = new QHBoxLayout(statusWrapper);
        statusWrapLay->setContentsMargins(0, 0, 0, 0);
        statusWrapLay->setAlignment(Qt::AlignCenter);
        statusWrapLay->addWidget(statusLbl);
        table->setCellWidget(i, 3, statusWrapper);
    }
    layout->addWidget(table);

    QHBoxLayout *btnRow = new QHBoxLayout;
    QPushButton *cancelBtn = new QPushButton("取消");
    cancelBtn->setMinimumHeight(36);
    QPushButton *confirmBtn = new QPushButton("确认选择");
    confirmBtn->setMinimumHeight(36);
    confirmBtn->setStyleSheet("QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; padding: 0 20px; font-size: 13px; }"
                              "QPushButton:hover { background: #3730A3; }");
    btnRow->addStretch();
    btnRow->addWidget(cancelBtn);
    btnRow->addWidget(confirmBtn);
    layout->addLayout(btnRow);

    connect(cancelBtn, &QPushButton::clicked, &dlg, &QDialog::reject);
    connect(confirmBtn, &QPushButton::clicked, [this, &dlg, table]() {
        m_selectedSourceIds.clear();
        m_selectedSourceList->clear();
        for (int i = 0; i < table->rowCount(); i++) {
            QCheckBox *cb = qobject_cast<QCheckBox*>(table->cellWidget(i, 0));
            if (cb && cb->isChecked()) {
                int docId = cb->property("docId").toInt();
                m_selectedSourceIds.append(docId);

                QWidget *itemW = new QWidget;
                QHBoxLayout *itemLay = new QHBoxLayout(itemW);
                itemLay->setContentsMargins(8, 4, 8, 4);
                itemLay->setSpacing(8);

                QLabel *icon = new QLabel;
                icon->setFixedSize(16, 16);
                icon->setPixmap(IconHelper::document(14, QColor("#4F46E5")));
                QLabel *nameL = new QLabel(m_allDocs[i].fileName);
                nameL->setStyleSheet("font-size: 12px; color: #111827;");
                QLabel *typeL = new QLabel(m_allDocs[i].fileType.toUpper());
                typeL->setStyleSheet("font-size: 10px; color: #4F46E5; background: #EEF2FF; border-radius: 3px; padding: 1px 6px;");

                QPushButton *removeBtn = new QPushButton(QString::fromUtf8("\xe2\x9c\x95"));
                removeBtn->setFixedSize(20, 20);
                removeBtn->setCursor(Qt::PointingHandCursor);
                removeBtn->setStyleSheet("QPushButton { background: transparent; border: none; color: #9CA3AF; font-size: 11px; }"
                                         "QPushButton:hover { color: #EF4444; }");
                int removeDocId = docId;
                connect(removeBtn, &QPushButton::clicked, [this, removeDocId]() {
                    m_selectedSourceIds.removeAll(removeDocId);
                    // Rebuild list
                    for (int r = 0; r < m_selectedSourceList->count(); r++) {
                        QListWidgetItem *li = m_selectedSourceList->item(r);
                        if (li->data(Qt::UserRole).toInt() == removeDocId) {
                            delete m_selectedSourceList->takeItem(r);
                            break;
                        }
                    }
                    m_selectedSourceList->setVisible(!m_selectedSourceIds.isEmpty());
                });

                itemLay->addWidget(icon);
                itemLay->addWidget(nameL, 1);
                itemLay->addWidget(typeL);
                itemLay->addWidget(removeBtn);

                QListWidgetItem *listItem = new QListWidgetItem;
                listItem->setData(Qt::UserRole, docId);
                listItem->setSizeHint(QSize(0, 32));
                m_selectedSourceList->addItem(listItem);
                m_selectedSourceList->setItemWidget(listItem, itemW);
            }
        }
        m_selectedSourceList->setVisible(!m_selectedSourceIds.isEmpty());
        dlg.accept();
    });

    dlg.exec();
}

void AutoFillPage::startFilling() {
    if (m_templateFilePaths.isEmpty()) {
        Toast::showMessage(this, QString::fromUtf8("\xe8\xaf\xb7\xe5\x85\x88\xe9\x80\x89\xe6\x8b\xa9\xe6\xa8\xa1\xe6\x9d\xbf\xe6\x96\x87\xe4\xbb\xb6"), Toast::Warning);
        return;
    }

    goToStep(1);
    m_results.clear();
    m_decisions.clear();
    m_downloadedBlobs.clear();
    m_fillTimer.start();
    m_fillStatusLabel->setText("\xe6\xad\xa3\xe5\x9c\xa8\xe4\xb8\x8a\xe4\xbc\xa0\xe6\xa8\xa1\xe6\x9d\xbf\xe5\xb9\xb6\xe5\xa1\xab\xe5\x85\x85...");
    m_fillDetailLabel->setText(QString("\xe5\x85\xb1 %1 \xe4\xb8\xaa\xe6\xa8\xa1\xe6\x9d\xbf\xef\xbc\x8cAI \xe6\xad\xa3\xe5\x9c\xa8\xe5\xa4\x84\xe7\x90\x86...").arg(m_templateFilePaths.size()));

    int *completedCount = new int(0);
    int totalCount = m_templateFilePaths.size();
    QString userReq = m_userRequirementEdit->toPlainText().trimmed();

    for (int i = 0; i < m_templateFilePaths.size(); i++) {
        QString filePath = m_templateFilePaths[i];
        QFileInfo fi(filePath);
        QString fileName = fi.fileName();

        // Step 1: Upload template
        ApiClient::instance().uploadTemplateFile(filePath, nullptr,
            [this, fileName, completedCount, totalCount, userReq](bool ok, const QJsonObject &data, const QString &err) {
                if (!ok) {
                    TemplateResult r; r.templateId = 0; r.fileName = fileName; r.status = "\xe4\xb8\x8a\xe4\xbc\xa0\xe5\xa4\xb1\xe8\xb4\xa5: " + err;
                    m_results.append(r);
                    (*completedCount)++;
                    if (*completedCount >= totalCount) { delete completedCount; showResults(); }
                    return;
                }
                int templateId = data["data"].toObject()["id"].toInt();
                m_fillDetailLabel->setText("\xe6\xad\xa3\xe5\x9c\xa8\xe8\xa7\xa3\xe6\x9e\x90\xe6\xa8\xa1\xe6\x9d\xbf: " + fileName);

                // Step 2: Parse template slots
                ApiClient::instance().parseTemplateSlots(templateId,
                    [this, templateId, fileName, completedCount, totalCount, userReq](bool ok2, const QJsonObject &, const QString &err2) {
                        if (!ok2) {
                            TemplateResult r; r.templateId = templateId; r.fileName = fileName;
                            r.status = "\xe8\xa7\xa3\xe6\x9e\x90\xe5\xa4\xb1\xe8\xb4\xa5: " + err2;
                            m_results.append(r);
                            (*completedCount)++;
                            if (*completedCount >= totalCount) { delete completedCount; showResults(); }
                            return;
                        }
                        m_fillDetailLabel->setText("\xe6\xad\xa3\xe5\x9c\xa8\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe5\x85\x85: " + fileName);

                        // Step 3: Fill template
                        ApiClient::instance().fillTemplate(templateId, m_selectedSourceIds, userReq,
                            [this, templateId, fileName, completedCount, totalCount](bool ok3, const QJsonObject &, const QString &err3) {
                                if (!ok3) {
                                    TemplateResult r; r.templateId = templateId; r.fileName = fileName;
                                    r.status = "\xe5\xa1\xab\xe5\x85\x85\xe5\xa4\xb1\xe8\xb4\xa5: " + err3;
                                    m_results.append(r);
                                    (*completedCount)++;
                                    if (*completedCount >= totalCount) { delete completedCount; showResults(); }
                                    return;
                                }
                                m_fillDetailLabel->setText("\xe6\xad\xa3\xe5\x9c\xa8\xe4\xb8\x8b\xe8\xbd\xbd\xe7\xbb\x93\xe6\x9e\x9c: " + fileName);

                                // Step 4: Download result
                                ApiClient::instance().downloadTemplateResult(templateId,
                                    [this, templateId, fileName, completedCount, totalCount](bool ok4, const QByteArray &blob, const QString &fn, const QString &) {
                                        TemplateResult r;
                                        r.templateId = templateId;
                                        r.fileName = fileName;
                                        r.status = "success";
                                        m_results.append(r);
                                        if (ok4 && !blob.isEmpty()) {
                                            m_downloadedBlobs[templateId] = qMakePair(blob, fn.isEmpty() ? fileName : fn);
                                        }
                                        (*completedCount)++;
                                        if (*completedCount >= totalCount) { delete completedCount; showResults(); }
                                    });
                            });
                    });
            });
    }
}

void AutoFillPage::showResults() {
    qint64 elapsedMs = m_fillTimer.elapsed();
    goToStep(2);

    m_resultList->clear();
    int successCount = 0;
    for (const auto &r : m_results) { if (r.status == "success") successCount++; }
    int srcCount = m_selectedSourceIds.isEmpty() ? m_allDocs.size() : m_selectedSourceIds.size();
    double secs = elapsedMs / 1000.0;
    m_resultSummaryLabel->setText(QString("\xe6\x88\x90\xe5\x8a\x9f\xe5\xa1\xab\xe5\x85\x85 %1/%2 \xe4\xb8\xaa\xe6\xa8\xa1\xe6\x9d\xbf\xef\xbc\x8c\xe6\x95\xb0\xe6\x8d\xae\xe6\xba\x90 %3 \xe4\xb8\xaa\xe6\x96\x87\xe6\xa1\xa3\xef\xbc\x8c\xe8\x80\x97\xe6\x97\xb6 %4 \xe7\xa7\x92")
        .arg(successCount).arg(m_results.size()).arg(srcCount).arg(secs, 0, 'f', 1));

    for (int i = 0; i < m_results.size(); i++) {
        const TemplateResult &r = m_results[i];
        QWidget *itemWidget = new QWidget;
        QHBoxLayout *itemLayout = new QHBoxLayout(itemWidget);
        itemLayout->setContentsMargins(12, 6, 12, 6);
        itemLayout->setSpacing(10);

        QLabel *icon = new QLabel;
        icon->setFixedSize(18, 18);
        if (r.status == "success")
            icon->setPixmap(IconHelper::document(14, QColor("#10B981")));
        else
            icon->setPixmap(IconHelper::closeX(14, QColor("#EF4444")));
        QLabel *name = new QLabel(r.fileName);
        name->setStyleSheet("font-size: 13px; font-weight: 500; color: #111827;");
        QLabel *status = new QLabel(r.status == "success" ? "\xe5\xa1\xab\xe5\x85\x85\xe6\x88\x90\xe5\x8a\x9f" : r.status);
        status->setStyleSheet(r.status == "success" ?
            "font-size: 11px; color: #10B981;" : "font-size: 11px; color: #EF4444;");

        itemLayout->addWidget(icon);
        itemLayout->addWidget(name);
        itemLayout->addWidget(status);
        itemLayout->addStretch();

        if (r.status == "success" && r.templateId > 0) {
            QPushButton *dlBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd");
            dlBtn->setFixedHeight(28);
            dlBtn->setCursor(Qt::PointingHandCursor);
            dlBtn->setStyleSheet("QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 4px; padding: 0 12px; font-size: 11px; }"
                                 "QPushButton:hover { background: #C7D2FE; }");
            int tid = r.templateId;
            connect(dlBtn, &QPushButton::clicked, [this, tid]() { downloadResult(tid); });

            QPushButton *emailBtn = new QPushButton("\xe9\x82\xae\xe4\xbb\xb6");
            emailBtn->setFixedHeight(28);
            emailBtn->setCursor(Qt::PointingHandCursor);
            emailBtn->setStyleSheet("QPushButton { background: #FEF3C7; color: #D97706; border: none; border-radius: 4px; padding: 0 12px; font-size: 11px; }"
                                    "QPushButton:hover { background: #FDE68A; }");
            connect(emailBtn, &QPushButton::clicked, [this, tid]() { sendResultEmail(tid); });

            QPushButton *auditBtn = new QPushButton("\xe8\xaf\xa6\xe6\x83\x85");
            auditBtn->setFixedHeight(28);
            auditBtn->setCursor(Qt::PointingHandCursor);
            auditBtn->setStyleSheet("QPushButton { background: #D1FAE5; color: #059669; border: none; border-radius: 4px; padding: 0 12px; font-size: 11px; }"
                                    "QPushButton:hover { background: #A7F3D0; }");
            connect(auditBtn, &QPushButton::clicked, [this, tid]() { viewAudit(tid); });

            itemLayout->addWidget(dlBtn);
            itemLayout->addWidget(emailBtn);
            itemLayout->addWidget(auditBtn);
        }

        QListWidgetItem *listItem = new QListWidgetItem;
        listItem->setSizeHint(QSize(0, 36));
        m_resultList->addItem(listItem);
        m_resultList->setItemWidget(listItem, itemWidget);
    }

    // Load decisions for tabs
    loadDecisions();
}

void AutoFillPage::downloadResult(int templateId) {
    ApiClient::instance().downloadTemplateResult(templateId,
        [this](bool ok, const QByteArray &data, const QString &filename, const QString &err) {
            if (!ok) { Toast::showMessage(this, QString::fromUtf8("\xe4\xb8\x8b\xe8\xbd\xbd\xe5\xa4\xb1\xe8\xb4\xa5: ") + err, Toast::Error); return; }
            QString savePath = QFileDialog::getSaveFileName(this, "保存结果",
                QStandardPaths::writableLocation(QStandardPaths::DesktopLocation) + "/" + filename);
            if (savePath.isEmpty()) return;
            QFile f(savePath);
            if (f.open(QIODevice::WriteOnly)) { f.write(data); f.close(); }
        });
}

void AutoFillPage::sendResultEmail(int templateId) {
    QDialog dlg(this);
    dlg.setWindowTitle("发送结果邮件");
    dlg.setFixedSize(400, 160);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(24, 20, 24, 20);
    QLineEdit *emailEdit = new QLineEdit;
    emailEdit->setPlaceholderText("请输入收件人邮箱");
    emailEdit->setMinimumHeight(38);
    emailEdit->setStyleSheet("QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 0 12px; font-size: 13px; }");
    QPushButton *sendBtn = new QPushButton("\xe5\x8f\x91\xe9\x80\x81\xe9\x82\xae\xe4\xbb\xb6");
    sendBtn->setMinimumHeight(40);
    sendBtn->setStyleSheet("QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; font-size: 14px; }"
                           "QPushButton:hover { background: #3730A3; }");
    layout->addWidget(new QLabel("收件人邮箱:"));
    layout->addWidget(emailEdit);
    layout->addWidget(sendBtn);

    connect(sendBtn, &QPushButton::clicked, [this, &dlg, emailEdit, templateId]() {
        ApiClient::instance().sendTemplateResultEmail(templateId, emailEdit->text().trimmed(),
            [this, &dlg](bool ok, const QJsonObject &, const QString &err) {
                if (ok) { Toast::showMessage(this, QString::fromUtf8("\xe9\x82\xae\xe4\xbb\xb6\xe5\x8f\x91\xe9\x80\x81\xe6\x88\x90\xe5\x8a\x9f"), Toast::Success); dlg.accept(); }
                else Toast::showMessage(this, QString::fromUtf8("\xe5\x8f\x91\xe9\x80\x81\xe5\xa4\xb1\xe8\xb4\xa5: ") + err, Toast::Error);
            });
    });
    dlg.exec();
}

void AutoFillPage::viewAudit(int templateId) {
    QDialog dlg(this);
    dlg.setWindowTitle("\xe5\xa1\xab\xe5\x85\x85\xe8\xaf\xa6\xe6\x83\x85");
    dlg.resize(800, 500);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(16, 16, 16, 16);

    QLabel *loadingLbl = new QLabel("\xe6\xad\xa3\xe5\x9c\xa8\xe5\x8a\xa0\xe8\xbd\xbd\xe5\xa1\xab\xe5\x85\x85\xe8\xaf\xa6\xe6\x83\x85...");
    loadingLbl->setAlignment(Qt::AlignCenter);
    loadingLbl->setStyleSheet("font-size: 14px; color: #6B7280;");
    layout->addWidget(loadingLbl);

    QTableWidget *table = new QTableWidget;
    table->setColumnCount(5);
    table->setHorizontalHeaderLabels({"\xe5\xad\x97\xe6\xae\xb5\xe5\x90\x8d\xe7\xa7\xb0", "\xe5\xa1\xab\xe5\x85\x85\xe5\x80\xbc", "\xe7\xbd\xae\xe4\xbf\xa1\xe5\xba\xa6", "\xe5\x86\xb3\xe7\xad\x96\xe6\x96\xb9\xe5\xbc\x8f", "\xe5\x86\xb3\xe7\xad\x96\xe5\x8e\x9f\xe5\x9b\xa0"});
    table->horizontalHeader()->setStretchLastSection(true);
    table->horizontalHeader()->setSectionResizeMode(0, QHeaderView::ResizeToContents);
    table->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    table->horizontalHeader()->setSectionResizeMode(2, QHeaderView::Fixed);
    table->horizontalHeader()->setSectionResizeMode(3, QHeaderView::Fixed);
    table->setColumnWidth(2, 140);
    table->setColumnWidth(3, 100);
    table->verticalHeader()->setVisible(false);
    table->setShowGrid(false);
    table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    table->setAlternatingRowColors(true);
    table->setStyleSheet(
        "QTableWidget { border: none; font-size: 12px; }"
        "QTableWidget::item { padding: 6px 8px; border-bottom: 1px solid #F3F4F6; }"
        "QHeaderView::section { background: #F9FAFB; border: none; border-bottom: 2px solid #E5E7EB; padding: 8px; font-weight: 600; color: #374151; }");
    table->setVisible(false);
    layout->addWidget(table);

    ApiClient::instance().getTemplateDecisions(templateId, [this, loadingLbl, table](bool ok, const QJsonObject &data, const QString &err) {
        loadingLbl->setVisible(false);
        if (!ok) {
            loadingLbl->setText("\xe5\x8a\xa0\xe8\xbd\xbd\xe5\xa4\xb1\xe8\xb4\xa5: " + err);
            loadingLbl->setVisible(true);
            return;
        }
        QJsonArray arr = data["data"].toArray();
        table->setRowCount(arr.size());
        table->setVisible(true);
        for (int i = 0; i < arr.size(); i++) {
            QJsonObject obj = arr[i].toObject();
            table->setItem(i, 0, new QTableWidgetItem(obj["slotLabel"].toString()));
            QString val = obj["finalValue"].toString();
            table->setItem(i, 1, new QTableWidgetItem(val.isEmpty() ? "\xe2\x80\x94" : val));
            double conf = obj["finalConfidence"].toDouble();
            QProgressBar *confBar = new QProgressBar;
            confBar->setRange(0, 100);
            confBar->setValue(qRound(conf * 100));
            confBar->setTextVisible(true);
            confBar->setFormat(QString::number(conf * 100, 'f', 1) + "%");
            confBar->setFixedHeight(20);
            QString color = conf >= 0.85 ? "#10B981" : (conf >= 0.70 ? "#3B82F6" : (conf >= 0.50 ? "#F59E0B" : "#EF4444"));
            confBar->setStyleSheet(QString(
                "QProgressBar { background: #F3F4F6; border: none; border-radius: 4px; font-size: 10px; }"
                "QProgressBar::chunk { background: %1; border-radius: 4px; }").arg(color));
            table->setCellWidget(i, 2, confBar);
            QString mode = obj["decisionMode"].toString();
            QLabel *modeLbl = new QLabel(decisionModeLabel(mode));
            modeLbl->setAlignment(Qt::AlignCenter);
            QString mc = decisionModeColor(mode);
            modeLbl->setStyleSheet(QString("font-size: 10px; font-weight: 500; color: %1; background: #F3F4F6; border-radius: 4px; padding: 2px 8px;")
                .arg(mc));
            table->setCellWidget(i, 3, modeLbl);
            table->setItem(i, 4, new QTableWidgetItem(obj["reason"].toString()));
        }
    });

    dlg.exec();
}

void AutoFillPage::loadDecisions() {
    m_decisions.clear();
    int *remaining = new int(0);
    QList<int> tids;
    for (const auto &r : m_results) {
        if (r.status == "success" && r.templateId > 0) { tids.append(r.templateId); (*remaining)++; }
    }
    if (tids.isEmpty()) { buildDetailTab(); buildAnalysisTab(); return; }

    for (int tid : tids) {
        ApiClient::instance().getTemplateDecisions(tid,
            [this, remaining](bool ok, const QJsonObject &data, const QString &) {
                if (ok) {
                    QJsonArray arr = data["data"].toArray();
                    for (const auto &v : arr) {
                        QJsonObject obj = v.toObject();
                        FillDecision d;
                        d.slotLabel = obj["slotLabel"].toString();
                        d.finalValue = obj["finalValue"].toString();
                        d.finalConfidence = obj["finalConfidence"].toDouble();
                        d.decisionMode = obj["decisionMode"].toString();
                        d.reason = obj["reason"].toString();
                        m_decisions.append(d);
                    }
                }
                (*remaining)--;
                if (*remaining <= 0) {
                    delete remaining;
                    buildDetailTab();
                    buildAnalysisTab();
                }
            });
    }
}

void AutoFillPage::buildDetailTab() {
    m_detailTable->setRowCount(0);
    m_detailTable->setRowCount(m_decisions.size());

    for (int i = 0; i < m_decisions.size(); i++) {
        const FillDecision &d = m_decisions[i];
        m_detailTable->setItem(i, 0, new QTableWidgetItem(d.slotLabel));
        QTableWidgetItem *valItem = new QTableWidgetItem(d.finalValue.isEmpty() ? "\xe2\x80\x94" : d.finalValue);
        m_detailTable->setItem(i, 1, valItem);

        // Confidence progress bar
        QProgressBar *confBar = new QProgressBar;
        confBar->setRange(0, 100);
        confBar->setValue(qRound(d.finalConfidence * 100));
        confBar->setTextVisible(true);
        confBar->setFormat(QString::number(d.finalConfidence * 100, 'f', 1) + "%");
        confBar->setFixedHeight(20);
        QString color = d.finalConfidence >= 0.85 ? "#10B981" : (d.finalConfidence >= 0.70 ? "#3B82F6" : (d.finalConfidence >= 0.50 ? "#F59E0B" : "#EF4444"));
        confBar->setStyleSheet(QString(
            "QProgressBar { background: #F3F4F6; border: none; border-radius: 4px; font-size: 10px; }"
            "QProgressBar::chunk { background: %1; border-radius: 4px; }").arg(color));
        m_detailTable->setCellWidget(i, 2, confBar);

        // Decision mode label
        QLabel *modeLbl = new QLabel(decisionModeLabel(d.decisionMode));
        modeLbl->setAlignment(Qt::AlignCenter);
        QString mc = decisionModeColor(d.decisionMode);
        modeLbl->setStyleSheet(QString("font-size: 10px; font-weight: 500; color: %1; background: #F3F4F6; border-radius: 4px; padding: 2px 8px;")
            .arg(mc));
        m_detailTable->setCellWidget(i, 3, modeLbl);

        m_detailTable->setItem(i, 4, new QTableWidgetItem(d.reason));
    }
}

void AutoFillPage::buildAnalysisTab() {
    // Clear previous analysis content
    QLayout *oldLayout = m_analysisWidget->layout();
    if (oldLayout) {
        QLayoutItem *child;
        while ((child = oldLayout->takeAt(0)) != nullptr) {
            if (child->widget()) delete child->widget();
            delete child;
        }
        delete oldLayout;
    }

    QVBoxLayout *layout = new QVBoxLayout(m_analysisWidget);
    layout->setContentsMargins(24, 20, 24, 20);
    layout->setSpacing(20);

    int total = m_decisions.size();
    if (total == 0) {
        QLabel *emptyLbl = new QLabel("\xe6\x9a\x82\xe6\x97\xa0\xe5\xa1\xab\xe5\x85\x85\xe6\x95\xb0\xe6\x8d\xae");
        emptyLbl->setAlignment(Qt::AlignCenter);
        emptyLbl->setStyleSheet("font-size: 14px; color: #9CA3AF;");
        layout->addWidget(emptyLbl);
        layout->addStretch();
        return;
    }

    // Calculate statistics
    int filledCount = 0;
    double totalConf = 0;
    int highConf = 0, midConf = 0, lowConf = 0, noFill = 0;
    QMap<QString, int> modeCounts;
    for (const FillDecision &d : m_decisions) {
        if (!d.finalValue.isEmpty() && d.finalValue != "\xe2\x80\x94") {
            filledCount++;
            totalConf += d.finalConfidence;
            if (d.finalConfidence >= 0.85) highConf++;
            else if (d.finalConfidence >= 0.70) midConf++;
            else lowConf++;
        } else {
            noFill++;
        }
        modeCounts[d.decisionMode] = modeCounts.value(d.decisionMode, 0) + 1;
    }
    double avgConf = filledCount > 0 ? totalConf / filledCount : 0;
    double fillRate = total > 0 ? (double)filledCount / total : 0;

    // Summary stat cards
    QHBoxLayout *statsRow = new QHBoxLayout;
    statsRow->setSpacing(16);
    auto makeStatCard = [](const QString &value, const QString &label, const QString &color, const QString &bgColor) -> QWidget* {
        QWidget *card = new QWidget;
        card->setMinimumHeight(80);
        card->setStyleSheet(QString("background: %1; border-radius: 10px;").arg(bgColor));
        QVBoxLayout *cl = new QVBoxLayout(card);
        cl->setAlignment(Qt::AlignCenter);
        cl->setSpacing(4);
        QLabel *vl = new QLabel(value);
        vl->setAlignment(Qt::AlignCenter);
        vl->setStyleSheet(QString("font-size: 24px; font-weight: 700; color: %1;").arg(color));
        QLabel *ll = new QLabel(label);
        ll->setAlignment(Qt::AlignCenter);
        ll->setStyleSheet("font-size: 12px; color: #6B7280;");
        cl->addWidget(vl);
        cl->addWidget(ll);
        return card;
    };
    statsRow->addWidget(makeStatCard(QString::number(total), "\xe6\x80\xbb\xe5\xad\x97\xe6\xae\xb5\xe6\x95\xb0", "#4F46E5", "#EEF2FF"));
    statsRow->addWidget(makeStatCard(QString::number(avgConf * 100, 'f', 1) + "%", "\xe5\xb9\xb3\xe5\x9d\x87\xe7\xbd\xae\xe4\xbf\xa1\xe5\xba\xa6", "#10B981", "#D1FAE5"));
    statsRow->addWidget(makeStatCard(QString::number(fillRate * 100, 'f', 1) + "%", "\xe5\xb7\xb2\xe5\xa1\xab\xe5\x85\x85\xe7\x8e\x87", "#3B82F6", "#DBEAFE"));
    layout->addLayout(statsRow);

    // Confidence distribution section
    QLabel *confTitle = new QLabel("\xe7\xbd\xae\xe4\xbf\xa1\xe5\xba\xa6\xe5\x88\x86\xe5\xb8\x83");
    confTitle->setStyleSheet("font-size: 14px; font-weight: 600; color: #111827;");
    layout->addWidget(confTitle);

    struct ConfLevel { QString name; int count; QString color; };
    QList<ConfLevel> levels = {
        {"\xe9\xab\x98\xe7\xbd\xae\xe4\xbf\xa1 (\xe2\x89\xa585%)", highConf, "#10B981"},
        {"\xe4\xb8\xad\xe7\xbd\xae\xe4\xbf\xa1 (70-85%)", midConf, "#3B82F6"},
        {"\xe4\xbd\x8e\xe7\xbd\xae\xe4\xbf\xa1 (<70%)", lowConf, "#F59E0B"},
        {"\xe6\x9c\xaa\xe5\xa1\xab\xe5\x86\x99", noFill, "#9CA3AF"}
    };
    for (const ConfLevel &cl : levels) {
        QHBoxLayout *barRow = new QHBoxLayout;
        barRow->setSpacing(12);
        QLabel *nameLbl = new QLabel(cl.name);
        nameLbl->setFixedWidth(120);
        nameLbl->setStyleSheet("font-size: 12px; color: #374151;");
        QProgressBar *bar = new QProgressBar;
        bar->setRange(0, total);
        bar->setValue(cl.count);
        bar->setFixedHeight(16);
        bar->setTextVisible(false);
        bar->setStyleSheet(QString(
            "QProgressBar { background: #F3F4F6; border: none; border-radius: 4px; }"
            "QProgressBar::chunk { background: %1; border-radius: 4px; }").arg(cl.color));
        double pct = total > 0 ? (double)cl.count / total * 100.0 : 0;
        QLabel *countLbl = new QLabel(QString("%1 (%2%)").arg(cl.count).arg(pct, 0, 'f', 1));
        countLbl->setFixedWidth(80);
        countLbl->setStyleSheet("font-size: 11px; color: #6B7280;");
        barRow->addWidget(nameLbl);
        barRow->addWidget(bar, 1);
        barRow->addWidget(countLbl);
        layout->addLayout(barRow);
    }

    // Decision mode distribution section
    layout->addSpacing(8);
    QLabel *modeTitle = new QLabel("\xe5\x86\xb3\xe7\xad\x96\xe6\x96\xb9\xe5\xbc\x8f\xe5\x88\x86\xe5\xb8\x83");
    modeTitle->setStyleSheet("font-size: 14px; font-weight: 600; color: #111827;");
    layout->addWidget(modeTitle);

    // Sort modes by count descending
    QList<QPair<QString, int>> sortedModes;
    for (auto it = modeCounts.constBegin(); it != modeCounts.constEnd(); ++it)
        sortedModes.append(qMakePair(it.key(), it.value()));
    std::sort(sortedModes.begin(), sortedModes.end(), [](const QPair<QString,int> &a, const QPair<QString,int> &b) { return a.second > b.second; });

    for (const auto &pair : sortedModes) {
        QHBoxLayout *modeRow = new QHBoxLayout;
        modeRow->setSpacing(10);
        QLabel *dot = new QLabel("\xe2\x97\x8f");
        dot->setFixedWidth(16);
        dot->setStyleSheet(QString("font-size: 10px; color: %1;").arg(decisionModeColor(pair.first)));
        QLabel *mLabel = new QLabel(decisionModeLabel(pair.first));
        mLabel->setFixedWidth(100);
        mLabel->setStyleSheet("font-size: 12px; color: #374151;");
        QProgressBar *mBar = new QProgressBar;
        mBar->setRange(0, total);
        mBar->setValue(pair.second);
        mBar->setFixedHeight(14);
        mBar->setTextVisible(false);
        mBar->setStyleSheet(QString(
            "QProgressBar { background: #F3F4F6; border: none; border-radius: 3px; }"
            "QProgressBar::chunk { background: %1; border-radius: 3px; }").arg(decisionModeColor(pair.first)));
        double pct = total > 0 ? (double)pair.second / total * 100.0 : 0;
        QLabel *cntLbl = new QLabel(QString("%1 (%2%)").arg(pair.second).arg(pct, 0, 'f', 1));
        cntLbl->setFixedWidth(80);
        cntLbl->setStyleSheet("font-size: 11px; color: #6B7280;");
        modeRow->addWidget(dot);
        modeRow->addWidget(mLabel);
        modeRow->addWidget(mBar, 1);
        modeRow->addWidget(cntLbl);
        layout->addLayout(modeRow);
    }

    layout->addStretch();
}

QString AutoFillPage::decisionModeLabel(const QString &mode) {
    if (mode == "rule_only") return "\xe8\xa7\x84\xe5\x88\x99\xe5\x8c\xb9\xe9\x85\x8d";
    if (mode == "rule_plus_llm") return "\xe8\xa7\x84\xe5\x88\x99+AI";
    if (mode == "statistical_aggregation") return "\xe7\xbb\x9f\xe8\xae\xa1\xe8\x81\x9a\xe5\x90\x88";
    if (mode == "direct_table_copy") return "\xe8\xa1\xa8\xe6\xa0\xbc\xe5\xa4\x8d\xe5\x88\xb6";
    if (mode == "greedy_fallback") return "\xe6\x99\xba\xe8\x83\xbd\xe5\x8c\xb9\xe9\x85\x8d";
    if (mode == "llm_fallback") return "AI\xe6\x8f\x90\xe5\x8f\x96";
    if (mode == "fallback_blank") return "\xe5\xbe\x85\xe8\xa1\xa5\xe5\x85\x85";
    if (mode == "forced_requirement") return "\xe9\x9c\x80\xe6\xb1\x82\xe5\x8c\xb9\xe9\x85\x8d";
    return mode;
}

QString AutoFillPage::decisionModeColor(const QString &mode) {
    if (mode == "rule_only") return "#3B82F6";
    if (mode == "rule_plus_llm") return "#8B5CF6";
    if (mode == "statistical_aggregation") return "#10B981";
    if (mode == "direct_table_copy") return "#06B6D4";
    if (mode == "greedy_fallback") return "#F97316";
    if (mode == "llm_fallback") return "#F59E0B";
    if (mode == "fallback_blank") return "#9CA3AF";
    if (mode == "forced_requirement") return "#EC4899";
    return "#6B7280";
}

bool AutoFillPage::eventFilter(QObject *obj, QEvent *event) {
    if (event->type() == QEvent::MouseButtonRelease) {
        QWidget *w = qobject_cast<QWidget*>(obj);
        if (w) {
            bool ok = false;
            int stepIdx = w->property("stepIndex").toInt(&ok);
            if (ok && stepIdx <= m_currentStep) {
                goToStep(stepIdx);
            }
        }
    }
    return QWidget::eventFilter(obj, event);
}

void AutoFillPage::dragEnterEvent(QDragEnterEvent *event) {
    if (m_currentStep != 0) return;
    if (event->mimeData()->hasUrls()) {
        for (const QUrl &url : event->mimeData()->urls()) {
            QString path = url.toLocalFile();
            if (path.endsWith(".docx", Qt::CaseInsensitive) || path.endsWith(".xlsx", Qt::CaseInsensitive)) {
                event->acceptProposedAction();
                m_addTemplateBtn->setStyleSheet(
                    "QPushButton { background: #EEF2FF; border: 2px dashed #818CF8; border-radius: 10px;"
                    "  font-size: 14px; color: #4F46E5; }");
                return;
            }
        }
    }
}

void AutoFillPage::dropEvent(QDropEvent *event) {
    m_addTemplateBtn->setStyleSheet(
        "QPushButton { background: white; border: 2px dashed #C7D2FE; border-radius: 10px;"
        "  font-size: 14px; color: #818CF8; }"
        "QPushButton:hover { background: #EEF2FF; border-color: #818CF8; }");

    if (!event->mimeData()->hasUrls()) return;
    QStringList validFiles;
    for (const QUrl &url : event->mimeData()->urls()) {
        QString path = url.toLocalFile();
        if (path.endsWith(".docx", Qt::CaseInsensitive) || path.endsWith(".xlsx", Qt::CaseInsensitive)) {
            validFiles.append(path);
        }
    }
    if (validFiles.isEmpty()) return;
    event->acceptProposedAction();

    // Add the dropped files to template list (same logic as selectTemplates)
    for (const QString &path : validFiles) {
        if (!m_templateFilePaths.contains(path))
            m_templateFilePaths.append(path);
    }
    rebuildTemplateList();
}
