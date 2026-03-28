#ifndef AIGENERATEPAGE_H
#define AIGENERATEPAGE_H

#include <QWidget>
#include <QLineEdit>
#include <QTextEdit>
#include <QPushButton>
#include <QLabel>
#include "../network/SseClient.h"

class AIGeneratePage : public QWidget {
    Q_OBJECT
public:
    explicit AIGeneratePage(QWidget *parent = nullptr);

private slots:
    void generate();
    void copyResult();
    void exportResult();
    void sendEmail();

private:
    void setupUI();

    // Config panel
    QString m_selectedType;
    QList<QPushButton*> m_typeBtns;
    QLineEdit *m_titleEdit;
    QTextEdit *m_requirementEdit;
    QPushButton *m_generateBtn;

    // Preview panel
    QTextEdit *m_resultDisplay;
    QWidget *m_emptyState;
    QWidget *m_loadingState;
    QWidget *m_resultToolbar;
    QLabel *m_wordCountLabel;
    SseClient *m_sse;
    QString m_generatedContent;
    bool m_generating = false;
};

#endif // AIGENERATEPAGE_H
