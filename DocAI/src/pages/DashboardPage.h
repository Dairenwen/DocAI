#ifndef DASHBOARDPAGE_H
#define DASHBOARDPAGE_H

#include <QWidget>
#include <QLabel>
#include <QEvent>

class DashboardPage : public QWidget {
    Q_OBJECT
public:
    explicit DashboardPage(QWidget *parent = nullptr);
    void refreshStats();

signals:
    void navigateTo(int pageIndex);

private:
    void setupUI();
    QWidget* makeStatCard(const QString &icon, const QString &label, const QString &bg, const QString &color);
    QWidget* makeGuideStep(int num, const QString &icon, const QString &title, const QString &desc, const QString &bgColor, int targetPage);
    QWidget* makeModuleCard(const QString &num, const QString &name, const QString &desc, const QStringList &features, int targetPage);

    bool event(QEvent *e) override;
    bool eventFilter(QObject *obj, QEvent *event) override;

    QLabel *m_totalLabel;
    QLabel *m_docxLabel;
    QLabel *m_xlsxLabel;
    QLabel *m_txtLabel;
};

#endif // DASHBOARDPAGE_H
