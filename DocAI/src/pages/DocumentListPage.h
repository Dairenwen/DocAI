#ifndef DOCUMENTLISTPAGE_H
#define DOCUMENTLISTPAGE_H

#include <QWidget>
#include <QTableWidget>
#include <QLineEdit>
#include <QComboBox>
#include <QPushButton>
#include <QLabel>
#include <QTimer>
#include <QStackedWidget>
#include <QCheckBox>
#include "../models/DataModels.h"

class LoadingOverlay;

class DocumentListPage : public QWidget {
    Q_OBJECT
public:
    explicit DocumentListPage(QWidget *parent = nullptr);

signals:
    void navigateToChat(int docId, const QString &docName);

private slots:
    void loadDocuments();
    void uploadDocuments();
    void deleteSelected();
    void downloadSelected();
    void onSelectionChanged();
    void toggleSelectAll(bool checked);

private:
    void setupUI();
    void pollStatuses();
    void previewDocument(int docId, const QString &fileName, const QString &fileType);
    void updateStatsBar();
    void updatePagination();
    void renderPage();
    static QString formatSize(int bytes);

    QLineEdit *m_searchEdit;
    QComboBox *m_typeFilter;
    QTableWidget *m_table;
    QPushButton *m_uploadBtn;
    QPushButton *m_deleteBtn;
    QPushButton *m_downloadBtn;
    QLabel *m_statsLabel;
    QLabel *m_selTag;
    QLabel *m_searchTag;
    QWidget *m_emptyState;
    QStackedWidget *m_stack;
    QWidget *m_paginationWidget;
    QLabel *m_pageInfo;
    QPushButton *m_prevBtn;
    QPushButton *m_nextBtn;
    QTimer *m_pollTimer;
    QList<SourceDocument> m_documents;
    int m_currentPage;
    int m_pageSize;
    LoadingOverlay *m_loading;
    QCheckBox *m_selectAllCb;
    int m_sortColumn = -1;
    Qt::SortOrder m_sortOrder = Qt::AscendingOrder;
};

#endif // DOCUMENTLISTPAGE_H
