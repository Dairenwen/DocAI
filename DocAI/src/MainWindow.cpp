#include "MainWindow.h"
#include "utils/TokenManager.h"
#include "network/ApiClient.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QMessageBox>
#include <QDialog>
#include <QLineEdit>
#include <QLabel>
#include <QPushButton>
#include <QApplication>
#include <QMenu>
#include <QAction>
#include <QPainter>
#include <QPainterPath>
#include <QParallelAnimationGroup>
#include <cstdlib>
#include <ctime>
#include <QMouseEvent>
#include <QResizeEvent>
#include <QEvent>
#include <QKeyEvent>
#include <QShortcut>
#include <QPropertyAnimation>
#include <QGraphicsOpacityEffect>
#include <QEasingCurve>

#ifdef Q_OS_WIN
#include <windows.h>
#include <windowsx.h>
#endif

static const int SHADOW_WIDTH = 10;
static const int CORNER_RADIUS = 10;

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent), m_forceQuit(false), m_resizeEdge(0) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
    setAttribute(Qt::WA_TranslucentBackground, true);
    setWindowTitle("DocAI");
    resize(1280 + SHADOW_WIDTH * 2, 800 + SHADOW_WIDTH * 2);
    setMinimumSize(1215 + SHADOW_WIDTH * 2, 680 + SHADOW_WIDTH * 2);

    setupUI();
    setupTrayIcon();

    // Alt+1..4: Navigate to pages
    for (int i = 0; i < 4; i++) {
        QShortcut *sc = new QShortcut(QKeySequence(Qt::ALT + Qt::Key_1 + i), this);
        connect(sc, &QShortcut::activated, [this, i]() {
            if (m_rootStack->currentWidget() == m_mainApp) {
                m_sidebar->setCurrentIndex(i);
                onPageChanged(i);
            }
        });
    }
    // Ctrl+N: New AI conversation
    connect(new QShortcut(QKeySequence("Ctrl+N"), this), &QShortcut::activated, [this]() {
        if (m_rootStack->currentWidget() == m_mainApp) {
            m_sidebar->setCurrentIndex(3);
            onPageChanged(3);
        }
    });

    // Enable mouse tracking on all child widgets for resize cursor
    setMouseTracking(true);
    for (QWidget *w : findChildren<QWidget*>())
        w->setMouseTracking(true);
    qApp->installEventFilter(this);

    connect(&ApiClient::instance(), &ApiClient::tokenExpired, this, &MainWindow::onTokenExpired);

    if (TokenManager::instance().isLoggedIn())
        showMainApp();
    else
        showLogin();
}

