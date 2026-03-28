#include "DocumentListPage.h"
#include "../network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QHeaderView>
#include <QFileDialog>
#include <QMessageBox>
#include <QJsonArray>
#include <QGraphicsDropShadowEffect>
#include <QCheckBox>
#include <QTextEdit>
#include <QDialog>
#include <QFileInfo>
#include <QStandardPaths>
#include <QDesktopServices>
#include <QUrl>
#include <QRegExp>
#include <algorithm>
#include <QProgressBar>
#include <QStackedWidget>
#include "../utils/IconHelper.h"
#include "../utils/LoadingOverlay.h"
#include "../utils/Toast.h"

static QCheckBox* findCheckBox(QWidget *cellWidget) {
    if (!cellWidget) return nullptr;
    return cellWidget->findChild<QCheckBox*>();
}

DocumentListPage::DocumentListPage(QWidget *parent) : QWidget(parent),
    m_currentPage(1), m_pageSize(20), m_loading(nullptr) {
    setupUI();
    m_loading = new LoadingOverlay(this);
    loadDocuments();
    m_pollTimer = new QTimer(this);
    connect(m_pollTimer, &QTimer::timeout, this, &DocumentListPage::pollStatuses);
    m_pollTimer->start(10000);
}

void DocumentListPage::setupUI() {
    QVBoxLayout *layout = new QVBoxLayout(this);
    layout->setContentsMargins(32, 24, 32, 24);
    layout->setSpacing(16);

    // Toolbar
    QWidget *toolbar = new QWidget;
    toolbar->setObjectName("toolbar");
    toolbar->setStyleSheet("#toolbar { background: white; border-radius: 10px; }");
    QGraphicsDropShadowEffect *sh = new QGraphicsDropShadowEffect;
    sh->setBlurRadius(12); sh->setColor(QColor(0,0,0,8)); sh->setOffset(0, 2);
    toolbar->setGraphicsEffect(sh);

    QHBoxLayout *tbLayout = new QHBoxLayout(toolbar);
    tbLayout->setContentsMargins(20, 12, 20, 12);
    tbLayout->setSpacing(12);

    m_searchEdit = new QLineEdit;
    m_searchEdit->setPlaceholderText("搜索文档名称...");
    m_searchEdit->setMinimumWidth(260);
    m_searchEdit->setMinimumHeight(36);
    m_searchEdit->setStyleSheet(
        "QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 0 12px; font-size: 13px; }"
        "QLineEdit:focus { border-color: #818CF8; }"
    );

    m_typeFilter = new QComboBox;
    m_typeFilter->addItem("全部类型", "");
    m_typeFilter->addItem("Word", "docx");
    m_typeFilter->addItem("Excel", "xlsx");
    m_typeFilter->addItem("TXT", "txt");
    m_typeFilter->addItem("Markdown", "md");
    m_typeFilter->setMinimumHeight(32);
    m_typeFilter->setFixedWidth(140);
    m_typeFilter->setStyleSheet(
        "QComboBox { border: 1.5px solid #D1D5DB; border-radius: 8px; padding: 0 28px 0 12px; font-size: 13px; background: white; }"
        "QComboBox:focus { border-color: #818CF8; }"
        "QComboBox:hover { border-color: #818CF8; background: #F9FAFB; }"
        "QComboBox::drop-down { border: none; width: 28px; subcontrol-position: right center; }"
        "QComboBox::down-arrow { width: 10px; height: 10px; }"
        "QComboBox QAbstractItemView { background: white; border: 1px solid #E5E7EB; border-radius: 8px;"
        "  selection-background-color: #EEF2FF; selection-color: #4F46E5; outline: none; padding: 4px; }"
        "QComboBox QAbstractItemView::item { padding: 6px 12px; border-radius: 4px; }"
    );

    QPushButton *refreshBtn = new QPushButton;
    refreshBtn->setFixedSize(36, 36);
    refreshBtn->setIcon(QIcon(IconHelper::refresh(16, QColor("#6B7280"))));
    refreshBtn->setStyleSheet("QPushButton { border: 1.5px solid #D1D5DB; border-radius: 6px; background: white; }"
                              "QPushButton:hover { background: #EEF2FF; }");

    m_downloadBtn = new QPushButton("\xe4\xb8\x8b\xe8\xbd\xbd\xe5\xb7\xb2\xe9\x80\x89");
    m_downloadBtn->setMinimumHeight(36);
    m_downloadBtn->setEnabled(false);
    m_downloadBtn->setStyleSheet(
        "QPushButton { background: #FEF3C7; color: #F59E0B; border: 1px solid #FCD34D; border-radius: 6px; padding: 0 16px; font-size: 13px; }"
        "QPushButton:hover { background: #FFFB8F; }"
        "QPushButton:disabled { background: #F9FAFB; color: #D1D5DB; border-color: #D1D5DB; }"
    );

    m_deleteBtn = new QPushButton("\xe5\x88\xa0\xe9\x99\xa4\xe5\xb7\xb2\xe9\x80\x89");
    m_deleteBtn->setMinimumHeight(36);
    m_deleteBtn->setEnabled(false);
    m_deleteBtn->setStyleSheet(
        "QPushButton { background: #FEE2E2; color: #EF4444; border: 1px solid #FCA5A5; border-radius: 6px; padding: 0 16px; font-size: 13px; }"
        "QPushButton:hover { background: #FFCCC7; }"
        "QPushButton:disabled { background: #F9FAFB; color: #D1D5DB; border-color: #D1D5DB; }"
    );

    m_uploadBtn = new QPushButton("\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x96\x87\xe6\xa1\xa3");
    m_uploadBtn->setMinimumHeight(36);
    m_uploadBtn->setCursor(Qt::PointingHandCursor);
    m_uploadBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #818CF8, stop:1 #4F46E5);"
        "  color: white; border: none; border-radius: 6px; padding: 0 20px; font-size: 13px; font-weight: 600; }"
        "QPushButton:hover { background: #4F46E5; }"
    );

    tbLayout->addWidget(m_searchEdit);
    tbLayout->addWidget(m_typeFilter);
    tbLayout->addWidget(refreshBtn);
    tbLayout->addStretch();
    tbLayout->addWidget(m_downloadBtn);
    tbLayout->addWidget(m_deleteBtn);
    tbLayout->addWidget(m_uploadBtn);

    // Stats bar
    QWidget *statsBar = new QWidget;
    QHBoxLayout *statsLayout = new QHBoxLayout(statsBar);
    statsLayout->setContentsMargins(0, 0, 0, 0);
    statsLayout->setSpacing(8);
    m_statsLabel = new QLabel;
    m_statsLabel->setStyleSheet("font-size: 12px; color: #6B7280; background: #F3F4F6; border-radius: 10px; padding: 2px 10px;");
    m_selTag = new QLabel;
    m_selTag->setStyleSheet("font-size: 12px; color: #10B981; background: #D1FAE5; border-radius: 10px; padding: 2px 10px;");
    m_selTag->hide();
    m_searchTag = new QLabel;
    m_searchTag->setStyleSheet("font-size: 12px; color: #6366F1; background: #EEF2FF; border-radius: 10px; padding: 2px 10px;");
    m_searchTag->hide();
    statsLayout->addWidget(m_statsLabel);
    statsLayout->addWidget(m_selTag);
    statsLayout->addWidget(m_searchTag);
    statsLayout->addStretch();

    // Table
    m_table = new QTableWidget;
    m_table->setColumnCount(7);
    m_table->setHorizontalHeaderLabels({"", "\xe6\x96\x87\xe4\xbb\xb6\xe5\x90\x8d", "\xe7\xb1\xbb\xe5\x9e\x8b", "\xe5\xa4\xa7\xe5\xb0\x8f", "\xe7\x8a\xb6\xe6\x80\x81", "\xe4\xb8\x8a\xe4\xbc\xa0\xe6\x97\xb6\xe9\x97\xb4", "\xe6\x93\x8d\xe4\xbd\x9c"});
    m_table->horizontalHeader()->setStretchLastSection(false);
    m_table->horizontalHeader()->setSectionResizeMode(QHeaderView::Fixed);
    m_table->horizontalHeader()->setSectionResizeMode(1, QHeaderView::Stretch);
    m_table->horizontalHeader()->setSectionResizeMode(5, QHeaderView::Stretch);
    m_table->setColumnWidth(0, 40);
    m_table->setColumnWidth(2, 90);
    m_table->setColumnWidth(3, 90);
    m_table->setColumnWidth(4, 110);
    m_table->setColumnWidth(6, 220);
    m_table->horizontalHeader()->setSectionsClickable(true);
    m_table->horizontalHeader()->setCursor(Qt::PointingHandCursor);
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_table->verticalHeader()->setVisible(false);
    m_table->setShowGrid(false);
    m_table->setAlternatingRowColors(false);
    m_table->setStyleSheet(
        "QTableWidget { background: white; border: none; border-radius: 10px; font-size: 13px; }"
        "QTableWidget::item { padding: 8px; border-bottom: 1px solid #F3F4F6; }"
        "QTableWidget::item:selected { background: #EEF2FF; color: #111827; }"
        "QTableWidget::item:hover { background: #F9FAFB; }"
        "QHeaderView::section { background: #F9FAFB; border: none; border-bottom: 2px solid #D1D5DB;"
        "  padding: 10px 8px; font-size: 13px; font-weight: 600; color: #6B7280; }"
        "QCheckBox::indicator { width: 12px; height: 12px; border: 1.5px solid #D1D5DB; border-radius: 2px; background: white; }"
        "QCheckBox::indicator:hover { border-color: #818CF8; }"
        "QCheckBox::indicator:checked { background: #4F46E5; border-color: #4F46E5; image: url(:/check.png); }"
    );

    // Select-all checkbox in header
    m_selectAllCb = new QCheckBox(m_table->horizontalHeader());
    m_selectAllCb->setFixedSize(14, 14);
    m_selectAllCb->setStyleSheet(
        "QCheckBox::indicator { width: 12px; height: 12px; border: 1.5px solid #D1D5DB; border-radius: 2px; background: white; }"
        "QCheckBox::indicator:hover { border-color: #818CF8; }"
        "QCheckBox::indicator:checked { background: #4F46E5; border-color: #4F46E5; image: url(:/check.png); }");
    connect(m_selectAllCb, &QCheckBox::toggled, this, &DocumentListPage::toggleSelectAll);
    // Position the checkbox in the first header column center
    auto repositionCb = [this]() {
        int secW = m_table->columnWidth(0);
        m_selectAllCb->move(secW / 2 - 7, (m_table->horizontalHeader()->height() - 14) / 2);
    };
    connect(m_table->horizontalHeader(), &QHeaderView::sectionResized, repositionCb);
    QTimer::singleShot(0, repositionCb);
    QGraphicsDropShadowEffect *tableSh = new QGraphicsDropShadowEffect;
    tableSh->setBlurRadius(12); tableSh->setColor(QColor(0,0,0,8)); tableSh->setOffset(0, 2);
    m_table->setGraphicsEffect(tableSh);

    // Empty state
    m_emptyState = new QWidget;
    QVBoxLayout *emptyLayout = new QVBoxLayout(m_emptyState);
    emptyLayout->setAlignment(Qt::AlignCenter);
    QLabel *emptyIcon = new QLabel;
    emptyIcon->setPixmap(IconHelper::folder(48, QColor("#D1D5DB")));
    emptyIcon->setAlignment(Qt::AlignCenter);
    QLabel *emptyText = new QLabel("\xe6\x9a\x82\xe6\x97\xa0\xe6\x96\x87\xe6\xa1\xa3\xef\xbc\x8c\xe4\xb8\x8a\xe4\xbc\xa0\xe4\xbd\xa0\xe7\x9a\x84\xe7\xac\xac\xe4\xb8\x80\xe4\xb8\xaa\xe6\x96\x87\xe4\xbb\xb6\xe5\x90\xa7");
    emptyText->setAlignment(Qt::AlignCenter);
    emptyText->setStyleSheet("font-size: 14px; color: #9CA3AF;");
    emptyLayout->addWidget(emptyIcon);
    emptyLayout->addWidget(emptyText);

    m_stack = new QStackedWidget;
    m_stack->addWidget(m_table);
    m_stack->addWidget(m_emptyState);

    // Pagination
    m_paginationWidget = new QWidget;
    QHBoxLayout *pageLayout = new QHBoxLayout(m_paginationWidget);
    pageLayout->setContentsMargins(0, 4, 0, 4);
    m_pageInfo = new QLabel;
    m_pageInfo->setStyleSheet("font-size: 12px; color: #6B7280;");
    m_prevBtn = new QPushButton("\xe4\xb8\x8a\xe4\xb8\x80\xe9\xa1\xb5");
    m_prevBtn->setFixedHeight(28);
    m_prevBtn->setStyleSheet("QPushButton { border: 1px solid #D1D5DB; border-radius: 6px; padding: 0 12px; font-size: 12px; background: white; }"
                              "QPushButton:hover { background: #EEF2FF; }"
                              "QPushButton:disabled { color: #D1D5DB; }");
    m_nextBtn = new QPushButton("\xe4\xb8\x8b\xe4\xb8\x80\xe9\xa1\xb5");
    m_nextBtn->setFixedHeight(28);
    m_nextBtn->setStyleSheet(m_prevBtn->styleSheet());
    connect(m_prevBtn, &QPushButton::clicked, [this]() { if (m_currentPage > 1) { m_currentPage--; renderPage(); } });
    connect(m_nextBtn, &QPushButton::clicked, [this]() { int maxPage = (m_documents.size() + m_pageSize - 1) / m_pageSize; if (m_currentPage < maxPage) { m_currentPage++; renderPage(); } });
    pageLayout->addWidget(m_pageInfo);
    pageLayout->addStretch();
    pageLayout->addWidget(m_prevBtn);
    pageLayout->addWidget(m_nextBtn);

    layout->addWidget(toolbar);
    layout->addWidget(statsBar);
    layout->addWidget(m_stack, 1);
    layout->addWidget(m_paginationWidget);

    // Connections
    connect(refreshBtn, &QPushButton::clicked, this, &DocumentListPage::loadDocuments);
    connect(m_searchEdit, &QLineEdit::returnPressed, this, &DocumentListPage::loadDocuments);
    connect(m_typeFilter, QOverload<int>::of(&QComboBox::currentIndexChanged), this, &DocumentListPage::loadDocuments);
    connect(m_uploadBtn, &QPushButton::clicked, this, &DocumentListPage::uploadDocuments);
    connect(m_deleteBtn, &QPushButton::clicked, this, &DocumentListPage::deleteSelected);
    connect(m_downloadBtn, &QPushButton::clicked, this, &DocumentListPage::downloadSelected);

    // Column header sort
    connect(m_table->horizontalHeader(), &QHeaderView::sectionClicked, [this](int col) {
        if (col == 0 || col == 6) return; // skip checkbox and actions columns
        if (m_sortColumn == col) {
            m_sortOrder = (m_sortOrder == Qt::AscendingOrder) ? Qt::DescendingOrder : Qt::AscendingOrder;
        } else {
            m_sortColumn = col;
            m_sortOrder = Qt::AscendingOrder;
        }
        // Sort m_documents
        std::sort(m_documents.begin(), m_documents.end(), [this](const SourceDocument &a, const SourceDocument &b) {
            bool asc = (m_sortOrder == Qt::AscendingOrder);
            switch (m_sortColumn) {
            case 1: return asc ? a.fileName < b.fileName : a.fileName > b.fileName;
            case 2: return asc ? a.fileType < b.fileType : a.fileType > b.fileType;
            case 3: return asc ? a.fileSize < b.fileSize : a.fileSize > b.fileSize;
            case 4: return asc ? a.status < b.status : a.status > b.status;
            case 5: return asc ? a.uploadTime < b.uploadTime : a.uploadTime > b.uploadTime;
            default: return false;
            }
        });
        // Update header indicators
        for (int i = 1; i <= 5; i++) {
            QString base = m_table->horizontalHeaderItem(i)->data(Qt::UserRole).toString();
            if (base.isEmpty()) base = m_table->horizontalHeaderItem(i)->text();
            m_table->horizontalHeaderItem(i)->setData(Qt::UserRole, base);
            if (i == m_sortColumn)
                m_table->horizontalHeaderItem(i)->setText(base + (m_sortOrder == Qt::AscendingOrder ? " \u2191" : " \u2193"));
            else
                m_table->horizontalHeaderItem(i)->setText(base);
        }
        m_currentPage = 1;
        renderPage();
        updatePagination();
    });
}

