#ifndef TOPBAR_H
#define TOPBAR_H

#include <QWidget>
#include <QLabel>
#include <QPushButton>
#include <QMenu>
#include <QPoint>

class TopBar : public QWidget {
    Q_OBJECT
public:
    explicit TopBar(QWidget *parent = nullptr);
    void setTitle(const QString &title);
    void setModuleTag(const QString &tag);
    void setNickname(const QString &name);

signals:
    void logoutClicked();
    void changePasswordClicked();
    void minimizeClicked();
    void maximizeClicked();
    void closeClicked();

protected:
    void mousePressEvent(QMouseEvent *event) override;
    void mouseMoveEvent(QMouseEvent *event) override;
    void mouseReleaseEvent(QMouseEvent *event) override;
    void mouseDoubleClickEvent(QMouseEvent *event) override;
    bool eventFilter(QObject *obj, QEvent *event) override;

private:
    void setupUI();
    QLabel *m_titleLabel;
    QLabel *m_tagLabel;
    QLabel *m_statusDot;
    QLabel *m_statusText;
    QLabel *m_avatarLabel;
    QLabel *m_nameLabel;
    QPushButton *m_userBtn;
    QPushButton *m_minBtn;
    QPushButton *m_maxBtn;
    QPushButton *m_closeBtn;
    QMenu *m_menu;
    bool m_dragging = false;
    QPoint m_dragPos;
};

#endif // TOPBAR_H