void MainWindow::setupUI() {
    // Shadow container: margins around content for shadow rendering
    QWidget *shadowContainer = new QWidget;
    shadowContainer->setObjectName("shadowContainer");
    shadowContainer->setStyleSheet("#shadowContainer { background: transparent; }");
    setCentralWidget(shadowContainer);

    QVBoxLayout *shadowLayout = new QVBoxLayout(shadowContainer);
    shadowLayout->setContentsMargins(SHADOW_WIDTH, SHADOW_WIDTH, SHADOW_WIDTH, SHADOW_WIDTH);
    shadowLayout->setSpacing(0);

    m_rootStack = new QStackedWidget;
    m_rootStack->setObjectName("rootStack");
    m_rootStack->setStyleSheet("#rootStack { background: white; border-radius: 10px; }");
    shadowLayout->addWidget(m_rootStack);

    // Login page
    m_loginPage = new LoginPage;
    connect(m_loginPage, &LoginPage::loginSuccess, this, &MainWindow::onLoginSuccess);
    m_rootStack->addWidget(m_loginPage);

    // Main app container
    m_mainApp = new QWidget;
    QHBoxLayout *appLayout = new QHBoxLayout(m_mainApp);
    appLayout->setContentsMargins(0, 0, 0, 0);
    appLayout->setSpacing(0);

    m_sidebar = new Sidebar;
    connect(m_sidebar, &Sidebar::pageChanged, this, &MainWindow::onPageChanged);

    QWidget *rightSide = new QWidget;
    QVBoxLayout *rightLayout = new QVBoxLayout(rightSide);
    rightLayout->setContentsMargins(0, 0, 0, 0);
    rightLayout->setSpacing(0);

    m_topBar = new TopBar;
    connect(m_topBar, &TopBar::logoutClicked, this, &MainWindow::onLogout);
    connect(m_topBar, &TopBar::changePasswordClicked, this, &MainWindow::onChangePassword);
    connect(m_topBar, &TopBar::minimizeClicked, this, &QWidget::showMinimized);
    connect(m_topBar, &TopBar::maximizeClicked, [this]() {
        // Fade out slightly with easing curve
        QPropertyAnimation *fadeOut = new QPropertyAnimation(this, "windowOpacity");
        fadeOut->setDuration(150);
        fadeOut->setStartValue(1.0);
        fadeOut->setEndValue(0.92);
        fadeOut->setEasingCurve(QEasingCurve::InCubic);
        connect(fadeOut, &QPropertyAnimation::finished, [this]() {
            if (isMaximized()) {
                showNormal();
                centralWidget()->layout()->setContentsMargins(SHADOW_WIDTH, SHADOW_WIDTH, SHADOW_WIDTH, SHADOW_WIDTH);
            } else {
                showMaximized();
                centralWidget()->layout()->setContentsMargins(0, 0, 0, 0);
            }
            // Fade back in with easing curve
            QPropertyAnimation *fadeIn = new QPropertyAnimation(this, "windowOpacity");
            fadeIn->setDuration(250);
            fadeIn->setStartValue(0.92);
            fadeIn->setEndValue(1.0);
            fadeIn->setEasingCurve(QEasingCurve::OutCubic);
            fadeIn->start(QAbstractAnimation::DeleteWhenStopped);
        });
        fadeOut->start(QAbstractAnimation::DeleteWhenStopped);
    });
    connect(m_topBar, &TopBar::closeClicked, [this]() { hide(); });

    m_pageStack = new QStackedWidget;
    m_dashboardPage = new DashboardPage;
    m_documentListPage = new DocumentListPage;
    m_autoFillPage = new AutoFillPage;
    m_aiChatPage = new AIChatPage;

    connect(m_dashboardPage, &DashboardPage::navigateTo, [this](int idx) {
        m_sidebar->setCurrentIndex(idx);
        onPageChanged(idx);
    });

    connect(m_documentListPage, &DocumentListPage::navigateToChat, [this](int docId, const QString &docName) {
        m_aiChatPage->linkDocumentAndOpenChat(docId, docName);
        m_sidebar->setCurrentIndex(3);
        onPageChanged(3);
    });

    connect(m_documentListPage, &DocumentListPage::documentsChanged, [this]() {
        m_dashboardPage->refreshStats();
        m_autoFillPage->loadDocStats();
    });

    m_pageStack->addWidget(m_dashboardPage);
    m_pageStack->addWidget(m_documentListPage);
    m_pageStack->addWidget(m_autoFillPage);
    m_pageStack->addWidget(m_aiChatPage);

    rightLayout->addWidget(m_topBar);
    rightLayout->addWidget(m_pageStack, 1);

    appLayout->addWidget(m_sidebar);
    appLayout->addWidget(rightSide, 1);

    m_rootStack->addWidget(m_mainApp);
}

void MainWindow::onLoginSuccess() {
    showMainApp();
}

void MainWindow::showLogin() {
    m_rootStack->setCurrentWidget(m_loginPage);
}

void MainWindow::showMainApp() {
    m_topBar->setNickname(TokenManager::instance().nickname());
    m_rootStack->setCurrentWidget(m_mainApp);
    m_sidebar->setCurrentIndex(0);
    onPageChanged(0);
    // Ensure dashboard stats are loaded with valid token
    m_dashboardPage->refreshStats();
}