void DocumentListPage::loadDocuments() {
    m_loading->showLoading(QString::fromUtf8("\xe5\x8a\xa0\xe8\xbd\xbd\xe6\x96\x87\xe6\xa1\xa3\xe4\xb8\xad..."));
    ApiClient::instance().getSourceDocuments([this](bool ok, const QJsonObject &data, const QString &/*err*/) {
        m_loading->hideLoading();
        if (!ok) return;
        m_documents.clear();
        QJsonArray arr = data["data"].toArray();
        QString keyword = m_searchEdit->text().trimmed();
        QString typeFilter = m_typeFilter->currentData().toString();

        for (const auto &v : arr) {
            SourceDocument doc = SourceDocument::fromJson(v.toObject());
            if (!keyword.isEmpty() && !doc.fileName.contains(keyword, Qt::CaseInsensitive)) continue;
            if (!typeFilter.isEmpty() && doc.fileType != typeFilter) continue;
            m_documents.append(doc);
        }

        // Update search tag
        if (!keyword.isEmpty()) {
            m_searchTag->setText(QString("\xe6\x90\x9c\xe7\xb4\xa2: %1").arg(keyword));
            m_searchTag->show();
        } else {
            m_searchTag->hide();
        }

        m_currentPage = 1;
        renderPage();
        updateStatsBar();
        updatePagination();
    });
}

