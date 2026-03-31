#include "TopBar.h"
#include <QHBoxLayout>
#include <QMouseEvent>
#include <QEvent>
#include <QApplication>
#include <QPainter>
#include <QPixmap>

static QIcon makeIconFromPainter(int size, std::function<void(QPainter&, int)> drawFn) {
    QPixmap pix(size, size);
    pix.fill(Qt::transparent);
    QPainter p(&pix);
    p.setRenderHint(QPainter::Antialiasing);
    drawFn(p, size);
    p.end();
    return QIcon(pix);
}

static QIcon minimizeIcon() {
    return makeIconFromPainter(16, [](QPainter &p, int s) {
        p.setPen(QPen(QColor("#9CA3AF"), 1.5));
        p.drawLine(3, s / 2, s - 3, s / 2);
    });
}

static QIcon maximizeIcon() {
    return makeIconFromPainter(16, [](QPainter &p, int s) {
        p.setPen(QPen(QColor("#9CA3AF"), 1.5));
        p.drawRect(3, 3, s - 7, s - 7);
    });
}

static QIcon closeIcon() {
    return makeIconFromPainter(16, [](QPainter &p, int s) {
        p.setPen(QPen(QColor("#9CA3AF"), 1.5));
        p.drawLine(4, 4, s - 4, s - 4);
        p.drawLine(s - 4, 4, 4, s - 4);
    });
}

TopBar::TopBar(QWidget *parent) : QWidget(parent) {
    setupUI();
}

void TopBar::setupUI() {
    setObjectName("topbar");
    setFixedHeight(64);
    setStyleSheet("#topbar { background: rgba(255,255,255,0.85); border-bottom: 1px solid #F3F4F6; }");

    QHBoxLayout *layout = new QHBoxLayout(this);
    layout->setContentsMargins(24, 0, 0, 0);
    layout->setSpacing(0);

    // ── Left: page title ──
    m_titleLabel = new QLabel("\xe5\xb7\xa5\xe4\xbd\x9c\xe5\x8f\xb0");
    m_titleLabel->setStyleSheet("font-size: 16px; font-weight: 600; color: #111827;");

    m_tagLabel = new QLabel;
    m_tagLabel->setVisible(false);

    // ── Right: user + window controls ──
    QHBoxLayout *rightLayout = new QHBoxLayout;
    rightLayout->setSpacing(0);

    // Status pill
    m_statusDot = new QLabel;
    m_statusDot->setFixedSize(6, 6);
    m_statusDot->setStyleSheet("background: #059669; border-radius: 3px;");
    m_statusText = new QLabel("\xe6\x9c\x8d\xe5\x8a\xa1\xe5\x9c\xa8\xe7\xba\xbf");
    m_statusText->setStyleSheet(
        "color: #059669; font-size: 11px; font-weight: 500;"
        "background: #ECFDF5; padding: 2px 4px; border-radius: 8px; margin-right: 8px;"
    );

    // User
    m_avatarLabel = new QLabel("U");
    m_avatarLabel->setFixedSize(32, 32);
    m_avatarLabel->setAlignment(Qt::AlignCenter);
    m_avatarLabel->setCursor(Qt::PointingHandCursor);
    m_avatarLabel->setStyleSheet(
        "background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #818CF8,stop:1 #6366F1);"
        "color: white; border-radius: 16px;"
        "font-size: 13px; font-weight: 600;"
    );
    m_nameLabel = new QLabel("\xe7\x94\xa8\xe6\x88\xb7");
    m_nameLabel->setStyleSheet("color: #374151; font-size: 13px; font-weight: 500; margin-left: 6px;");
    m_nameLabel->setCursor(Qt::PointingHandCursor);

    m_userBtn = new QPushButton;
    m_userBtn->setFixedSize(20, 20);
    m_userBtn->setIcon(makeIconFromPainter(16, [](QPainter &p, int s) {
        p.setPen(Qt::NoPen);
        p.setBrush(QColor("#6B7280"));
        QPolygonF tri;
        tri << QPointF(3, 5) << QPointF(s - 3, 5) << QPointF(s / 2.0, s - 4);
        p.drawPolygon(tri);
    }));
    m_userBtn->setIconSize(QSize(12, 12));
    m_userBtn->setStyleSheet("border: none; background: transparent; margin-left: 0px;");
    m_userBtn->setCursor(Qt::PointingHandCursor);

    m_menu = new QMenu(this);
    m_menu->setStyleSheet(
        "QMenu { background: white; border: 1px solid #E5E7EB; border-radius: 8px; padding: 4px 0; }"
        "QMenu::item { padding: 8px 24px; font-size: 13px; color: #374151; }"
        "QMenu::item:hover { background: #EEF2FF; color: #4F46E5; }"
    );
    m_menu->addAction("\xe4\xbf\xae\xe6\x94\xb9\xe5\xaf\x86\xe7\xa0\x81", this, &TopBar::changePasswordClicked);
    m_menu->addAction("\xe9\x80\x80\xe5\x87\xba\xe7\x99\xbb\xe5\xbd\x95", this, &TopBar::logoutClicked);

    connect(m_userBtn, &QPushButton::clicked, [this]() {
        m_menu->exec(m_avatarLabel->mapToGlobal(QPoint(0, m_avatarLabel->height() + 4)));
    });
    m_avatarLabel->installEventFilter(this);
    m_nameLabel->installEventFilter(this);

    // Window controls with QPainter-drawn icons
    auto makeWinBtn = [](const QIcon &icon) -> QPushButton* {
        QPushButton *btn = new QPushButton;
        btn->setIcon(icon);
        btn->setIconSize(QSize(16, 16));
        btn->setFixedSize(46, 32);
        btn->setCursor(Qt::PointingHandCursor);
        btn->setStyleSheet(
            "QPushButton { border: none; background: transparent; border-radius: 0; }"
            "QPushButton:hover { background: #F3F4F6; }");
        return btn;
    };

    m_minBtn = makeWinBtn(minimizeIcon());
    m_maxBtn = makeWinBtn(maximizeIcon());
    m_closeBtn = makeWinBtn(closeIcon());
    m_closeBtn->setStyleSheet(
        "QPushButton { border: none; background: transparent; border-radius: 0; }"
        "QPushButton:hover { background: #EF4444; }");

    connect(m_minBtn, &QPushButton::clicked, this, &TopBar::minimizeClicked);
    connect(m_maxBtn, &QPushButton::clicked, this, &TopBar::maximizeClicked);
    connect(m_closeBtn, &QPushButton::clicked, this, &TopBar::closeClicked);

    rightLayout->addWidget(m_statusDot);
    rightLayout->addWidget(m_statusText);
    rightLayout->addWidget(m_avatarLabel);
    rightLayout->addWidget(m_nameLabel);
    rightLayout->addWidget(m_userBtn);
    rightLayout->addSpacing(16);
    rightLayout->addWidget(m_minBtn);
    rightLayout->addWidget(m_maxBtn);
    rightLayout->addWidget(m_closeBtn);

    layout->addWidget(m_titleLabel);
    layout->addWidget(m_tagLabel);
    layout->addStretch();
    layout->addLayout(rightLayout);
}

