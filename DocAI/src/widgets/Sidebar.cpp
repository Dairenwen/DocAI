#include "Sidebar.h"
#include "../utils/IconHelper.h"
#include <QVariant>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QHBoxLayout>
#include <cstdlib>

Sidebar::Sidebar(QWidget *parent) : QWidget(parent) {
    setupUI();
    initFloatingChars();
    m_floatTimer = new QTimer(this);
    connect(m_floatTimer, &QTimer::timeout, this, &Sidebar::animateFloatingChars);
    m_floatTimer->start(30); // faster interval
}

void Sidebar::paintEvent(QPaintEvent *) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    // Clip to top-left and bottom-left rounded corners
    QPainterPath path;
    path.moveTo(10, 0);
    path.arcTo(QRectF(0, 0, 20, 20), 90, 90); // top-left corner
    path.lineTo(0, height() - 10);
    path.arcTo(QRectF(0, height() - 20, 20, 20), 180, 90); // bottom-left corner
    path.lineTo(width(), height());
    path.lineTo(width(), 0);
    path.closeSubpath();
    p.setClipPath(path);
    p.fillRect(rect(), QColor("#FFFFFF"));

    // Draw floating characters in the area below nav items
    int floatTop = 260; // approximate top of float area (below nav items)
    int floatBottom = height() - 50; // above collapse button
    if (floatBottom > floatTop && !m_collapsed) {
        int floatH = floatBottom - floatTop;
        for (const SidebarFloatingChar &fc : m_floatChars) {
            int px = (int)(fc.x * width());
            int py = floatTop + (int)(fc.y * floatH);
            p.save();
            p.translate(px, py);
            p.rotate(fc.rotation);
            QFont font("Consolas", (int)fc.size);
            font.setWeight(QFont::Bold);
            p.setFont(font);
            p.setPen(QColor(79, 70, 229, (int)(fc.opacity * 255)));
            p.drawText(0, 0, fc.ch);
            p.restore();
        }
    }

    p.setClipping(false);
    // Right border line
    p.setPen(QColor("#F3F4F6"));
    p.drawLine(width() - 1, 0, width() - 1, height());
}

void Sidebar::initFloatingChars() {
    m_floatChars.clear();
    for (int i = 0; i < 18; i++) {
        SidebarFloatingChar fc;
        fc.x = (qrand() % 900 + 50) / 1000.0;
        fc.y = (qrand() % 1000) / 1000.0;
        fc.rotation = (qrand() % 120) - 60; // -60..+60 degrees
        fc.opacity = 0.06 + (qrand() % 10) / 100.0;
        fc.speed = 0.003 + (qrand() % 40) / 10000.0; // faster
        bool upper = qrand() % 2;
        fc.ch = QString(QChar(upper ? 'A' + qrand() % 26 : 'a' + qrand() % 26));
        fc.size = 8 + (qrand() % 9); // 8..16
        m_floatChars.append(fc);
    }
}

void Sidebar::animateFloatingChars() {
    for (int i = 0; i < m_floatChars.size(); i++) {
        SidebarFloatingChar &fc = m_floatChars[i];
        fc.y -= fc.speed;
        // Color gradient: #4F46E5 fading to white as it rises
        fc.opacity = 0.15 * fc.y;
        if (fc.opacity < 0.01) fc.opacity = 0.01;
        if (fc.y < 0) {
            fc.y = 1.0;
            fc.x = (qrand() % 900 + 50) / 1000.0;
            fc.rotation = (qrand() % 120) - 60;
            fc.speed = 0.003 + (qrand() % 40) / 10000.0;
            fc.opacity = 0.15;
            bool upper = qrand() % 2;
            fc.ch = QString(QChar(upper ? 'A' + qrand() % 26 : 'a' + qrand() % 26));
            fc.size = 8 + (qrand() % 9);
        }
    }
    update();
}