void DocumentListPage::renderPage() {
    int total = m_documents.size();
    int startIdx = (m_currentPage - 1) * m_pageSize;
    int endIdx = qMin(startIdx + m_pageSize, total);
    int pageCount = endIdx - startIdx;

    if (total == 0) {
        m_stack->setCurrentIndex(1);
        m_paginationWidget->hide();
        return;
    }
    m_stack->setCurrentIndex(0);
    m_paginationWidget->setVisible(total > m_pageSize);

    m_table->setRowCount(pageCount);
    for (int row = 0; row < pageCount; row++) {
        const SourceDocument &doc = m_documents[startIdx + row];
        m_table->setRowHeight(row, 52);
        // Checkbox - centered in a wrapper widget
        QWidget *cbWrapper = new QWidget;
        QHBoxLayout *cbLay = new QHBoxLayout(cbWrapper);
        cbLay->setContentsMargins(0, 0, 0, 0);
        cbLay->setAlignment(Qt::AlignCenter);
        QCheckBox *cb = new QCheckBox;
        cb->setProperty("docId", QVariant(doc.id));
        cb->setProperty("docIdx", QVariant(startIdx + row));
        connect(cb, &QCheckBox::toggled, this, &DocumentListPage::onSelectionChanged);
        cbLay->addWidget(cb);
        m_table->setCellWidget(row, 0, cbWrapper);

        // Filename
        m_table->setItem(row, 1, new QTableWidgetItem(doc.fileName));

        // Type - colored tag
        QWidget *typeWidget = new QWidget;
        QHBoxLayout *typeLayout = new QHBoxLayout(typeWidget);
        typeLayout->setContentsMargins(4, 0, 4, 0);
        QLabel *typeTag = new QLabel(doc.fileType.toUpper());
        QString tagColor, tagBg;
        if (doc.fileType == "docx") { tagColor = "#3B82F6"; tagBg = "#DBEAFE"; }
        else if (doc.fileType == "xlsx") { tagColor = "#10B981"; tagBg = "#D1FAE5"; }
        else if (doc.fileType == "md") { tagColor = "#F59E0B"; tagBg = "#FEF3C7"; }
        else { tagColor = "#6B7280"; tagBg = "#F3F4F6"; }
        typeTag->setStyleSheet(QString("font-size: 11px; font-weight: 600; color: %1; background: %2; border-radius: 4px; padding: 2px 8px;").arg(tagColor, tagBg));
        typeLayout->addWidget(typeTag);
        typeLayout->addStretch();
        m_table->setCellWidget(row, 2, typeWidget);

        // File size
        QTableWidgetItem *sizeItem = new QTableWidgetItem(formatSize(doc.fileSize));
        sizeItem->setTextAlignment(Qt::AlignCenter);
        m_table->setItem(row, 3, sizeItem);


        // Status - colored tag
        QWidget *statusWidget = new QWidget;
        QHBoxLayout *statusLayout = new QHBoxLayout(statusWidget);
        statusLayout->setContentsMargins(4, 0, 4, 0);
        QString statusText, statusColor, statusBg;
        if (doc.status == "parsed") { statusText = "\xe5\xb7\xb2\xe6\x8f\x90\xe5\x8f\x96"; statusColor = "#10B981"; statusBg = "#D1FAE5"; }
        else if (doc.status == "parsing") { statusText = "\xe6\x8f\x90\xe5\x8f\x96\xe4\xb8\xad"; statusColor = "#3B82F6"; statusBg = "#DBEAFE"; }
        else if (doc.status == "failed") { statusText = "\xe6\x8f\x90\xe5\x8f\x96\xe5\xa4\xb1\xe8\xb4\xa5"; statusColor = "#EF4444"; statusBg = "#FEE2E2"; }
        else { statusText = "\xe5\xbe\x85\xe5\xa4\x84\xe7\x90\x86"; statusColor = "#6B7280"; statusBg = "#F3F4F6"; }
        QLabel *statusTag = new QLabel(statusText);
        statusTag->setStyleSheet(QString("font-size: 11px; font-weight: 600; color: %1; background: %2; border-radius: 4px; padding: 2px 8px;").arg(statusColor, statusBg));
        statusLayout->addWidget(statusTag);
        statusLayout->addStretch();
        m_table->setCellWidget(row, 4, statusWidget);

        // Upload time - centered
        QString timeStr = doc.uploadTime;
        if (timeStr.contains('T')) timeStr = timeStr.left(16).replace('T', ' ');
        QTableWidgetItem *timeItem = new QTableWidgetItem(timeStr);
        timeItem->setTextAlignment(Qt::AlignCenter);
        m_table->setItem(row, 5, timeItem);

        // Actions
        QWidget *actWidget = new QWidget;
        QHBoxLayout *actLayout = new QHBoxLayout(actWidget);
        actLayout->setContentsMargins(4, 0, 4, 0);
        actLayout->setSpacing(6);

        auto makeIconBtn = [](const QPixmap &icon, const QString &tooltip, const QString &bg, const QString &hoverBg) -> QPushButton* {
            QPushButton *btn = new QPushButton;
            btn->setFixedSize(32, 32);
            btn->setIcon(QIcon(icon));
            btn->setIconSize(QSize(16, 16));
            btn->setToolTip(tooltip);
            btn->setCursor(Qt::PointingHandCursor);
            btn->setStyleSheet(QString("QPushButton { background: %1; border: none; border-radius: 6px; }"
                "QPushButton:hover { background: %2; }").arg(bg, hoverBg));
            return btn;
        };

        QPushButton *previewBtn = makeIconBtn(IconHelper::document(16, QColor("#4F46E5")), "\xe6\x96\x87\xe4\xbb\xb6\xe9\xa2\x84\xe8\xa7\x88", "#EEF2FF", "#C7D2FE");
        QPushButton *chatBtn = makeIconBtn(IconHelper::chat(16, QColor("#8B5CF6")), "AI \xe5\xaf\xb9\xe8\xaf\x9d", "#F5F3FF", "#DDD6FE");
        chatBtn->setEnabled(doc.status == "parsed");
        if (doc.status != "parsed") chatBtn->setStyleSheet("QPushButton { background: #F9FAFB; border: none; border-radius: 6px; }");
        QPushButton *dlBtn = makeIconBtn(IconHelper::download(16, QColor("#10B981")), "\xe4\xb8\x8b\xe8\xbd\xbd", "#D1FAE5", "#A7F3D0");
        QPushButton *delBtn = makeIconBtn(IconHelper::trash(16, QColor("#EF4444")), "\xe5\x88\xa0\xe9\x99\xa4", "#FEE2E2", "#FECACA");

        int docId = doc.id;
        QString fn = doc.fileName;
        QString ft = doc.fileType;
        connect(previewBtn, &QPushButton::clicked, [this, docId, fn, ft]() { previewDocument(docId, fn, ft); });
        connect(chatBtn, &QPushButton::clicked, [this, docId, fn]() { emit navigateToChat(docId, fn); });
        connect(dlBtn, &QPushButton::clicked, [this, docId, fn]() {
            m_loading->showLoading(QString::fromUtf8("\xe4\xb8\x8b\xe8\xbd\xbd\xe4\xb8\xad..."));
            ApiClient::instance().downloadSourceDocument(docId, [this, fn](bool ok, const QByteArray &data, const QString &filename, const QString &) {
                m_loading->hideLoading();
                if (!ok) return;
                QString savePath = QFileDialog::getSaveFileName(nullptr, "\xe4\xbf\x9d\xe5\xad\x98\xe6\x96\x87\xe4\xbb\xb6",
                    QStandardPaths::writableLocation(QStandardPaths::DesktopLocation) + "/" + (filename.isEmpty() ? fn : filename));
                if (savePath.isEmpty()) return;
                QFile f(savePath);
                if (f.open(QIODevice::WriteOnly)) { f.write(data); f.close(); }
            });
        });
        connect(delBtn, &QPushButton::clicked, [this, docId, fn]() {
            if (QMessageBox::question(this, "\xe7\xa1\xae\xe8\xae\xa4",
                    QString("\xe7\xa1\xae\xe5\xae\x9a\xe5\x88\xa0\xe9\x99\xa4\xe6\x96\x87\xe6\xa1\xa3\xe3\x80\x8c%1\xe3\x80\x8d\xef\xbc\x9f").arg(fn)) != QMessageBox::Yes) return;
            ApiClient::instance().deleteDocument(docId, [this](bool ok, const QJsonObject &, const QString &) {
                if (ok) loadDocuments();
            });
        });

        actLayout->addStretch();
        actLayout->addWidget(previewBtn);
        actLayout->addWidget(chatBtn);
        actLayout->addWidget(dlBtn);
        actLayout->addWidget(delBtn);
        actLayout->addStretch();
        m_table->setCellWidget(row, 6, actWidget);
    }

    updateStatsBar();
    updatePagination();
}

