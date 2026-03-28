#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QStackedWidget>
#include <QSystemTrayIcon>
#include <QCloseEvent>
#include "pages/LoginPage.h"
#include "pages/DashboardPage.h"
#include "pages/DocumentListPage.h"
#include "pages/AutoFillPage.h"
#include "pages/AIChatPage.h"
#include "widgets/Sidebar.h"
#include "widgets/TopBar.h"

class QMenu;

class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(QWidget *parent = nullptr);

protected:
    void closeEvent(QCloseEvent *event) override;
    void paintEvent(QPaintEvent *event) override;
    void mousePressEvent(QMouseEvent *event) override;
    void mouseMoveEvent(QMouseEvent *event) override;
    void mouseReleaseEvent(QMouseEvent *event) override;
    bool eventFilter(QObject *obj, QEvent *event) override;

private slots:
    void onLoginSuccess();
    void onLogout();
    void onPageChanged(int index);
    void onChangePassword();
    void onTokenExpired();
    void onTrayActivated(QSystemTrayIcon::ActivationReason reason);

private:
    void setupUI();
    void setupTrayIcon();
    void showLogin();
    void showMainApp();

    QStackedWidget *m_rootStack;

    // Login
    LoginPage *m_loginPage;

    // Main app container
    QWidget *m_mainApp;
    Sidebar *m_sidebar;
    TopBar *m_topBar;
    QStackedWidget *m_pageStack;

    // System tray
    QSystemTrayIcon *m_trayIcon;
    QMenu *m_trayMenu;
    bool m_forceQuit;

    // Resize
    int m_resizeEdge;
    QPoint m_resizePressPos;
    QRect m_resizePressGeometry;
    bool m_overrideCursor = false;

    // Pages
    DashboardPage *m_dashboardPage;
    DocumentListPage *m_documentListPage;
    AutoFillPage *m_autoFillPage;
    AIChatPage *m_aiChatPage;
};

#endif // MAINWINDOW_H
