#ifndef AUTOFILLPAGE_H
#define AUTOFILLPAGE_H

#include <QWidget>
#include <QStackedWidget>
#include <QPushButton>
#include <QLabel>
#include <QListWidget>
#include <QTextEdit>
#include <QLineEdit>
#include <QProgressBar>
#include <QFrame>
#include <QEvent>
#include <QDragEnterEvent>
#include <QDropEvent>
#include <QTabWidget>
#include <QTableWidget>
#include <QElapsedTimer>
#include "../models/DataModels.h"

class AutoFillPage : public QWidget {
    Q_OBJECT
public:
    explicit AutoFillPage(QWidget *parent = nullptr);
    void loadDocStats();

protected:
    bool eventFilter(QObject *obj, QEvent *event) override;
    void dragEnterEvent(QDragEnterEvent *event) override;
    void dropEvent(QDropEvent *event) override;

private slots:
    void goToStep(int step);
    void selectTemplates();
    void openSourceDialog();
    void startFilling();
    void downloadResult(int templateId);
    void sendResultEmail(int templateId);
    void viewAudit(int templateId);

private:
    void setupUI();
    QWidget* createStep1();
    QWidget* createStep2();
    QWidget* createStep3();
    void showResults();
    void rebuildTemplateList();
    void loadDecisions();
    void buildDetailTab();
    void buildAnalysisTab();
    QString decisionModeLabel(const QString &mode);
    QString decisionModeColor(const QString &mode);

    int m_currentStep = 0;
    QStackedWidget *m_stepStack;
    QList<QLabel*> m_stepLabels;
    QList<QWidget*> m_stepDots;
    QList<QFrame*> m_stepLines;

    // Step 1
    QLabel *m_docCountLabel;
    QLabel *m_extractedCountLabel;
    QListWidget *m_selectedSourceList;
    QListWidget *m_templateFileList;
    QPushButton *m_addTemplateBtn;
    QPushButton *m_clearTemplateBtn;
    QPushButton *m_selectSourceBtn;
    QPushButton *m_nextBtn;
    QTextEdit *m_userRequirementEdit;

    // Step 2
    QLabel *m_fillStatusLabel;
    QProgressBar *m_fillProgress;
    QLabel *m_fillDetailLabel;

    // Step 3
    QWidget *m_resultSummary;
    QLabel *m_resultSummaryLabel;
    QListWidget *m_resultList;
    QTabWidget *m_resultTabs;
    QTableWidget *m_detailTable;
    QWidget *m_analysisWidget;

    // Data
    QList<SourceDocument> m_allDocs;
    QList<int> m_selectedSourceIds;
    QStringList m_templateFilePaths;
    struct TemplateResult {
        int templateId;
        QString fileName;
        QString status;
    };
    QList<TemplateResult> m_results;

    struct FillDecision {
        QString slotLabel;
        QString finalValue;
        double finalConfidence;
        QString decisionMode;
        QString reason;
    };
    QList<FillDecision> m_decisions;
    QMap<int, QPair<QByteArray, QString>> m_downloadedBlobs;
    QElapsedTimer m_fillTimer;
};

#endif // AUTOFILLPAGE_H