void DocumentListPage::uploadDocuments() {
    QStringList files = QFileDialog::getOpenFileNames(this, "选择文档",
        QStandardPaths::writableLocation(QStandardPaths::DocumentsLocation),
        "文档文件 (*.docx *.xlsx *.txt *.md);;所有文件 (*)");
    if (files.isEmpty()) return;

    // Upload progress dialog
    QDialog *dlg = new QDialog(this);
    dlg->setWindowTitle("上传文档");
    dlg->setFixedWidth(450);
    dlg->setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *dlgLayout = new QVBoxLayout(dlg);
    dlgLayout->setContentsMargins(24, 20, 24, 20);

    struct UploadItem { QLabel *nameLabel; QProgressBar *bar; QLabel *statusLabel; };
    QList<UploadItem> items;
    for (const QString &f : files) {
        QFileInfo fi(f);
        QHBoxLayout *row = new QHBoxLayout;
        QLabel *name = new QLabel(fi.fileName());
        name->setStyleSheet("font-size: 13px; color: #6B7280;");
        name->setMinimumWidth(200);
        QProgressBar *bar = new QProgressBar;
        bar->setRange(0, 100);
        bar->setFixedWidth(120);
        bar->setFixedHeight(6);
        bar->setTextVisible(false);
        bar->setStyleSheet("QProgressBar { background: #D1D5DB; border: none; border-radius: 3px; }"
                           "QProgressBar::chunk { background: #818CF8; border-radius: 3px; }");
        QLabel *status = new QLabel("等待中");
        status->setStyleSheet("font-size: 12px; color: #9CA3AF;");
        row->addWidget(name);
        row->addWidget(bar);
        row->addWidget(status);
        dlgLayout->addLayout(row);
        items.append({name, bar, status});
    }
    dlg->show();

    int *completed = new int(0);
    int total = files.size();
    for (int i = 0; i < files.size(); i++) {
        items[i].statusLabel->setText("上传中...");
        ApiClient::instance().uploadSourceDocument(files[i],
            [items, i](int progress) {
                items[i].bar->setValue(progress);
            },
            [this, items, i, completed, total, dlg](bool ok, const QJsonObject &, const QString &err) {
                if (ok) {
                    items[i].bar->setValue(100);
                    items[i].statusLabel->setText("\xe6\x88\x90\xe5\x8a\x9f");
                    items[i].statusLabel->setStyleSheet("font-size: 12px; color: #10B981;");
                } else {
                    items[i].statusLabel->setText("\xe5\xa4\xb1\xe8\xb4\xa5 " + err);
                    items[i].statusLabel->setStyleSheet("font-size: 12px; color: #EF4444;");
                }
                (*completed)++;
                if (*completed >= total) {
                    delete completed;
                    loadDocuments();
                    QTimer::singleShot(1500, dlg, &QDialog::accept);
                }
            });
    }
}