void Sidebar::setupUI() {
    setObjectName("sidebar");
    setFixedWidth(260);

    QVBoxLayout *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(0, 16, 0, 12);
    mainLayout->setSpacing(0);

    // ── Logo area ──
    QWidget *logoSection = new QWidget;
    logoSection->setFixedHeight(56);
    QHBoxLayout *logoLayout = new QHBoxLayout(logoSection);
    logoLayout->setContentsMargins(20, 0, 20, 0);
    logoLayout->setSpacing(12);

    QLabel *logoIcon = new QLabel;
    logoIcon->setFixedSize(36, 36);
    logoIcon->setAlignment(Qt::AlignCenter);
    logoIcon->setPixmap(IconHelper::document(20, QColor("white")));
    logoIcon->setStyleSheet(
        "background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #818CF8,stop:1 #6366F1);"
        "border-radius: 10px;"
    );

    m_logoTextWidget = new QWidget;
    QVBoxLayout *logoTextLayout = new QVBoxLayout(m_logoTextWidget);
    logoTextLayout->setContentsMargins(0, 0, 0, 0);
    logoTextLayout->setSpacing(0);
    QLabel *logoTitle = new QLabel("DocAI");
    logoTitle->setStyleSheet("color: #111827; font-size: 18px; font-weight: 700; background: transparent;");
    QLabel *logoSub = new QLabel("\xe6\x99\xba\xe8\x83\xbd\xe6\x96\x87\xe6\xa1\xa3");
    logoSub->setStyleSheet("color: #9CA3AF; font-size: 11px; background: transparent;");
    logoTextLayout->addWidget(logoTitle);
    logoTextLayout->addWidget(logoSub);

    logoLayout->addWidget(logoIcon);
    logoLayout->addWidget(m_logoTextWidget);
    logoLayout->addStretch();

    mainLayout->addWidget(logoSection);
    mainLayout->addSpacing(16);

    // ── Navigation items ──
    m_navLayout = new QVBoxLayout;
    m_navLayout->setSpacing(4);
    m_navLayout->setContentsMargins(12, 0, 12, 0);

    struct MenuDef { QPixmap icon; QString text; };
    QList<MenuDef> menus = {
        {IconHelper::home(16, QColor("#9CA3AF")), "\xe5\xb7\xa5\xe4\xbd\x9c\xe5\x8f\xb0"},
        {IconHelper::folder(16, QColor("#9CA3AF")), "\xe6\x96\x87\xe6\xa1\xa3\xe7\xae\xa1\xe7\x90\x86"},
        {IconHelper::table(16, QColor("#9CA3AF")), "\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe8\xa1\xa8"},
        {IconHelper::chat(16, QColor("#9CA3AF")), "AI \xe5\xaf\xb9\xe8\xaf\x9d"},
    };

    for (int i = 0; i < menus.size(); i++) {
        QWidget *navWidget = new QWidget;
        navWidget->setFixedHeight(44);
        navWidget->setCursor(Qt::PointingHandCursor);
        navWidget->setAttribute(Qt::WA_Hover);
        navWidget->installEventFilter(this);
        navWidget->setProperty("navIndex", QVariant(i));

        QHBoxLayout *navLayout = new QHBoxLayout(navWidget);
        navLayout->setContentsMargins(14, 0, 14, 0);
        navLayout->setSpacing(12);

        QWidget *indicator = new QWidget;
        indicator->setFixedSize(3, 20);
        indicator->setStyleSheet("background: transparent; border-radius: 1px;");

        QLabel *iconLbl = new QLabel;
        iconLbl->setFixedSize(20, 20);
        iconLbl->setAlignment(Qt::AlignCenter);
        iconLbl->setPixmap(menus[i].icon);
        iconLbl->setStyleSheet("background: transparent;");

        QLabel *textLbl = new QLabel(menus[i].text);
        textLbl->setStyleSheet("font-size: 14px; background: transparent; color: #6B7280;");

        navLayout->addWidget(indicator);
        navLayout->addWidget(iconLbl);
        navLayout->addWidget(textLbl, 1);

        NavItem item;
        item.container = navWidget;
        item.iconLabel = iconLbl;
        item.textLabel = textLbl;
        item.indicator = indicator;
        m_items.append(item);
        m_navLayout->addWidget(navWidget);
    }

    mainLayout->addLayout(m_navLayout);
    mainLayout->addStretch();

    // ── Collapse toggle ──
    m_collapseWidget = new QWidget;
    QHBoxLayout *collapseLayout = new QHBoxLayout(m_collapseWidget);
    collapseLayout->setContentsMargins(16, 8, 16, 8);
    collapseLayout->setSpacing(6);

    m_collapseIcon = new QLabel("\xe2\x97\x80");
    m_collapseIcon->setStyleSheet("color: #D1D5DB; font-size: 11px; background: transparent;");
    m_collapseIcon->setAlignment(Qt::AlignCenter);
    m_collapseLbl = new QLabel("\xe6\x94\xb6\xe8\xb5\xb7");
    m_collapseLbl->setStyleSheet("color: #D1D5DB; font-size: 12px; background: transparent;");
    collapseLayout->addWidget(m_collapseIcon);
    collapseLayout->addWidget(m_collapseLbl);
    collapseLayout->addStretch();
    m_collapseWidget->setCursor(Qt::PointingHandCursor);
    m_collapseWidget->setAttribute(Qt::WA_Hover);
    m_collapseWidget->setProperty("navIndex", QVariant(-1));
    m_collapseWidget->installEventFilter(this);
    m_collapseWidget->setStyleSheet("background: transparent; border-radius: 6px; margin: 0 12px;");
    mainLayout->addWidget(m_collapseWidget);

    updateSelection();
}