void MainWindow::onLogout() {
    ApiClient::instance().logout([this](bool, const QJsonObject &, const QString &) {
        TokenManager::instance().clear();
        showLogin();
    });
}

void MainWindow::onPageChanged(int index) {
    int oldIndex = m_pageStack->currentIndex();

    static const QStringList titles = {"\xe5\xb7\xa5\xe4\xbd\x9c\xe5\x8f\xb0", "\xe6\x96\x87\xe6\xa1\xa3\xe7\xae\xa1\xe7\x90\x86", "\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe8\xa1\xa8", "AI \xe5\xaf\xb9\xe8\xaf\x9d", "AI \xe5\x86\x99\xe4\xbd\x9c"};
    if (index >= 0 && index < titles.size())
        m_topBar->setTitle(titles[index]);

    if (oldIndex == index) return;

    QWidget *newPage = m_pageStack->widget(index);
    newPage->resize(m_pageStack->size());
    QPixmap newShot = newPage->grab();

    // Random direction: 0=left, 1=right, 2=top, 3=bottom
    static int lastDir = -1;
    int dir;
    do { dir = std::rand() % 4; } while (dir == lastDir);
    lastDir = dir;

    QPoint startPos;
    int w = m_pageStack->width();
    int h = m_pageStack->height();
    switch (dir) {
        case 0: startPos = QPoint(-w, 0); break; // from left
        case 1: startPos = QPoint(w, 0); break;  // from right
        case 2: startPos = QPoint(0, -h); break;  // from top
        default: startPos = QPoint(0, h); break;  // from bottom
    }

    // Dark overlay on old page
    QWidget *darkOverlay = new QWidget(m_pageStack);
    darkOverlay->setFixedSize(m_pageStack->size());
    darkOverlay->move(0, 0);
    darkOverlay->setStyleSheet("background: rgba(0,0,0,38);");
    darkOverlay->show();
    darkOverlay->raise();
    QGraphicsOpacityEffect *darkEffect = new QGraphicsOpacityEffect(darkOverlay);
    darkOverlay->setGraphicsEffect(darkEffect);
    darkEffect->setOpacity(0.0);

    // Slide panel with new page snapshot
    QLabel *slidePanel = new QLabel(m_pageStack);
    slidePanel->setPixmap(newShot);
    slidePanel->setFixedSize(m_pageStack->size());
    slidePanel->move(startPos);
    slidePanel->show();
    slidePanel->raise();

    QPropertyAnimation *slideAnim = new QPropertyAnimation(slidePanel, "pos");
    slideAnim->setDuration(280);
    slideAnim->setStartValue(startPos);
    slideAnim->setEndValue(QPoint(0, 0));
    slideAnim->setEasingCurve(QEasingCurve::OutCubic);

    QPropertyAnimation *darkAnim = new QPropertyAnimation(darkEffect, "opacity");
    darkAnim->setDuration(280);
    darkAnim->setStartValue(0.0);
    darkAnim->setEndValue(1.0);
    darkAnim->setEasingCurve(QEasingCurve::OutCubic);

    QParallelAnimationGroup *group = new QParallelAnimationGroup;
    group->addAnimation(slideAnim);
    group->addAnimation(darkAnim);

    connect(group, &QParallelAnimationGroup::finished, [this, index, darkOverlay, slidePanel]() {
        m_pageStack->setCurrentIndex(index);
        darkOverlay->deleteLater();
        slidePanel->deleteLater();
    });

    group->start(QAbstractAnimation::DeleteWhenStopped);
}