void DocumentListPage::deleteSelected() {
    QList<int> ids;
    for (int i = 0; i < m_table->rowCount(); i++) {
        QCheckBox *cb = findCheckBox(m_table->cellWidget(i, 0));
        if (cb && cb->isChecked()) ids.append(cb->property("docId").toInt());
    }
    if (ids.isEmpty()) return;
    if (QMessageBox::question(this, "确认", QString("确定删除选中的 %1 个文档？").arg(ids.size())) != QMessageBox::Yes) return;
    ApiClient::instance().batchDeleteDocuments(ids, [this, ids](bool ok, const QJsonObject &, const QString &) {
        if (ok) {
            Toast::showMessage(this, QString::fromUtf8("\xe5\xb7\xb2\xe5\x88\xa0\xe9\x99\xa4 %1 \xe4\xb8\xaa\xe6\x96\x87\xe6\xa1\xa3").arg(ids.size()), Toast::Success);
            loadDocuments();
        } else {
            Toast::showMessage(this, QString::fromUtf8("\xe5\x88\xa0\xe9\x99\xa4\xe5\xa4\xb1\xe8\xb4\xa5"), Toast::Error);
        }
    });
}

void DocumentListPage::downloadSelected() {
    QList<QPair<int,QString>> items;
    for (int i = 0; i < m_table->rowCount(); i++) {
        QCheckBox *cb = findCheckBox(m_table->cellWidget(i, 0));
        if (cb && cb->isChecked()) {
            int idx = cb->property("docIdx").toInt();
            items.append({cb->property("docId").toInt(), m_documents[idx].fileName});
        }
    }
    if (items.isEmpty()) return;

    QString saveDir = QFileDialog::getExistingDirectory(this,
        QString::fromUtf8("\xe9\x80\x89\xe6\x8b\xa9\xe4\xbf\x9d\xe5\xad\x98\xe7\x9b\xae\xe5\xbd\x95"),
        QStandardPaths::writableLocation(QStandardPaths::DesktopLocation));
    if (saveDir.isEmpty()) return;

    m_loading->showLoading(QString::fromUtf8("\xe4\xb8\x8b\xe8\xbd\xbd\xe4\xb8\xad..."));
    int *completed = new int(0);
    int *succeeded = new int(0);
    int total = items.size();
    for (const auto &item : items) {
        int docId = item.first;
        QString fn = item.second;
        ApiClient::instance().downloadSourceDocument(docId, [this, fn, saveDir, completed, succeeded, total](bool ok, const QByteArray &data, const QString &filename, const QString &) {
            if (ok) {
                QString savePath = saveDir + "/" + (filename.isEmpty() ? fn : filename);
                QFile f(savePath);
                if (f.open(QIODevice::WriteOnly)) { f.write(data); f.close(); (*succeeded)++; }
            }
            (*completed)++;
            if (*completed >= total) {
                m_loading->hideLoading();
                int s = *succeeded;
                delete completed;
                delete succeeded;
                Toast::showMessage(this, QString::fromUtf8("\xe6\x88\x90\xe5\x8a\x9f\xe4\xb8\x8b\xe8\xbd\xbd %1 \xe4\xb8\xaa\xe6\x96\x87\xe4\xbb\xb6").arg(s), Toast::Success);
            }
        });
    }
}