bool Sidebar::event(QEvent *e) {
    return QWidget::event(e);
}

bool Sidebar::eventFilter(QObject *obj, QEvent *event) {
    QWidget *w = qobject_cast<QWidget*>(obj);
    if (!w) return QWidget::eventFilter(obj, event);
    int idx = w->property("navIndex").toInt();

    if (event->type() == QEvent::MouseButtonRelease) {
        if (idx == -1) {
            setCollapsed(!m_collapsed);
            emit collapseToggled(m_collapsed);
        } else {
            m_currentIndex = idx;
            updateSelection();
            emit pageChanged(idx);
        }
    } else if (event->type() == QEvent::HoverEnter) {
        if (idx == -1) {
            w->setStyleSheet("background: #F3F4F6; border-radius: 6px; margin: 0 12px;");
        } else if (idx != m_currentIndex) {
            w->setStyleSheet("background: #F3F4F6; border-radius: 6px;");
        }
    } else if (event->type() == QEvent::HoverLeave) {
        if (idx == -1) {
            w->setStyleSheet("background: transparent; border-radius: 6px; margin: 0 12px;");
        } else if (idx != m_currentIndex) {
            w->setStyleSheet("background: transparent; border-radius: 6px;");
        }
    }
    return QWidget::eventFilter(obj, event);
}

void Sidebar::setCurrentIndex(int index) {
    m_currentIndex = index;
    updateSelection();
}

void Sidebar::setCollapsed(bool collapsed) {
    m_collapsed = collapsed;
    setFixedWidth(collapsed ? 72 : 260);
    m_logoTextWidget->setVisible(!collapsed);
    m_collapseLbl->setVisible(!collapsed);
    // Update collapse icon direction and center it
    m_collapseIcon->setText(collapsed ? "\xe2\x96\xb6" : "\xe2\x97\x80");
    QHBoxLayout *cLay = qobject_cast<QHBoxLayout*>(m_collapseWidget->layout());
    if (cLay) {
        if (collapsed) {
            cLay->setContentsMargins(0, 8, 0, 8);
            m_collapseIcon->setFixedWidth(72);
        } else {
            cLay->setContentsMargins(16, 8, 16, 8);
            m_collapseIcon->setFixedWidth(QWIDGETSIZE_MAX);
            m_collapseIcon->setMinimumWidth(0);
        }
    }
    for (auto &item : m_items) {
        item.textLabel->setVisible(!collapsed);
        item.indicator->setVisible(!collapsed);
        QHBoxLayout *lay = qobject_cast<QHBoxLayout*>(item.container->layout());
        if (lay) {
            if (collapsed) {
                lay->setContentsMargins(0, 0, 0, 0);
                lay->setSpacing(0);
                // Center icon properly: let layout center a 20x20 icon in 72px width
                item.iconLabel->setFixedSize(20, 20);
                item.iconLabel->setAlignment(Qt::AlignCenter);
            } else {
                lay->setContentsMargins(14, 0, 14, 0);
                lay->setSpacing(12);
                item.iconLabel->setFixedSize(20, 20);
            }
        }
    }
}

void Sidebar::updateSelection() {
    // Icon generator functions matching nav order: home, folder, table, chat
    typedef QPixmap (*IconFn)(int, const QColor&);
    static IconFn iconFns[] = {
        IconHelper::home, IconHelper::folder, IconHelper::table,
        IconHelper::chat
    };

    for (int i = 0; i < m_items.size(); i++) {
        bool active = (i == m_currentIndex);
        NavItem &item = m_items[i];

        // Enterprise style: active = primary filled bg + white text + left highlight bar
        item.indicator->setStyleSheet(active
            ? "background: white; border-radius: 1px;"
            : "background: transparent; border-radius: 1px;");

        item.container->setStyleSheet(active
            ? "background: #4F46E5; border-radius: 6px;"
            : "background: transparent; border-radius: 6px;");

        QColor iconColor = active ? QColor("#FFFFFF") : QColor("#9CA3AF");
        if (i < 4)
            item.iconLabel->setPixmap(iconFns[i](16, iconColor));
        item.iconLabel->setStyleSheet("background: transparent;");

        item.textLabel->setStyleSheet(active
            ? "font-size: 14px; font-weight: 600; background: transparent; color: white;"
            : "font-size: 14px; background: transparent; color: #6B7280;");
    }
}
