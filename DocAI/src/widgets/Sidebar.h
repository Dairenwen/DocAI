#ifndef SIDEBAR_H
#define SIDEBAR_H

#include <QWidget>
#include <QVBoxLayout>
#include <QLabel>
#include <QList>
#include <QEvent>
#include <QTimer>

struct SidebarFloatingChar {
    double x;        // 0..1 horizontal position
    double y;        // 0..1 vertical position within float area
    double rotation; // degrees
    double opacity;  // 0..1
    double speed;    // upward speed per tick
    QString ch;
    double size;
};

class Sidebar : public QWidget {
    Q_OBJECT
public:
    explicit Sidebar(QWidget *parent = nullptr);
    void setCurrentIndex(int index);
    void setCollapsed(bool collapsed);
    bool isCollapsed() const { return m_collapsed; }

signals:
    void pageChanged(int index);
    void collapseToggled(bool collapsed);

protected:
    void paintEvent(QPaintEvent *e) override;
    bool event(QEvent *e) override;
    bool eventFilter(QObject *obj, QEvent *event) override;

private:
    void setupUI();
    void updateSelection();
    void initFloatingChars();
    void animateFloatingChars();

    struct NavItem {
        QWidget *container;
        QLabel *iconLabel;
        QLabel *textLabel;
        QWidget *indicator;
    };

    QList<NavItem> m_items;
    int m_currentIndex = 0;
    bool m_collapsed = false;
    QVBoxLayout *m_navLayout;
    QWidget *m_logoTextWidget;
    QWidget *m_collapseWidget;
    QLabel *m_collapseIcon;
    QLabel *m_collapseLbl;
    QList<SidebarFloatingChar> m_floatChars;
    QTimer *m_floatTimer;
};

#endif // SIDEBAR_H