void DocumentListPage::onSelectionChanged() {
    int count = 0;
    for (int i = 0; i < m_table->rowCount(); i++) {
        QCheckBox *cb = findCheckBox(m_table->cellWidget(i, 0));
        if (cb && cb->isChecked()) count++;
    }
    m_deleteBtn->setEnabled(count > 0);
    m_downloadBtn->setEnabled(count > 0);
    m_deleteBtn->setText(count > 0 ? QString("\xe5\x88\xa0\xe9\x99\xa4\xe5\xb7\xb2\xe9\x80\x89(%1)").arg(count) : "\xe5\x88\xa0\xe9\x99\xa4\xe5\xb7\xb2\xe9\x80\x89");
    m_downloadBtn->setText(count > 0 ? QString("\xe4\xb8\x8b\xe8\xbd\xbd\xe5\xb7\xb2\xe9\x80\x89(%1)").arg(count) : "\xe4\xb8\x8b\xe8\xbd\xbd\xe5\xb7\xb2\xe9\x80\x89");
    updateStatsBar();
}

void DocumentListPage::updateStatsBar() {
    m_statsLabel->setText(QString("\xe5\x85\xb1 %1 \xe4\xb8\xaa\xe6\x96\x87\xe6\xa1\xa3").arg(m_documents.size()));
    int selCount = 0;
    for (int i = 0; i < m_table->rowCount(); i++) {
        QCheckBox *cb = findCheckBox(m_table->cellWidget(i, 0));
        if (cb && cb->isChecked()) selCount++;
    }
    if (selCount > 0) {
        m_selTag->setText(QString("\xe5\xb7\xb2\xe9\x80\x89 %1 \xe4\xb8\xaa").arg(selCount));
        m_selTag->show();
    } else {
        m_selTag->hide();
    }
}