void MainWindow::onChangePassword() {
    QDialog dlg(this);
    dlg.setWindowTitle("\xe4\xbf\xae\xe6\x94\xb9\xe5\xaf\x86\xe7\xa0\x81");
    dlg.setFixedSize(400, 260);

    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(28, 24, 28, 24);
    layout->setSpacing(14);

    auto makeLine = [](const QString &placeholder) {
        QLineEdit *le = new QLineEdit;
        le->setPlaceholderText(placeholder);
        le->setEchoMode(QLineEdit::Password);
        le->setMinimumHeight(38);
        return le;
    };
    QLineEdit *oldPwdEdit = makeLine("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe5\xbd\x93\xe5\x89\x8d\xe5\xaf\x86\xe7\xa0\x81");
    QLineEdit *newPwdEdit = makeLine("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe6\x96\xb0\xe5\xaf\x86\xe7\xa0\x81");
    QLineEdit *confirmEdit = makeLine("\xe7\xa1\xae\xe8\xae\xa4\xe6\x96\xb0\xe5\xaf\x86\xe7\xa0\x81");
    layout->addWidget(new QLabel("\xe5\xbd\x93\xe5\x89\x8d\xe5\xaf\x86\xe7\xa0\x81"));
    layout->addWidget(oldPwdEdit);
    layout->addWidget(new QLabel("\xe6\x96\xb0\xe5\xaf\x86\xe7\xa0\x81"));
    layout->addWidget(newPwdEdit);
    layout->addWidget(new QLabel("\xe7\xa1\xae\xe8\xae\xa4\xe5\xaf\x86\xe7\xa0\x81"));
    layout->addWidget(confirmEdit);

    QPushButton *btn = new QPushButton("\xe7\xa1\xae\xe8\xae\xa4\xe4\xbf\xae\xe6\x94\xb9");
    btn->setMinimumHeight(40);
    btn->setStyleSheet(
        "QPushButton { background: #4F46E5; color: white; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; }"
        "QPushButton:hover { background: #818CF8; }"
        "QPushButton:pressed { background: #3730A3; }");
    layout->addWidget(btn);

    connect(btn, &QPushButton::clicked, [&]() {
        if (newPwdEdit->text() != confirmEdit->text()) {
            QMessageBox::warning(&dlg, "\xe9\x94\x99\xe8\xaf\xaf", "\xe4\xb8\xa4\xe6\xac\xa1\xe5\xaf\x86\xe7\xa0\x81\xe4\xb8\x8d\xe4\xb8\x80\xe8\x87\xb4");
            return;
        }
        ApiClient::instance().changePassword(oldPwdEdit->text(), newPwdEdit->text(),
            [this, &dlg](bool ok, const QJsonObject &, const QString &err) {
                if (ok) { QMessageBox::information(this, "\xe6\x88\x90\xe5\x8a\x9f", "\xe5\xaf\x86\xe7\xa0\x81\xe4\xbf\xae\xe6\x94\xb9\xe6\x88\x90\xe5\x8a\x9f"); dlg.accept(); }
                else QMessageBox::warning(this, "\xe5\xa4\xb1\xe8\xb4\xa5", err);
            });
    });

    dlg.exec();
}

void MainWindow::onTokenExpired() {
    TokenManager::instance().clear();
    QMessageBox::warning(this, "登录已过期", "登录凭证已过期，请重新登录");
    showLogin();
}

void MainWindow::setupTrayIcon() {
    m_trayIcon = new QSystemTrayIcon(windowIcon(), this);
    m_trayMenu = new QMenu(this);

    QAction *showAction = m_trayMenu->addAction("\xe6\x98\xbe\xe7\xa4\xba\xe4\xb8\xbb\xe7\xaa\x97\xe5\x8f\xa3");
    connect(showAction, &QAction::triggered, [this]() {
        show();
        raise();
        activateWindow();
    });

    m_trayMenu->addSeparator();

    QAction *quitAction = m_trayMenu->addAction("\xe9\x80\x80\xe5\x87\xba");
    connect(quitAction, &QAction::triggered, [this]() {
        m_forceQuit = true;
        qApp->quit();
    });

    m_trayIcon->setContextMenu(m_trayMenu);
    m_trayIcon->setToolTip("DocAI");
    connect(m_trayIcon, &QSystemTrayIcon::activated, this, &MainWindow::onTrayActivated);
    m_trayIcon->show();
}

