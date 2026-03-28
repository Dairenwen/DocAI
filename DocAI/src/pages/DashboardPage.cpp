#include "DashboardPage.h"
#include "../network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QScrollArea>
#include <QPushButton>
#include <QMouseEvent>
#include <QVariant>
#include <QJsonArray>
#include <QGraphicsDropShadowEffect>
#include <QFrame>
#include "../utils/IconHelper.h"

static QGraphicsDropShadowEffect* makeShadow(QObject *parent) {
    auto *e = new QGraphicsDropShadowEffect(parent);
    e->setColor(QColor(0, 0, 0, 15));
    e->setBlurRadius(12);
    e->setOffset(0, 2);
    return e;
}

DashboardPage::DashboardPage(QWidget *parent) : QWidget(parent) {
    setupUI();
    refreshStats();
}

void DashboardPage::setupUI() {
    QVBoxLayout *outer = new QVBoxLayout(this);
    outer->setContentsMargins(0, 0, 0, 0);

    QScrollArea *scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setStyleSheet("QScrollArea { background: white; border: none; }"
                          "QScrollBar:vertical { width: 6px; background: transparent; }"
                          "QScrollBar::handle:vertical { background: #D1D5DB; border-radius: 3px; }"
                          "QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }");

    QWidget *content = new QWidget;
    QVBoxLayout *layout = new QVBoxLayout(content);
    layout->setContentsMargins(32, 24, 32, 32);
    layout->setSpacing(24);

    // ===== Compact Action Bar (replaces large banner) =====
    QWidget *banner = new QWidget;
    banner->setObjectName("banner");
    banner->setStyleSheet(
        "#banner { background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #667eea,stop:1 #764ba2);"
        "  border-radius: 10px; }"
    );
    banner->setGraphicsEffect(makeShadow(banner));
    QHBoxLayout *bannerLayout = new QHBoxLayout(banner);
    bannerLayout->setContentsMargins(24, 14, 24, 14);

    QVBoxLayout *bannerTextCol = new QVBoxLayout;
    bannerTextCol->setSpacing(2);
    QLabel *bannerTitle = new QLabel("DocAI \xe6\x99\xba\xe8\x83\xbd\xe6\x96\x87\xe6\xa1\xa3\xe5\xa4\x84\xe7\x90\x86\xe7\xb3\xbb\xe7\xbb\x9f");
    bannerTitle->setStyleSheet("color: white; font-size: 16px; font-weight: 700; background: transparent;");
    QLabel *bannerDesc = new QLabel("\xe6\x95\xb4\xe5\x90\x88 AI \xe5\xa4\xa7\xe8\xaf\xad\xe8\xa8\x80\xe6\xa8\xa1\xe5\x9e\x8b\xef\xbc\x8c\xe5\xae\x9e\xe7\x8e\xb0\xe6\x96\x87\xe6\xa1\xa3\xe6\x99\xba\xe8\x83\xbd\xe7\xbc\x96\xe8\xbe\x91\xe3\x80\x81\xe4\xbf\xa1\xe6\x81\xaf\xe8\x87\xaa\xe5\x8a\xa8\xe6\x8f\x90\xe5\x8f\x96\xe3\x80\x81\xe8\xa1\xa8\xe6\xa0\xbc\xe4\xb8\x80\xe9\x94\xae\xe5\xa1\xab\xe5\x86\x99");
    bannerDesc->setStyleSheet("color: rgba(255,255,255,0.75); font-size: 12px; background: transparent;");
    bannerTextCol->addWidget(bannerTitle);
    bannerTextCol->addWidget(bannerDesc);

    QHBoxLayout *btnRow = new QHBoxLayout;
    btnRow->setSpacing(8);
    auto makeBannerBtn = [this](const QString &text, int page) -> QPushButton* {
        QPushButton *btn = new QPushButton(text);
        btn->setFixedHeight(32);
        btn->setCursor(Qt::PointingHandCursor);
        btn->setStyleSheet(
            "QPushButton { background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);"
            "  border-radius: 6px; padding: 0 16px; font-size: 12px; font-weight: 500; }"
            "QPushButton:hover { background: rgba(255,255,255,0.35); }");
        connect(btn, &QPushButton::clicked, [this, page]() { emit navigateTo(page); });
        return btn;
    };
    btnRow->addWidget(makeBannerBtn("\xe5\xbc\x80\xe5\xa7\x8b\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe8\xa1\xa8", 2));
    btnRow->addWidget(makeBannerBtn("\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x96\x87\xe6\xa1\xa3", 1));
    btnRow->addWidget(makeBannerBtn("AI \xe5\xaf\xb9\xe8\xaf\x9d", 3));

    bannerLayout->addLayout(bannerTextCol, 1);
    bannerLayout->addLayout(btnRow);

    // ===== Stats Row =====
    QHBoxLayout *statsRow = new QHBoxLayout;
    statsRow->setSpacing(16);

    m_totalLabel = new QLabel("0");
    m_docxLabel = new QLabel("0");
    m_xlsxLabel = new QLabel("0");
    m_txtLabel = new QLabel("0");

    auto makeStatWidget = [](QLabel *valLabel, const QString &label, const QPixmap &iconPix, const QString &bg, const QString &color) -> QWidget* {
        QWidget *card = new QWidget;
        card->setObjectName("statCard");
        card->setStyleSheet("#statCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }"
                            "#statCard:hover { border-color: #C7D2FE; }");
        card->setGraphicsEffect(makeShadow(card));
        card->setCursor(Qt::PointingHandCursor);
        QHBoxLayout *h = new QHBoxLayout(card);
        h->setContentsMargins(20, 16, 20, 16);
        QLabel *iconLbl = new QLabel;
        iconLbl->setFixedSize(44, 44);
        iconLbl->setAlignment(Qt::AlignCenter);
        iconLbl->setPixmap(iconPix);
        iconLbl->setStyleSheet(QString("background: %1; border-radius: 12px;").arg(bg));
        QVBoxLayout *info = new QVBoxLayout;
        info->setSpacing(2);
        valLabel->setStyleSheet(QString("font-size: 24px; font-weight: 700; color: %1;").arg(color));
        QLabel *lbl = new QLabel(label);
        lbl->setStyleSheet("font-size: 12px; color: #9CA3AF;");
        info->addWidget(valLabel);
        info->addWidget(lbl);
        h->addWidget(iconLbl);
        h->addLayout(info);
        h->addStretch();
        return card;
    };

    QWidget *sc1 = makeStatWidget(m_totalLabel, "\xe6\x80\xbb\xe6\x96\x87\xe6\xa1\xa3\xe6\x95\xb0", IconHelper::document(22, QColor("#3730A3")), "#EEF2FF", "#3730A3");
    sc1->setProperty("targetPage", 1); sc1->installEventFilter(this);
    QWidget *sc2 = makeStatWidget(m_docxLabel, "Word \xe6\x96\x87\xe6\xa1\xa3", IconHelper::pencil(22, QColor("#10B981")), "#D1FAE5", "#10B981");
    sc2->setProperty("targetPage", 1); sc2->installEventFilter(this);
    QWidget *sc3 = makeStatWidget(m_xlsxLabel, "Excel \xe8\xa1\xa8\xe6\xa0\xbc", IconHelper::chart(22, QColor("#F59E0B")), "#FEF3C7", "#F59E0B");
    sc3->setProperty("targetPage", 1); sc3->installEventFilter(this);
    QWidget *sc4 = makeStatWidget(m_txtLabel, "\xe6\x96\x87\xe6\x9c\xac\xe6\x96\x87\xe4\xbb\xb6", IconHelper::document(22, QColor("#06B6D4")), "#CFFAFE", "#06B6D4");
    sc4->setProperty("targetPage", 1); sc4->installEventFilter(this);
    statsRow->addWidget(sc1);
    statsRow->addWidget(sc2);
    statsRow->addWidget(sc3);
    statsRow->addWidget(sc4);

    // ===== Quick Start =====
    QLabel *sectionTitle1 = new QLabel("快速入门 Quick Start");
    sectionTitle1->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QLabel *sectionDesc1 = new QLabel("按照以下步骤即可快速开始使用系统完成文档处理工作");
    sectionDesc1->setStyleSheet("font-size: 13px; color: #9CA3AF;");

    QHBoxLayout *guideRow = new QHBoxLayout;
    guideRow->setSpacing(16);
    guideRow->addWidget(makeGuideStep(1, "upload", "\xe4\xb8\x8a\xe4\xbc\xa0\xe6\xba\x90\xe6\x96\x87\xe6\xa1\xa3",
        "\xe4\xb8\x8a\xe4\xbc\xa0Word/Excel/\xe6\x96\x87\xe6\x9c\xac\xe6\x96\x87\xe6\xa1\xa3\n\xe7\xb3\xbb\xe7\xbb\x9f\xe8\x87\xaa\xe5\x8a\xa8\xe6\x8f\x90\xe5\x8f\x96\xe5\x85\xb3\xe9\x94\xae\xe4\xbf\xa1\xe6\x81\xaf", "#EEF2FF", 1));
    guideRow->addWidget(makeGuideStep(2, "table", "\xe4\xb8\x8a\xe4\xbc\xa0\xe5\xa1\xab\xe8\xa1\xa8\xe6\xa8\xa1\xe6\x9d\xbf",
        "\xe4\xb8\x8a\xe4\xbc\xa0\xe5\x90\xab\xe5\x8d\xa0\xe4\xbd\x8d\xe7\xac\xa6\xe7\x9a\x84\xe6\xa8\xa1\xe6\x9d\xbf\xe6\x96\x87\xe4\xbb\xb6\n\xe6\x94\xaf\xe6\x8c\x81Word\xe5\x92\x8c" "Excel\xe6\xa0\xbc\xe5\xbc\x8f", "#EEF2FF", 2));
    guideRow->addWidget(makeGuideStep(3, "robot", "AI\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe5\x85\x85",
        "AI\xe8\x87\xaa\xe5\x8a\xa8\xe5\x8c\xb9\xe9\x85\x8d\xe6\x8f\x90\xe5\x8f\x96\xe6\x95\xb0\xe6\x8d\xae\n\xe4\xb8\x80\xe9\x94\xae\xe5\xae\x8c\xe6\x88\x90\xe8\xa1\xa8\xe6\xa0\xbc\xe5\xa1\xab\xe5\x86\x99", "#D1FAE5", 2));
    guideRow->addWidget(makeGuideStep(4, "download", "\xe4\xb8\x8b\xe8\xbd\xbd\xe7\xbb\x93\xe6\x9e\x9c",
        "\xe9\xa2\x84\xe8\xa7\x88\xe5\xa1\xab\xe5\x85\x85\xe7\xbb\x93\xe6\x9e\x9c\n\xe4\xb8\x8b\xe8\xbd\xbd\xe6\x88\x96\xe5\x8f\x91\xe9\x80\x81\xe9\x82\xae\xe4\xbb\xb6", "#FEF3C7", 2));

    // ===== Core Modules =====
    QLabel *sectionTitle2 = new QLabel("核心功能模块 Core Modules");
    sectionTitle2->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QLabel *sectionDesc2 = new QLabel("覆盖从文档理解到数据提取、自动填表的完整工作流");
    sectionDesc2->setStyleSheet("font-size: 13px; color: #9CA3AF;");

    QHBoxLayout *modulesRow = new QHBoxLayout;
    modulesRow->setSpacing(16);
    modulesRow->addWidget(makeModuleCard("01", "AI 智能对话", "基于大语言模型，支持文档关联问答、内容编辑、润色翻译等",
        {"SSE流式输出", "多会话管理", "9种快捷指令"}, 3));
    modulesRow->addWidget(makeModuleCard("02", "文档信息提取", "上传文档后自动识别提取关键字段与结构化数据",
        {"多格式支持", "批量上传", "状态追踪"}, 1));
    modulesRow->addWidget(makeModuleCard("03", "智能填表系统", "三步完成：上传模板→AI填充→预览下载",
        {"占位符匹配", "置信度分析", "邮件发送"}, 2));

    // ===== About =====
    QWidget *aboutCard = new QWidget;
    aboutCard->setObjectName("aboutCard");
    aboutCard->setStyleSheet("#aboutCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    aboutCard->setGraphicsEffect(makeShadow(aboutCard));
    QVBoxLayout *aboutLayout = new QVBoxLayout(aboutCard);
    aboutLayout->setContentsMargins(28, 24, 28, 24);
    QLabel *aboutTitle = new QLabel("关于 DocAI");
    aboutTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QLabel *aboutText = new QLabel(
        "DocAI 是一款基于大语言模型（LLM）的智能文档处理系统，旨在解决企业和个人在文档管理、"
        "信息提取、表格填写等场景中的效率瓶颈。系统采用\"先提取、后匹配、再填充\"的三阶段处理架构，"
        "确保每一步结果可解释、可追溯。"
    );
    aboutText->setWordWrap(true);
    aboutText->setStyleSheet("font-size: 13px; color: #6B7280; line-height: 1.6;");
    aboutLayout->addWidget(aboutTitle);
    aboutLayout->addWidget(aboutText);

    // ===== Collapsible: Formats & Tips (default collapsed) =====
    QPushButton *toggleTipsBtn = new QPushButton(QString::fromUtf8("\u25b6 \xe6\x94\xaf\xe6\x8c\x81\xe6\xa0\xbc\xe5\xbc\x8f\xe4\xb8\x8e\xe4\xbd\xbf\xe7\x94\xa8\xe6\x8f\x90\xe7\xa4\xba"));
    toggleTipsBtn->setCursor(Qt::PointingHandCursor);
    toggleTipsBtn->setStyleSheet("QPushButton { text-align: left; font-size: 13px; font-weight: 600; color: #6B7280; border: none; background: transparent; padding: 4px 0; }"
                                 "QPushButton:hover { color: #4F46E5; }");
    QWidget *tipsContainer = new QWidget;
    QVBoxLayout *tipsContainerLayout = new QVBoxLayout(tipsContainer);
    tipsContainerLayout->setContentsMargins(0, 0, 0, 0);
    tipsContainerLayout->setSpacing(16);

    QHBoxLayout *tipsRow = new QHBoxLayout;
    tipsRow->setSpacing(16);

    // Formats card
    QWidget *formatsCard = new QWidget;
    formatsCard->setObjectName("formatsCard");
    formatsCard->setStyleSheet("#formatsCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    formatsCard->setGraphicsEffect(makeShadow(formatsCard));
    QVBoxLayout *fmtLayout = new QVBoxLayout(formatsCard);
    fmtLayout->setContentsMargins(24, 20, 24, 20);
    QLabel *fmtTitle = new QLabel("\xe6\x94\xaf\xe6\x8c\x81\xe6\xa0\xbc\xe5\xbc\x8f");
    fmtTitle->setStyleSheet("font-size: 15px; font-weight: 600; color: #111827;");
    fmtLayout->addWidget(fmtTitle);
    fmtLayout->addSpacing(8);
    struct FmtDef { QString ext; QString color; };
    QList<FmtDef> fmts = {{".docx", "#4F46E5"}, {".xlsx", "#10B981"}, {".txt", "#F59E0B"}, {".md", "#8B5CF6"}};
    QHBoxLayout *fmtTags = new QHBoxLayout;
    fmtTags->setSpacing(8);
    for (const auto &f : fmts) {
        QLabel *tag = new QLabel(f.ext);
        tag->setStyleSheet(QString("font-size: 12px; font-weight: 600; color: %1; background: %2;"
            "border-radius: 6px; padding: 4px 12px;").arg(f.color, f.color + "1A"));
        fmtTags->addWidget(tag);
    }
    fmtTags->addStretch();
    fmtLayout->addLayout(fmtTags);

    // Tips card
    QWidget *tipsCard = new QWidget;
    tipsCard->setObjectName("tipsCard");
    tipsCard->setStyleSheet("#tipsCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    tipsCard->setGraphicsEffect(makeShadow(tipsCard));
    QVBoxLayout *tipsLayout = new QVBoxLayout(tipsCard);
    tipsLayout->setContentsMargins(24, 20, 24, 20);
    QLabel *tipsTitle = new QLabel("\xe4\xbd\xbf\xe7\x94\xa8\xe6\x8f\x90\xe7\xa4\xba");
    tipsTitle->setStyleSheet("font-size: 15px; font-weight: 600; color: #111827;");
    tipsLayout->addWidget(tipsTitle);
    tipsLayout->addSpacing(4);
    QStringList tips = {
        "\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x96\x87\xe6\xa1\xa3\xe5\x90\x8e\xe7\xb3\xbb\xe7\xbb\x9f\xe8\x87\xaa\xe5\x8a\xa8\xe8\xbf\x9b\xe8\xa1\x8c\xe7\xbb\x93\xe6\x9e\x84\xe5\x8c\x96\xe4\xbf\xa1\xe6\x81\xaf\xe6\x8f\x90\xe5\x8f\x96",
        "\xe6\xa8\xa1\xe6\x9d\xbf\xe6\x96\x87\xe4\xbb\xb6\xe9\x9c\x80\xe5\x8c\x85\xe5\x90\xab {{\xe5\xad\x97\xe6\xae\xb5\xe5\x90\x8d}} \xe5\x8d\xa0\xe4\xbd\x8d\xe7\xac\xa6\xe6\x88\x96\xe7\x95\x99\xe7\xa9\xba\xe7\x9a\x84\xe8\xa1\xa8\xe6\xa0\xbc\xe5\x8d\x95\xe5\x85\x83\xe6\xa0\xbc",
        "AI \xe5\xaf\xb9\xe8\xaf\x9d\xe4\xb8\xad\xe5\x8f\xaf\xe5\x85\xb3\xe8\x81\x94\xe5\xb7\xb2\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x96\x87\xe6\xa1\xa3\xe8\xbf\x9b\xe8\xa1\x8c\xe6\xb7\xb1\xe5\xba\xa6\xe5\x88\x86\xe6\x9e\x90",
        "\xe6\x89\xb9\xe9\x87\x8f\xe4\xb8\x8a\xe4\xbc\xa0\xe6\xa8\xa1\xe6\x9d\xbf\xe5\x8f\xaf\xe4\xb8\x80\xe6\xac\xa1\xe6\x80\xa7\xe5\xa1\xab\xe5\x85\x85\xe5\xb9\xb6\xe4\xb8\x8b\xe8\xbd\xbd ZIP \xe5\x8e\x8b\xe7\xbc\xa9\xe5\x8c\x85",
    };
    for (const QString &tip : tips) {
        QLabel *tipLbl = new QLabel(QString("\xe2\x80\xa2 ") + tip);
        tipLbl->setWordWrap(true);
        tipLbl->setStyleSheet("font-size: 12px; color: #6B7280;");
        tipsLayout->addWidget(tipLbl);
    }

    tipsRow->addWidget(formatsCard, 1);
    tipsRow->addWidget(tipsCard, 2);
    tipsContainerLayout->addLayout(tipsRow);
    tipsContainer->setVisible(false);
    connect(toggleTipsBtn, &QPushButton::clicked, [toggleTipsBtn, tipsContainer]() {
        bool show = !tipsContainer->isVisible();
        tipsContainer->setVisible(show);
        toggleTipsBtn->setText(show ? QString::fromUtf8("\u25bc \xe6\x94\xaf\xe6\x8c\x81\xe6\xa0\xbc\xe5\xbc\x8f\xe4\xb8\x8e\xe4\xbd\xbf\xe7\x94\xa8\xe6\x8f\x90\xe7\xa4\xba")
                                    : QString::fromUtf8("\u25b6 \xe6\x94\xaf\xe6\x8c\x81\xe6\xa0\xbc\xe5\xbc\x8f\xe4\xb8\x8e\xe4\xbd\xbf\xe7\x94\xa8\xe6\x8f\x90\xe7\xa4\xba"));
    });

    // ===== Workflow Diagram =====
    QLabel *workflowTitle = new QLabel("\xe5\xb7\xa5\xe4\xbd\x9c\xe6\xb5\x81\xe7\xa8\x8b\xe5\x9b\xbe\xe8\xa7\xa3 Workflow");
    workflowTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QWidget *workflowCard = new QWidget;
    workflowCard->setObjectName("wfCard");
    workflowCard->setStyleSheet("#wfCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    workflowCard->setGraphicsEffect(makeShadow(workflowCard));
    QHBoxLayout *wfLayout = new QHBoxLayout(workflowCard);
    wfLayout->setContentsMargins(24, 20, 24, 20);
    wfLayout->setSpacing(0);
    QStringList wfSteps = {"\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x96\x87\xe6\xa1\xa3", "\xe6\x96\x87\xe6\xa1\xa3\xe8\xa7\xa3\xe6\x9e\x90",
        "AI\xe4\xbf\xa1\xe6\x81\xaf\xe6\x8f\x90\xe5\x8f\x96", "\xe4\xb8\x8a\xe4\xbc\xa0\xe6\xa8\xa1\xe6\x9d\xbf",
        "\xe6\x99\xba\xe8\x83\xbd\xe5\x8c\xb9\xe9\x85\x8d", "\xe8\x87\xaa\xe5\x8a\xa8\xe5\xa1\xab\xe5\x85\x85",
        "\xe4\xb8\x8b\xe8\xbd\xbd\xe7\xbb\x93\xe6\x9e\x9c"};
    for (int i = 0; i < wfSteps.size(); i++) {
        QLabel *step = new QLabel(wfSteps[i]);
        step->setAlignment(Qt::AlignCenter);
        step->setStyleSheet("font-size: 12px; font-weight: 600; color: #4F46E5; background: #EEF2FF; border-radius: 8px; padding: 8px 12px;");
        wfLayout->addWidget(step);
        if (i < wfSteps.size() - 1) {
            QLabel *arrow = new QLabel("\xe2\x86\x92");
            arrow->setAlignment(Qt::AlignCenter);
            arrow->setStyleSheet("font-size: 14px; color: #D1D5DB; padding: 0 4px;");
            wfLayout->addWidget(arrow);
        }
    }

    // ===== Model Info Bar =====
    QWidget *modelBar = new QWidget;
    modelBar->setObjectName("modelBar");
    modelBar->setStyleSheet("#modelBar { background: #EEF2FF; border-radius: 10px; border: 1px solid #C7D2FE; }");
    QHBoxLayout *modelLayout = new QHBoxLayout(modelBar);
    modelLayout->setContentsMargins(20, 12, 20, 12);
    QLabel *modelLabel = new QLabel("AI \xe5\xbc\x95\xe6\x93\x8e\xef\xbc\x9a\xe5\xa4\xa7\xe8\xaf\xad\xe8\xa8\x80\xe6\xa8\xa1\xe5\x9e\x8b\xe9\xa9\xb1\xe5\x8a\xa8");
    modelLabel->setStyleSheet("font-size: 13px; font-weight: 600; color: #4F46E5;");
    modelLayout->addWidget(modelLabel);
    modelLayout->addStretch();
    for (const QString &mtag : {"\xe8\xb6\x85\xe9\x95\xbf\xe4\xb8\x8a\xe4\xb8\x8b\xe6\x96\x87", "\xe5\xa4\x9a\xe6\xa8\xa1\xe5\x9e\x8b\xe5\x88\x87\xe6\x8d\xa2"}) {
        QLabel *tagLbl = new QLabel(mtag);
        tagLbl->setStyleSheet("font-size: 11px; color: #4F46E5; background: rgba(79,70,229,0.1); border-radius: 4px; padding: 2px 8px;");
        modelLayout->addWidget(tagLbl);
    }

    // ===== Core Advantages =====
    QLabel *advTitle = new QLabel("\xe6\xa0\xb8\xe5\xbf\x83\xe4\xbc\x98\xe5\x8a\xbf Core Advantages");
    advTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    struct AdvDef { QString name; QString desc; QString color; };
    QList<AdvDef> advs = {
        {"\xe6\x95\x88\xe7\x8e\x87\xe5\x80\x8d\xe5\xa2\x9e", "\xe7\xa7\x92\xe7\xba\xa7\xe6\x8f\x90\xe5\x8f\x96\xef\xbc\x8c\xe5\x88\x86\xe9\x92\x9f\xe5\xae\x8c\xe6\x88\x90\xe5\x85\xa8\xe6\xb5\x81\xe7\xa8\x8b", "#4F46E5"},
        {"\xe7\xb2\xbe\xe5\x87\x86\xe6\x8f\x90\xe5\x8f\x96", "AI \xe6\x99\xba\xe8\x83\xbd\xe8\xaf\x86\xe5\x88\xab\xe5\x85\xb3\xe9\x94\xae\xe4\xbf\xa1\xe6\x81\xaf", "#10B981"},
        {"\xe6\x89\xb9\xe9\x87\x8f\xe5\xa4\x84\xe7\x90\x86", "\xe5\xa4\x9a\xe6\xa8\xa1\xe6\x9d\xbf\xe4\xb8\x80\xe9\x94\xae\xe5\xa1\xab\xe5\x85\x85\xe5\xaf\xbc\xe5\x87\xba", "#F59E0B"},
        {"AI \xe5\xaf\xb9\xe8\xaf\x9d", "\xe8\x87\xaa\xe7\x84\xb6\xe8\xaf\xad\xe8\xa8\x80\xe6\xb7\xb1\xe5\xba\xa6\xe4\xba\xa4\xe4\xba\x92", "#8B5CF6"},
        {"\xe5\xae\x89\xe5\x85\xa8\xe5\x8f\xaf\xe9\x9d\xa0", "\xe6\x95\xb0\xe6\x8d\xae\xe5\x8a\xa0\xe5\xaf\x86\xef\xbc\x8c\xe6\x9d\x83\xe9\x99\x90\xe6\x8e\xa7\xe5\x88\xb6", "#EF4444"},
        {"\xe6\xa0\xbc\xe5\xbc\x8f\xe5\x85\xbc\xe5\xae\xb9", "Word/Excel/TXT/MD \xe7\xbb\x9f\xe4\xb8\x80\xe5\xa4\x84\xe7\x90\x86", "#06B6D4"},
    };
    QGridLayout *advGrid = new QGridLayout;
    advGrid->setSpacing(12);
    for (int i = 0; i < advs.size(); i++) {
        QWidget *advCard = new QWidget;
        advCard->setObjectName(QString("adv%1").arg(i));
        advCard->setStyleSheet(QString("#adv%1 { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }").arg(i));
        advCard->setGraphicsEffect(makeShadow(advCard));
        QVBoxLayout *aLay = new QVBoxLayout(advCard);
        aLay->setContentsMargins(16, 14, 16, 14);
        QLabel *aName = new QLabel(advs[i].name);
        aName->setStyleSheet(QString("font-size: 14px; font-weight: 600; color: %1;").arg(advs[i].color));
        QLabel *aDesc = new QLabel(advs[i].desc);
        aDesc->setStyleSheet("font-size: 12px; color: #9CA3AF;");
        aLay->addWidget(aName);
        aLay->addWidget(aDesc);
        advGrid->addWidget(advCard, i / 3, i % 3);
    }

    // ===== Comparison Table =====
    QLabel *cmpTitle = new QLabel("\xe4\xbc\xa0\xe7\xbb\x9f\xe6\x96\xb9\xe5\xbc\x8f vs DocAI");
    cmpTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    QWidget *cmpCard = new QWidget;
    cmpCard->setObjectName("cmpCard");
    cmpCard->setStyleSheet("#cmpCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    cmpCard->setGraphicsEffect(makeShadow(cmpCard));
    QVBoxLayout *cmpLayout = new QVBoxLayout(cmpCard);
    cmpLayout->setContentsMargins(24, 20, 24, 20);
    struct CmpRow { QString dim; QString old_way; QString new_way; };
    QList<CmpRow> cmpRows = {
        {"\xe4\xbf\xa1\xe6\x81\xaf\xe6\x8f\x90\xe5\x8f\x96", "\xe4\xba\xba\xe5\xb7\xa5\xe9\x80\x90\xe9\xa1\xb5\xe9\x98\x85\xe8\xaf\xbb\xef\xbc\x8c\xe6\x89\x8b\xe5\x8a\xa8\xe6\x91\x98\xe5\xbd\x95", "AI \xe8\x87\xaa\xe5\x8a\xa8\xe8\xaf\x86\xe5\x88\xab\xe5\xb9\xb6\xe6\x8f\x90\xe5\x8f\x96\xe7\xbb\x93\xe6\x9e\x84\xe5\x8c\x96\xe6\x95\xb0\xe6\x8d\xae"},
        {"\xe8\xa1\xa8\xe6\xa0\xbc\xe5\xa1\xab\xe5\x86\x99", "\xe5\xaf\xb9\xe7\x85\xa7\xe6\xba\x90\xe6\x96\x87\xe6\xa1\xa3\xe9\x80\x90\xe6\xa0\xbc\xe6\x89\x8b\xe5\xa1\xab\xef\xbc\x8c\xe5\xae\xb9\xe6\x98\x93\xe9\x81\x97\xe6\xbc\x8f", "\xe6\x99\xba\xe8\x83\xbd\xe5\x8c\xb9\xe9\x85\x8d\xe4\xb8\x80\xe9\x94\xae\xe5\xa1\xab\xe5\x85\x85\xef\xbc\x8c\xe6\x89\xb9\xe9\x87\x8f\xe5\xa4\x84\xe7\x90\x86"},
        {"\xe6\x96\x87\xe6\xa1\xa3\xe7\xbc\x96\xe8\xbe\x91", "\xe5\x8f\x8d\xe5\xa4\x8d\xe4\xbf\xae\xe6\x94\xb9\xe6\xa0\xbc\xe5\xbc\x8f\xef\xbc\x8c\xe6\x89\x8b\xe5\x8a\xa8\xe8\xb0\x83\xe6\x95\xb4\xe6\x8e\x92\xe7\x89\x88", "AI \xe5\xaf\xb9\xe8\xaf\x9d\xe5\xbc\x8f\xe7\xbc\x96\xe8\xbe\x91\xef\xbc\x8c\xe8\x87\xaa\xe7\x84\xb6\xe8\xaf\xad\xe8\xa8\x80\xe9\xa9\xb1\xe5\x8a\xa8"},
        {"\xe5\xa4\x84\xe7\x90\x86\xe9\x80\x9f\xe5\xba\xa6", "\xe5\x8d\x95\xe4\xbb\xbd\xe6\x96\x87\xe6\xa1\xa3\xe9\x9c\x80\xe8\xa6\x81\xe6\x95\xb0\xe5\xb0\x8f\xe6\x97\xb6", "\xe7\xa7\x92\xe7\xba\xa7\xe6\x8f\x90\xe5\x8f\x96\xef\xbc\x8c\xe5\x88\x86\xe9\x92\x9f\xe5\xae\x8c\xe6\x88\x90\xe5\x85\xa8\xe6\xb5\x81\xe7\xa8\x8b"},
        {"\xe5\xa4\x9a\xe6\x96\x87\xe6\xa1\xa3\xe5\x8d\x8f\xe5\x90\x8c", "\xe5\x9c\xa8\xe5\xa4\x9a\xe4\xb8\xaa\xe6\x96\x87\xe4\xbb\xb6\xe9\x97\xb4\xe6\x9d\xa5\xe5\x9b\x9e\xe5\x88\x87\xe6\x8d\xa2", "\xe7\xbb\x9f\xe4\xb8\x80\xe6\x96\x87\xe6\xa1\xa3\xe5\xba\x93\xef\xbc\x8c\xe8\xb7\xa8\xe6\x96\x87\xe6\xa1\xa3\xe6\x99\xba\xe8\x83\xbd\xe6\xa3\x80\xe7\xb4\xa2"},
        {"\xe6\xa0\xbc\xe5\xbc\x8f\xe5\x85\xbc\xe5\xae\xb9", "\xe4\xb8\x8d\xe5\x90\x8c\xe6\xa0\xbc\xe5\xbc\x8f\xe9\x9c\x80\xe4\xb8\x8d\xe5\x90\x8c\xe5\xb7\xa5\xe5\x85\xb7", "Word/Excel/TXT/MD \xe7\xbb\x9f\xe4\xb8\x80\xe5\xa4\x84\xe7\x90\x86"},
    };
    // Table header
    QHBoxLayout *cmpHdr = new QHBoxLayout;
    for (const QString &h : {"\xe5\xaf\xb9\xe6\xaf\x94\xe7\xbb\xb4\xe5\xba\xa6", "\xe4\xbc\xa0\xe7\xbb\x9f\xe6\x89\x8b\xe5\xb7\xa5\xe6\x96\xb9\xe5\xbc\x8f", "DocAI \xe6\x99\xba\xe8\x83\xbd\xe5\xa4\x84\xe7\x90\x86"}) {
        QLabel *hLbl = new QLabel(h);
        hLbl->setStyleSheet("font-size: 12px; font-weight: 600; color: #6B7280; padding: 8px 0;");
        cmpHdr->addWidget(hLbl, 1);
    }
    cmpLayout->addLayout(cmpHdr);
    QFrame *hLine = new QFrame; hLine->setFrameShape(QFrame::HLine); hLine->setStyleSheet("color: #F3F4F6;");
    cmpLayout->addWidget(hLine);
    for (const auto &r : cmpRows) {
        QHBoxLayout *row = new QHBoxLayout;
        QLabel *dimLbl = new QLabel(r.dim);
        dimLbl->setStyleSheet("font-size: 12px; font-weight: 600; color: #374151; padding: 6px 0;");
        QLabel *oldLbl = new QLabel(r.old_way);
        oldLbl->setWordWrap(true);
        oldLbl->setStyleSheet("font-size: 12px; color: #9CA3AF; padding: 6px 0;");
        QLabel *newLbl = new QLabel(r.new_way);
        newLbl->setWordWrap(true);
        newLbl->setStyleSheet("font-size: 12px; color: #4F46E5; padding: 6px 0;");
        row->addWidget(dimLbl, 1);
        row->addWidget(oldLbl, 1);
        row->addWidget(newLbl, 1);
        cmpLayout->addLayout(row);
    }

    // Assembly
    layout->addWidget(banner);
    layout->addLayout(statsRow);
    layout->addWidget(sectionTitle1);
    layout->addWidget(sectionDesc1);
    layout->addLayout(guideRow);
    layout->addWidget(sectionTitle2);
    layout->addWidget(sectionDesc2);
    layout->addLayout(modulesRow);
    layout->addWidget(toggleTipsBtn);
    layout->addWidget(tipsContainer);
    layout->addWidget(workflowTitle);
    layout->addWidget(workflowCard);
    layout->addWidget(modelBar);
    layout->addWidget(aboutCard);
    layout->addWidget(advTitle);
    layout->addLayout(advGrid);
    layout->addWidget(cmpTitle);
    layout->addWidget(cmpCard);
    layout->addStretch();

    scroll->setWidget(content);
    outer->addWidget(scroll);
}

QWidget* DashboardPage::makeGuideStep(int num, const QString &icon, const QString &title,
                                        const QString &desc, const QString &bgColor, int targetPage) {
    QWidget *card = new QWidget;
    card->setObjectName("guideCard");
    card->setStyleSheet(QString("#guideCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }"
                                "#guideCard:hover { border-color: #4F46E5; }"));
    card->setCursor(Qt::PointingHandCursor);
    card->setGraphicsEffect(makeShadow(card));

    QVBoxLayout *v = new QVBoxLayout(card);
    v->setContentsMargins(20, 20, 20, 20);
    v->setSpacing(8);

    QLabel *numLbl = new QLabel(QString::number(num));
    numLbl->setFixedSize(28, 28);
    numLbl->setAlignment(Qt::AlignCenter);
    numLbl->setStyleSheet("background: #EEF2FF; color: #4F46E5; border-radius: 14px; font-size: 13px; font-weight: 700;");

    // Map icon name to IconHelper pixmap
    QPixmap iconPix;
    QColor iconColor("#4F46E5");
    if (icon == "upload") iconPix = IconHelper::upload(20, iconColor);
    else if (icon == "table") iconPix = IconHelper::table(20, iconColor);
    else if (icon == "robot") iconPix = IconHelper::robot(20, QColor("#10B981"));
    else if (icon == "download") iconPix = IconHelper::download(20, QColor("#F59E0B"));
    else iconPix = IconHelper::document(20, iconColor);

    QLabel *iconLbl = new QLabel;
    iconLbl->setFixedSize(40, 40);
    iconLbl->setAlignment(Qt::AlignCenter);
    iconLbl->setPixmap(iconPix);
    iconLbl->setStyleSheet(QString("background: %1; border-radius: 16px;").arg(bgColor));

    QLabel *titleLbl = new QLabel(title);
    titleLbl->setStyleSheet("font-size: 14px; font-weight: 600; color: #111827;");
    QLabel *descLbl = new QLabel(desc);
    descLbl->setWordWrap(true);
    descLbl->setStyleSheet("font-size: 12px; color: #9CA3AF;");

    v->addWidget(numLbl);
    v->addWidget(iconLbl);
    v->addWidget(titleLbl);
    v->addWidget(descLbl);
    v->addStretch();

    // Click handler
    card->installEventFilter(this);
    card->setProperty("targetPage", QVariant(targetPage));

    return card;
}

QWidget* DashboardPage::makeModuleCard(const QString &num, const QString &name, const QString &desc,
                                         const QStringList &features, int targetPage) {
    QWidget *card = new QWidget;
    card->setObjectName("moduleCard");
    card->setStyleSheet("#moduleCard { background: white; border-radius: 10px; border: 1px solid #F3F4F6; }");
    card->setGraphicsEffect(makeShadow(card));
    card->setCursor(Qt::PointingHandCursor);

    QVBoxLayout *v = new QVBoxLayout(card);
    v->setContentsMargins(24, 20, 24, 20);
    v->setSpacing(8);

    QLabel *numLbl = new QLabel(num);
    numLbl->setStyleSheet("font-size: 24px; font-weight: 800; color: #D1D5DB;");
    QLabel *nameLbl = new QLabel(name);
    nameLbl->setStyleSheet("font-size: 16px; font-weight: 600; color: #111827;");
    QLabel *descLbl = new QLabel(desc);
    descLbl->setWordWrap(true);
    descLbl->setStyleSheet("font-size: 12px; color: #6B7280;");

    QHBoxLayout *tagsLayout = new QHBoxLayout;
    tagsLayout->setSpacing(6);
    for (const QString &f : features) {
        QLabel *tag = new QLabel(f);
        tag->setStyleSheet("background: #EEF2FF; color: #4F46E5; font-size: 11px; padding: 3px 8px; border-radius: 8px;");
        tagsLayout->addWidget(tag);
    }
    tagsLayout->addStretch();

    v->addWidget(numLbl);
    v->addWidget(nameLbl);
    v->addWidget(descLbl);
    v->addLayout(tagsLayout);
    v->addStretch();

    card->installEventFilter(this);
    card->setProperty("targetPage", QVariant(targetPage));

    return card;
}

bool DashboardPage::event(QEvent *e) {
    return QWidget::event(e);
}

bool DashboardPage::eventFilter(QObject *obj, QEvent *event) {
    if (event->type() == QEvent::MouseButtonRelease) {
        QWidget *w = qobject_cast<QWidget*>(obj);
        if (w && w->property("targetPage").isValid()) {
            emit navigateTo(w->property("targetPage").toInt());
        }
    }
    return QWidget::eventFilter(obj, event);
}

void DashboardPage::refreshStats() {
    ApiClient::instance().getSourceDocuments([this](bool ok, const QJsonObject &data, const QString &) {
        if (!ok) return;
        QJsonArray docs = data["data"].toArray();
        int total = docs.size(), docx = 0, xlsx = 0, txt = 0;
        for (const auto &v : docs) {
            QString type = v.toObject()["fileType"].toString();
            if (type == "docx") docx++;
            else if (type == "xlsx") xlsx++;
            else txt++;
        }
        m_totalLabel->setText(QString::number(total));
        m_docxLabel->setText(QString::number(docx));
        m_xlsxLabel->setText(QString::number(xlsx));
        m_txtLabel->setText(QString::number(txt));
    });
}
