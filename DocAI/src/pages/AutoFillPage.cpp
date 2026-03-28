#include "AutoFillPage.h"
#include "../network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFileDialog>
#include <QFileInfo>
#include <QStandardPaths>
#include <QGraphicsDropShadowEffect>
#include <QJsonArray>
#include <QMessageBox>
#include <QDialog>
#include <QTableWidget>
#include <QHeaderView>
#include <QCheckBox>
#include <QScrollArea>
#include <QMouseEvent>
#include <QTimer>
#include <QMimeData>
#include "../utils/IconHelper.h"
#include "../utils/Toast.h"

AutoFillPage::AutoFillPage(QWidget *parent) : QWidget(parent) {
    setAcceptDrops(true);
    setupUI();
    loadDocStats();
}

void AutoFillPage::setupUI() {
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
    QVBoxLayout *layout = new QVBoxLayout(page);
    layout->setContentsMargins(0, 0, 0, 0);
    layout->setSpacing(16);

    QLabel *title = new QLabel("\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe8\xa1\xa8\xe5\xb7\xb2\xe5\xae\x8c\xe6\x88\x90");
    title->setStyleSheet("font-size: 20px; font-weight: 700; color: #111827;");

    // Result summary banner
    m_resultSummary = new QWidget;
    m_resultSummary->setObjectName("resSummary");
    m_resultSummary->setStyleSheet("#resSummary { background: #EEF2FF; border: 1px solid #C7D2FE; border-radius: 10px; }");
    QHBoxLayout *summaryLayout = new QHBoxLayout(m_resultSummary);
    summaryLayout->setContentsMargins(16, 10, 16, 10);
    m_resultSummaryLabel = new QLabel;
    m_resultSummaryLabel->setStyleSheet("font-size: 13px; color: #4F46E5;");
    summaryLayout->addWidget(m_resultSummaryLabel, 1);

    // Top action buttons
    QHBoxLayout *actionRow = new QHBoxLayout;
    QPushButton *dlAllBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd\xe7\xbb\x93\xe6\x9e\x9c");
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

    m_resultList = new QListWidget;
    m_resultList->setStyleSheet(
        "QListWidget { border: none; background: transparent; }"
        "QListWidget::item { background: white; border-radius: 10px; margin: 6px 0; padding: 16px; }"
        "QListWidget::item:hover { background: #F9FAFB; }");

    QPushButton *backBtn = new QPushButton("\xe7\xbb\xa7\xe7\xbb\xad\xe5\xa1\xab\xe5\x85\x85\xe6\x96\xb0\xe6\xa8\xa1\xe6\x9d\xbf");
    backBtn->setMinimumHeight(44);
    backBtn->setCursor(Qt::PointingHandCursor);
    backBtn->setStyleSheet(
        "QPushButton { background: white; color: #4F46E5; border: 2px solid #C7D2FE; border-radius: 6px; font-size: 14px; font-weight: 600; }"
        "QPushButton:hover { background: #EEF2FF; }");
    connect(backBtn, &QPushButton::clicked, [this]() { goToStep(0); });

    layout->addLayout(actionRow);
    layout->addWidget(m_resultSummary);
    layout->addWidget(m_resultList, 1);
    layout->addWidget(backBtn);

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
    m_fillStatusLabel->setText("正在上传模板并填充...");
    m_fillDetailLabel->setText(QString("共 %1 个模板，AI 正在处理...").arg(m_templateFilePaths.size()));

    int *completedCount = new int(0);
    int totalCount = m_templateFilePaths.size();
    QString userReq = m_userRequirementEdit->toPlainText().trimmed();

    for (int i = 0; i < m_templateFilePaths.size(); i++) {
        QString filePath = m_templateFilePaths[i];
        QFileInfo fi(filePath);
        QString fileName = fi.fileName();

        // Upload template
        ApiClient::instance().uploadTemplateFile(filePath, nullptr,
            [this, fileName, completedCount, totalCount, userReq](bool ok, const QJsonObject &data, const QString &err) {
                if (!ok) {
                    TemplateResult r;
                    r.templateId = 0;
                    r.fileName = fileName;
                    r.status = "上传失败: " + err;
                    m_results.append(r);
                    (*completedCount)++;
                    if (*completedCount >= totalCount) { delete completedCount; goToStep(2); showResults(); }
                    return;
                }
                int templateId = data["data"].toObject()["id"].toInt();
                m_fillDetailLabel->setText("正在填充: " + fileName);

                // Fill template
                ApiClient::instance().fillTemplate(templateId, m_selectedSourceIds, userReq,
                    [this, templateId, fileName, completedCount, totalCount](bool ok2, const QJsonObject &, const QString &err2) {
                        TemplateResult r;
                        r.templateId = templateId;
                        r.fileName = fileName;
                        r.status = ok2 ? "success" : ("填充失败: " + err2);
                        m_results.append(r);
                        (*completedCount)++;
                        if (*completedCount >= totalCount) { delete completedCount; goToStep(2); showResults(); }
                    });
            });
    }
}