void MainWindow::closeEvent(QCloseEvent *event) {
    if (m_forceQuit) {
        event->accept();
    } else {
        event->ignore();
        hide();
    }
}

void MainWindow::onTrayActivated(QSystemTrayIcon::ActivationReason reason) {
    if (reason == QSystemTrayIcon::DoubleClick || reason == QSystemTrayIcon::Trigger) {
        if (isVisible()) {
            hide();
        } else {
            show();
            raise();
            activateWindow();
        }
    }
}

void MainWindow::resizeEvent(QResizeEvent *event) {
    QMainWindow::resizeEvent(event);
}

void MainWindow::paintEvent(QPaintEvent *) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);

    QRect contentRect = rect().adjusted(SHADOW_WIDTH, SHADOW_WIDTH, -SHADOW_WIDTH, -SHADOW_WIDTH);
    if (isMaximized()) {
        contentRect = rect();
    }

    // Draw shadow layers (only when not maximized)
    if (!isMaximized()) {
        for (int i = 0; i < SHADOW_WIDTH; i++) {
            QRect shadowRect = contentRect.adjusted(-i, -i, i, i);
            int alpha = 3 + (SHADOW_WIDTH - i) * 2;
            QPainterPath path;
            path.addRoundedRect(shadowRect, CORNER_RADIUS + i, CORNER_RADIUS + i);
            painter.setPen(Qt::NoPen);
            painter.setBrush(QColor(0, 0, 0, alpha));
            painter.drawPath(path);
        }
    }

    // Draw main background
    QPainterPath bgPath;
    int radius = isMaximized() ? 0 : CORNER_RADIUS;
    bgPath.addRoundedRect(contentRect, radius, radius);
    painter.setPen(Qt::NoPen);
    painter.setBrush(QColor("white"));
    painter.drawPath(bgPath);
}

static int hitTestEdge(const QPoint &pos, const QRect &rect, int margin) {
    int edge = 0;
    if (pos.x() <= rect.left() + margin) edge |= 1; // left
    if (pos.x() >= rect.right() - margin) edge |= 2; // right
    if (pos.y() <= rect.top() + margin) edge |= 4; // top
    if (pos.y() >= rect.bottom() - margin) edge |= 8; // bottom
    return edge;
}

void MainWindow::mousePressEvent(QMouseEvent *event) {
    if (isMaximized() || event->button() != Qt::LeftButton) {
        QMainWindow::mousePressEvent(event);
        return;
    }
    int edge = hitTestEdge(event->pos(), rect(), SHADOW_WIDTH + 4);
    if (edge) {
        m_resizeEdge = edge;
        m_resizePressPos = event->globalPos();
        m_resizePressGeometry = geometry();
    }
    QMainWindow::mousePressEvent(event);
}

void MainWindow::mouseMoveEvent(QMouseEvent *event) {
    if (m_resizeEdge && (event->buttons() & Qt::LeftButton)) {
        QPoint diff = event->globalPos() - m_resizePressPos;
        QRect newGeo = m_resizePressGeometry;
        if (m_resizeEdge & 1) newGeo.setLeft(newGeo.left() + diff.x());
        if (m_resizeEdge & 2) newGeo.setRight(newGeo.right() + diff.x());
        if (m_resizeEdge & 4) newGeo.setTop(newGeo.top() + diff.y());
        if (m_resizeEdge & 8) newGeo.setBottom(newGeo.bottom() + diff.y());
        if (newGeo.width() >= minimumWidth() && newGeo.height() >= minimumHeight())
            setGeometry(newGeo);
        return;
    }
    // Update cursor shape
    if (!isMaximized()) {
        int edge = hitTestEdge(event->pos(), rect(), SHADOW_WIDTH + 4);
        if ((edge & 1) && (edge & 4)) setCursor(Qt::SizeFDiagCursor);
        else if ((edge & 2) && (edge & 8)) setCursor(Qt::SizeFDiagCursor);
        else if ((edge & 1) && (edge & 8)) setCursor(Qt::SizeBDiagCursor);
        else if ((edge & 2) && (edge & 4)) setCursor(Qt::SizeBDiagCursor);
        else if (edge & 1 || edge & 2) setCursor(Qt::SizeHorCursor);
        else if (edge & 4 || edge & 8) setCursor(Qt::SizeVerCursor);
        else setCursor(Qt::ArrowCursor);
    }
    QMainWindow::mouseMoveEvent(event);
}