void DocumentListPage::updatePagination() {
    int total = m_documents.size();
    int maxPage = qMax(1, (total + m_pageSize - 1) / m_pageSize);
    m_prevBtn->setEnabled(m_currentPage > 1);
    m_nextBtn->setEnabled(m_currentPage < maxPage);
    m_pageInfo->setText(QString("\xe5\x85\xb1 %1 \xe6\x9d\xa1\xef\xbc\x8c\xe7\xac\xac %2/%3 \xe9\xa1\xb5").arg(total).arg(m_currentPage).arg(maxPage));
}

QString DocumentListPage::formatSize(int bytes) {
    if (bytes < 1024) return QString::number(bytes) + " B";
    if (bytes < 1024 * 1024) return QString::number(bytes / 1024.0, 'f', 1) + " KB";
    return QString::number(bytes / (1024.0 * 1024.0), 'f', 1) + " MB";
}

void DocumentListPage::pollStatuses() {
    bool hasPending = false;
    for (const auto &doc : m_documents) {
        if (doc.status == "parsing" || doc.status == "uploaded") { hasPending = true; break; }
    }
    if (hasPending) loadDocuments();
}

void DocumentListPage::previewDocument(int docId, const QString &fileName, const QString &fileType) {
    QDialog *dlg = new QDialog(this);
    dlg->setWindowTitle(QString::fromUtf8("\xe9\xa2\x84\xe8\xa7\x88 - ") + fileName);
    dlg->resize(900, 650);
    dlg->setStyleSheet("QDialog { background: white; }");
    QVBoxLayout *layout = new QVBoxLayout(dlg);
    layout->setContentsMargins(0, 0, 0, 0);

    QTextEdit *viewer = new QTextEdit;
    viewer->setReadOnly(true);
    viewer->setStyleSheet("QTextEdit { border: none; padding: 20px; font-size: 14px; line-height: 1.6; }");
    layout->addWidget(viewer);

    viewer->setPlainText(QString::fromUtf8("\xe6\xad\xa3\xe5\x9c\xa8\xe5\x8a\xa0\xe8\xbd\xbd\xe6\x96\x87\xe6\xa1\xa3\xe5\x86\x85\xe5\xae\xb9..."));
    dlg->show();

    QString ext = fileType.toLower();

    if (ext == "txt" || ext == "md") {
        // Download raw file and display as text/markdown
        ApiClient::instance().downloadSourceDocument(docId, [viewer, ext](bool ok, const QByteArray &data, const QString &, const QString &err) {
            if (!ok) {
                viewer->setPlainText(QString::fromUtf8("\xe5\x8a\xa0\xe8\xbd\xbd\xe5\xa4\xb1\xe8\xb4\xa5: ") + err);
                return;
            }
            QString content = QString::fromUtf8(data);
            if (ext == "md") {
                viewer->setMarkdown(content);
            } else {
                viewer->setPlainText(content);
            }
        });
    } else if (ext == "docx" || ext == "xlsx") {
        // For binary formats: download to temp file and open with system app, show summary in dialog
        ApiClient::instance().downloadSourceDocument(docId, [this, viewer, fileName, ext](bool ok, const QByteArray &data, const QString &, const QString &err) {
            if (!ok) {
                viewer->setPlainText(QString::fromUtf8("\xe5\x8a\xa0\xe8\xbd\xbd\xe5\xa4\xb1\xe8\xb4\xa5: ") + err);
                return;
            }
            // Save to temp and open with system default application
            QString tempDir = QStandardPaths::writableLocation(QStandardPaths::TempLocation);
            QString tempPath = tempDir + "/docai_preview_" + fileName;
            QFile f(tempPath);
            if (f.open(QIODevice::WriteOnly)) {
                f.write(data);
                f.close();
                viewer->setPlainText(QString::fromUtf8("\xe5\xb7\xb2\xe5\x9c\xa8\xe7\xb3\xbb\xe7\xbb\x9f\xe9\xbb\x98\xe8\xae\xa4\xe5\xba\x94\xe7\x94\xa8\xe4\xb8\xad\xe6\x89\x93\xe5\xbc\x80\xe6\x96\x87\xe6\xa1\xa3\n\n")
                    + QString::fromUtf8("\xe4\xb8\xb4\xe6\x97\xb6\xe6\x96\x87\xe4\xbb\xb6\xe4\xbd\x8d\xe7\xbd\xae: ") + tempPath);
                QDesktopServices::openUrl(QUrl::fromLocalFile(tempPath));
            } else {
                viewer->setPlainText(QString::fromUtf8("\xe6\x97\xa0\xe6\xb3\x95\xe5\x88\x9b\xe5\xbb\xba\xe4\xb8\xb4\xe6\x97\xb6\xe6\x96\x87\xe4\xbb\xb6"));
            }
        });
    } else {
        viewer->setPlainText(QString::fromUtf8("\xe4\xb8\x8d\xe6\x94\xaf\xe6\x8c\x81\xe8\xaf\xa5\xe6\xa0\xbc\xe5\xbc\x8f\xe7\x9a\x84\xe9\xa2\x84\xe8\xa7\x88\xef\xbc\x8c\xe8\xaf\xb7\xe4\xb8\x8b\xe8\xbd\xbd\xe5\x90\x8e\xe6\x9f\xa5\xe7\x9c\x8b"));
    }
}

void DocumentListPage::toggleSelectAll(bool checked) {
    for (int i = 0; i < m_table->rowCount(); i++) {
        QCheckBox *cb = findCheckBox(m_table->cellWidget(i, 0));
        if (cb) cb->setChecked(checked);
    }
}