void TopBar::setTitle(const QString &title) { m_titleLabel->setText(title); }
void TopBar::setModuleTag(const QString &tag) { Q_UNUSED(tag); }
void TopBar::setNickname(const QString &name) {
    m_nameLabel->setText(name);
    m_avatarLabel->setText(name.isEmpty() ? "U" : name.left(1).toUpper());
}

void TopBar::mousePressEvent(QMouseEvent *event) {
    if (event->button() == Qt::LeftButton) {
        m_dragging = true;
        m_dragPos = event->globalPos() - window()->frameGeometry().topLeft();
        event->accept();
    }
}

void TopBar::mouseMoveEvent(QMouseEvent *event) {
    if (m_dragging && (event->buttons() & Qt::LeftButton)) {
        window()->move(event->globalPos() - m_dragPos);
        event->accept();
    }
}

void TopBar::mouseReleaseEvent(QMouseEvent *event) {
    m_dragging = false;
    QWidget::mouseReleaseEvent(event);
}

void TopBar::mouseDoubleClickEvent(QMouseEvent *event) {
    emit maximizeClicked();
    QWidget::mouseDoubleClickEvent(event);
}

bool TopBar::eventFilter(QObject *obj, QEvent *event) {
    if (event->type() == QEvent::MouseButtonPress) {
        if (obj == m_avatarLabel || obj == m_nameLabel) {
            m_menu->exec(m_avatarLabel->mapToGlobal(QPoint(0, m_avatarLabel->height() + 4)));
            return true;
        }
    }
    return QWidget::eventFilter(obj, event);
}