void AutoFillPage::showResults() {
    m_resultList->clear();
    int successCount = 0;
    for (const auto &r : m_results) { if (r.status == "success") successCount++; }
    int srcCount = m_selectedSourceIds.isEmpty() ? m_allDocs.size() : m_selectedSourceIds.size();
    m_resultSummaryLabel->setText(QString("\xe6\x88\x90\xe5\x8a\x9f\xe5\xa1\xab\xe5\x85\x85 %1/%2 \xe4\xb8\xaa\xe6\xa8\xa1\xe6\x9d\xbf\xef\xbc\x8c\xe6\x95\xb0\xe6\x8d\xae\xe6\xba\x90 %3 \xe4\xb8\xaa\xe6\x96\x87\xe6\xa1\xa3")
        .arg(successCount).arg(m_results.size()).arg(srcCount));
    for (int i = 0; i < m_results.size(); i++) {
        const TemplateResult &r = m_results[i];

        QWidget *itemWidget = new QWidget;
        QHBoxLayout *itemLayout = new QHBoxLayout(itemWidget);
        itemLayout->setContentsMargins(16, 12, 16, 12);
        itemLayout->setSpacing(12);

        QLabel *icon = new QLabel;
        icon->setFixedSize(20, 20);
        if (r.status == "success")
            icon->setPixmap(IconHelper::document(16, QColor("#10B981")));
        else
            icon->setPixmap(IconHelper::closeX(16, QColor("#EF4444")));
        icon->setAlignment(Qt::AlignCenter);
        QLabel *name = new QLabel(r.fileName);
        name->setStyleSheet("font-size: 14px; font-weight: 500; color: #111827;");
        QLabel *status = new QLabel(r.status == "success" ? "填充成功" : r.status);
        status->setStyleSheet(r.status == "success" ?
            "font-size: 12px; color: #10B981;" : "font-size: 12px; color: #EF4444;");

        itemLayout->addWidget(icon);
        QVBoxLayout *infoLayout = new QVBoxLayout;
        infoLayout->addWidget(name);
        infoLayout->addWidget(status);
        itemLayout->addLayout(infoLayout, 1);

        if (r.status == "success" && r.templateId > 0) {
            QPushButton *dlBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd");
            dlBtn->setMinimumHeight(32);
            dlBtn->setCursor(Qt::PointingHandCursor);
            dlBtn->setStyleSheet("QPushButton { background: #EEF2FF; color: #4F46E5; border: none; border-radius: 6px; padding: 0 14px; font-size: 12px; }"
                                 "QPushButton:hover { background: #BAE7FF; }");
            int tid = r.templateId;
            connect(dlBtn, &QPushButton::clicked, [this, tid]() { downloadResult(tid); });

            QPushButton *emailBtn = new QPushButton("\xe9\x82\xae\xe4\xbb\xb6");
            emailBtn->setMinimumHeight(32);
            emailBtn->setStyleSheet("QPushButton { background: #FEF3C7; color: #F59E0B; border: none; border-radius: 6px; padding: 0 14px; font-size: 12px; }"
                                    "QPushButton:hover { background: #FFFB8F; }");
            connect(emailBtn, &QPushButton::clicked, [this, tid]() { sendResultEmail(tid); });

            QPushButton *auditBtn = new QPushButton("\xe8\xaf\xa6\xe6\x83\x85");
            auditBtn->setMinimumHeight(32);
            auditBtn->setStyleSheet("QPushButton { background: #D1FAE5; color: #10B981; border: none; border-radius: 6px; padding: 0 14px; font-size: 12px; }"
                                    "QPushButton:hover { background: #D9F7BE; }");
            connect(auditBtn, &QPushButton::clicked, [this, tid]() { viewAudit(tid); });

            itemLayout->addWidget(dlBtn);
            itemLayout->addWidget(emailBtn);
            itemLayout->addWidget(auditBtn);
        }

        QListWidgetItem *listItem = new QListWidgetItem;
        listItem->setSizeHint(itemWidget->sizeHint() + QSize(0, 12));
        m_resultList->addItem(listItem);
        m_resultList->setItemWidget(listItem, itemWidget);
    }
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
    dlg.setWindowTitle("填充详情");
    dlg.resize(600, 400);
    dlg.setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);

    QTextEdit *viewer = new QTextEdit;
    viewer->setReadOnly(true);
    viewer->setStyleSheet("QTextEdit { border: none; padding: 16px; font-size: 13px; }");
    viewer->setPlainText("正在加载填充详情...");
    layout->addWidget(viewer);

    ApiClient::instance().getTemplateAudit(templateId, [viewer](bool ok, const QJsonObject &data, const QString &err) {
        if (!ok) { viewer->setPlainText("加载失败: " + err); return; }
        QJsonDocument doc(data["data"].toObject());
        viewer->setPlainText(doc.toJson(QJsonDocument::Indented));
    });

    dlg.exec();
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