void MainWindow::mouseReleaseEvent(QMouseEvent *event) {
    m_resizeEdge = 0;
    QMainWindow::mouseReleaseEvent(event);
}

bool MainWindow::eventFilter(QObject *obj, QEvent *event) {
    if (event->type() == QEvent::MouseMove && !isMaximized() && !m_resizeEdge) {
        QMouseEvent *me = static_cast<QMouseEvent*>(event);
        QPoint pos = mapFromGlobal(me->globalPos());
        if (rect().contains(pos)) {
            int edge = hitTestEdge(pos, rect(), SHADOW_WIDTH + 5);
            if (edge) {
                Qt::CursorShape shape = Qt::ArrowCursor;
                if ((edge & 1) && (edge & 4)) shape = Qt::SizeFDiagCursor;
                else if ((edge & 2) && (edge & 8)) shape = Qt::SizeFDiagCursor;
                else if ((edge & 1) && (edge & 8)) shape = Qt::SizeBDiagCursor;
                else if ((edge & 2) && (edge & 4)) shape = Qt::SizeBDiagCursor;
                else if (edge & 1 || edge & 2) shape = Qt::SizeHorCursor;
                else if (edge & 4 || edge & 8) shape = Qt::SizeVerCursor;
                if (!m_overrideCursor) {
                    QApplication::setOverrideCursor(shape);
                    m_overrideCursor = true;
                } else {
                    QApplication::changeOverrideCursor(shape);
                }
            } else if (m_overrideCursor) {
                QApplication::restoreOverrideCursor();
                m_overrideCursor = false;
            }
        } else if (m_overrideCursor) {
            QApplication::restoreOverrideCursor();
            m_overrideCursor = false;
        }
    }
    return QMainWindow::eventFilter(obj, event);
}

bool MainWindow::nativeEvent(const QByteArray &eventType, void *message, long *result) {
#ifdef Q_OS_WIN
    MSG *msg = static_cast<MSG*>(message);
    if (msg->message == WM_NCHITTEST && !isMaximized()) {
        POINT pt = { GET_X_LPARAM(msg->lParam), GET_Y_LPARAM(msg->lParam) };
        RECT winRect;
        GetWindowRect(msg->hwnd, &winRect);
        int x = pt.x - winRect.left;
        int y = pt.y - winRect.top;
        int w = winRect.right - winRect.left;
        int h = winRect.bottom - winRect.top;
        int border = SHADOW_WIDTH;

        // Outside the content area (in the shadow region): transparent to system
        bool inLeft   = x < border;
        bool inRight  = x >= w - border;
        bool inTop    = y < border;
        bool inBottom = y >= h - border;

        if ((inLeft || inRight) && (inTop || inBottom)) {
            // Corner shadow area: allow resize
            if (inLeft && inTop) { *result = HTTOPLEFT; return true; }
            if (inRight && inTop) { *result = HTTOPRIGHT; return true; }
            if (inLeft && inBottom) { *result = HTBOTTOMLEFT; return true; }
            if (inRight && inBottom) { *result = HTBOTTOMRIGHT; return true; }
        }
        if (inLeft) { *result = HTLEFT; return true; }
        if (inRight) { *result = HTRIGHT; return true; }
        if (inTop) { *result = HTTOP; return true; }
        if (inBottom) { *result = HTBOTTOM; return true; }
    }
#endif
    return QMainWindow::nativeEvent(eventType, message, result);
}
