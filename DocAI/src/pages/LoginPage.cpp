#include "LoginPage.h"
#include "../network/ApiClient.h"
#include "../utils/TokenManager.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QPainter>
#include <QPainterPath>
#include <QJsonObject>
#include <QDialog>
#include <QFormLayout>
#include <QMessageBox>
#include <QTimer>
#include <QApplication>
#include <QGraphicsDropShadowEffect>
#include <QMouseEvent>
#include <QtMath>
#include <QMainWindow>
#include <QSettings>
#include <cstdlib>
#include "../utils/IconHelper.h"

LoginPage::LoginPage(QWidget *parent) : QWidget(parent) {
    setMouseTracking(true);
    setupUI();
    initParticles();
    m_animTimer = new QTimer(this);
    connect(m_animTimer, &QTimer::timeout, this, &LoginPage::animateTick);
    m_animTimer->start(30);
    loadSavedCredentials();
}

void LoginPage::initParticles() {
    static const QStringList chars = {
        "AI", "ML", "NLP", "Doc", "{}", "</>", "01", "//",
        "API", "LLM", "OCR", "RAG", "Qt", "C++", "&&",
        "\xe6\x96\x87", "\xe6\xa1\xa3", "\xe6\x99\xba", "\xe8\x83\xbd",
        "\xe5\xa1\xab", "\xe8\xa1\xa8", "\xe6\x8f\x90\xe5\x8f\x96", "\xe5\x88\x86\xe6\x9e\x90"
    };
    m_particles.clear();
    for (int i = 0; i < 35; i++) {
        FloatingChar p;
        p.baseX = (qrand() % 1000) / 1000.0;
        p.baseY = (qrand() % 1000) / 1000.0;
        p.x = p.baseX;
        p.y = p.baseY;
        p.vx = ((qrand() % 100) - 50) / 8000.0;
        p.vy = ((qrand() % 100) - 50) / 8000.0;
        p.ch = chars[qrand() % chars.size()];
        p.size = 10 + (qrand() % 8);
        p.opacity = 0.06 + (qrand() % 10) / 100.0;
        m_particles.append(p);
    }
}

void LoginPage::animateTick() {
    QPointF mouseNorm(m_mousePos.x() / qMax(1, width()),
                      m_mousePos.y() / qMax(1, height()));

    for (int i = 0; i < m_particles.size(); i++) {
        FloatingChar &p = m_particles[i];
        // Mouse repulsion
        double dx = p.x - mouseNorm.x();
        double dy = p.y - mouseNorm.y();
        double dist = qSqrt(dx * dx + dy * dy);
        if (dist < 0.15 && dist > 0.001) {
            double force = (0.15 - dist) * 0.003;
            p.vx += dx / dist * force;
            p.vy += dy / dist * force;
        }
        // Spring back to base
        p.vx += (p.baseX - p.x) * 0.002;
        p.vy += (p.baseY - p.y) * 0.002;
        // Damping
        p.vx *= 0.97;
        p.vy *= 0.97;
        // Update position
        p.x += p.vx;
        p.y += p.vy;
    }
    update();
}

void LoginPage::paintEvent(QPaintEvent *) {
    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);

    // Background gradient (clipped to rounded corners)
    QLinearGradient bg(0, 0, width(), height());
    bg.setColorAt(0.0, QColor("#F0F0FF"));
    bg.setColorAt(0.5, QColor("#F9FAFB"));
    bg.setColorAt(1.0, QColor("#F5F0FF"));
    QPainterPath bgPath;
    bgPath.addRoundedRect(rect(), 10, 10);
    painter.setClipPath(bgPath);
    painter.fillRect(rect(), bg);

    // Draw floating characters
    for (const FloatingChar &p : m_particles) {
        int px = (int)(p.x * width());
        int py = (int)(p.y * height());
        painter.save();
        QFont font("Consolas", (int)p.size);
        font.setWeight(QFont::Bold);
        painter.setFont(font);
        painter.setPen(QColor(79, 70, 229, (int)(p.opacity * 255)));
        painter.drawText(px, py, p.ch);
        painter.restore();
    }
}

void LoginPage::setupUI() {
    // Background painted in paintEvent

    QVBoxLayout *mainLayout = new QVBoxLayout(this);
    mainLayout->setContentsMargins(0, 0, 0, 0);

    // ===== Window control buttons (top-right overlay) =====
    QHBoxLayout *winControlRow = new QHBoxLayout;
    winControlRow->setContentsMargins(0, 4, 4, 0);
    winControlRow->setSpacing(0);
    winControlRow->addStretch();

    auto makeWinBtn = [this](const QPixmap &icon, const QString &hoverBg) -> QPushButton* {
        QPushButton *btn = new QPushButton;
        btn->setFixedSize(36, 28);
        btn->setIcon(QIcon(icon));
        btn->setIconSize(QSize(12, 12));
        btn->setCursor(Qt::PointingHandCursor);
        btn->setStyleSheet(QString(
            "QPushButton { background: transparent; border: none; border-radius: 4px; }"
            "QPushButton:hover { background: %1; }").arg(hoverBg));
        return btn;
    };

    // Draw control icons
    auto drawMinIcon = []() -> QPixmap {
        QPixmap pix(12, 12); pix.fill(Qt::transparent);
        QPainter p(&pix); p.setPen(QPen(QColor("#6B7280"), 1.5));
        p.drawLine(2, 6, 10, 6); p.end(); return pix;
    };
    auto drawMaxIcon = []() -> QPixmap {
        QPixmap pix(12, 12); pix.fill(Qt::transparent);
        QPainter p(&pix); p.setPen(QPen(QColor("#6B7280"), 1.2));
        p.drawRect(2, 2, 8, 8); p.end(); return pix;
    };
    auto drawCloseIcon = []() -> QPixmap {
        QPixmap pix(12, 12); pix.fill(Qt::transparent);
        QPainter p(&pix); p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(QColor("#6B7280"), 1.5));
        p.drawLine(2, 2, 10, 10); p.drawLine(10, 2, 2, 10); p.end(); return pix;
    };

    QPushButton *minBtn = makeWinBtn(drawMinIcon(), "rgba(0,0,0,0.06)");
    QPushButton *maxBtn = makeWinBtn(drawMaxIcon(), "rgba(0,0,0,0.06)");
    QPushButton *closeBtn = makeWinBtn(drawCloseIcon(), "#FEE2E2");
    winControlRow->addWidget(minBtn);
    winControlRow->addWidget(maxBtn);
    winControlRow->addWidget(closeBtn);

    connect(minBtn, &QPushButton::clicked, [this]() { window()->showMinimized(); });
    connect(maxBtn, &QPushButton::clicked, [this]() {
        QWidget *win = window();
        QMainWindow *mw = qobject_cast<QMainWindow*>(win);
        if (win->isMaximized()) {
            win->showNormal();
            if (mw && mw->centralWidget() && mw->centralWidget()->layout())
                mw->centralWidget()->layout()->setContentsMargins(10, 10, 10, 10);
        } else {
            win->showMaximized();
            if (mw && mw->centralWidget() && mw->centralWidget()->layout())
                mw->centralWidget()->layout()->setContentsMargins(0, 0, 0, 0);
        }
    });
    connect(closeBtn, &QPushButton::clicked, [this]() { window()->close(); });

    mainLayout->addLayout(winControlRow);

    // ======= Auth Container (brand left + form right) =======
    QWidget *authContainer = new QWidget;
    authContainer->setObjectName("authContainer");
    authContainer->setFixedSize(860, 500);
    authContainer->setStyleSheet(
        "#authContainer { background: white; border-radius: 10px; border: 1px solid #E5E7EB; }"
    );
    QGraphicsDropShadowEffect *cardShadow = new QGraphicsDropShadowEffect(authContainer);
    cardShadow->setColor(QColor(0, 0, 0, 20));
    cardShadow->setBlurRadius(50);
    cardShadow->setOffset(0, 25);
    authContainer->setGraphicsEffect(cardShadow);

    QHBoxLayout *authLayout = new QHBoxLayout(authContainer);
    authLayout->setContentsMargins(0, 0, 0, 0);
    authLayout->setSpacing(0);

    // ---- Brand Panel (Left) ----
    QWidget *brandPanel = new QWidget;
    brandPanel->setObjectName("brandPanel");
    brandPanel->setFixedWidth(340);
    brandPanel->setStyleSheet(
        "#brandPanel { background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #F8F7FF,stop:1 #F0EEFF);"
        "  border-top-left-radius: 20px; border-bottom-left-radius: 20px;"
        "  border-right: 1px solid #E5E7EB; }"
    );
    QVBoxLayout *brandLayout = new QVBoxLayout(brandPanel);
    brandLayout->setContentsMargins(32, 36, 32, 32);
    brandLayout->setSpacing(0);

    // Brand logo
    QHBoxLayout *brandLogoRow = new QHBoxLayout;
    brandLogoRow->setSpacing(10);
    QLabel *brandIcon = new QLabel;
    brandIcon->setFixedSize(40, 40);
    brandIcon->setAlignment(Qt::AlignCenter);
    brandIcon->setStyleSheet(
        "background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #818CF8,stop:1 #6366F1);"
        "border-radius: 12px; color: white; font-size: 18px; font-weight: bold;"
    );
    brandIcon->setPixmap(IconHelper::document(20, QColor("white")));
    brandIcon->setAlignment(Qt::AlignCenter);
    QLabel *brandTitle = new QLabel("DocAI");
    brandTitle->setStyleSheet("font-size: 18px; font-weight: 700; color: #111827;");
    brandLogoRow->addWidget(brandIcon);
    brandLogoRow->addWidget(brandTitle);
    brandLogoRow->addStretch();

    brandLayout->addLayout(brandLogoRow);
    brandLayout->addSpacing(24);

    // Brand headline
    QLabel *brandHeadline = new QLabel("\xe6\x99\xba\xe8\x83\xbd\xe6\x96\x87\xe6\xa1\xa3\n\xe5\xa4\x84\xe7\x90\x86\xe7\xb3\xbb\xe7\xbb\x9f");
    brandHeadline->setStyleSheet("font-size: 28px; font-weight: 800; color: #111827;");
    brandLayout->addWidget(brandHeadline);
    brandLayout->addSpacing(8);

    // Divider
    QWidget *divider = new QWidget;
    divider->setFixedSize(40, 2);
    divider->setStyleSheet("background: qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #4F46E5,stop:1 transparent);");
    brandLayout->addWidget(divider);
    brandLayout->addSpacing(16);

    QLabel *brandDesc = new QLabel("AI \xe9\xa9\xb1\xe5\x8a\xa8 \xc2\xb7 \xe9\xab\x98\xe6\x95\x88\xe5\x8a\x9e\xe5\x85\xac \xc2\xb7 \xe4\xb8\x80\xe9\x94\xae\xe5\xa1\xab\xe8\xa1\xa8");
    brandDesc->setStyleSheet("font-size: 13px; color: #6B7280;");
    brandLayout->addWidget(brandDesc);
    brandLayout->addSpacing(24);

    // Features (matching Vue Login.vue brand-features)
    struct BrandFeat { QString main; QString desc; };
    QList<BrandFeat> features = {
        {"AI \xe6\x99\xba\xe8\x83\xbd\xe5\xaf\xb9\xe8\xaf\x9d", "\xe5\x9f\xba\xe4\xba\x8e\xe5\xa4\xa7\xe8\xaf\xad\xe8\xa8\x80\xe6\xa8\xa1\xe5\x9e\x8b\xef\xbc\x8c\xe4\xb8\x8e\xe6\x96\x87\xe6\xa1\xa3\xe6\xb7\xb1\xe5\xba\xa6\xe4\xba\xa4\xe4\xba\x92"},
        {"\xe6\x96\x87\xe6\xa1\xa3\xe4\xbf\xa1\xe6\x81\xaf\xe6\x8f\x90\xe5\x8f\x96", "\xe8\x87\xaa\xe5\x8a\xa8\xe8\xaf\x86\xe5\x88\xab\xe5\xb9\xb6\xe6\x8f\x90\xe5\x8f\x96\xe5\x85\xb3\xe9\x94\xae\xe5\xad\x97\xe6\xae\xb5\xe4\xb8\x8e\xe7\xbb\x93\xe6\x9e\x84\xe5\x8c\x96\xe6\x95\xb0\xe6\x8d\xae"},
        {"\xe8\xa1\xa8\xe6\xa0\xbc\xe6\x99\xba\xe8\x83\xbd\xe5\xa1\xab\xe5\x86\x99", "\xe6\x99\xba\xe8\x83\xbd\xe5\x8c\xb9\xe9\x85\x8d\xe6\x95\xb0\xe6\x8d\xae\xef\xbc\x8c\xe6\x89\xb9\xe9\x87\x8f\xe5\xa1\xab\xe5\x85\x85\xe6\xa8\xa1\xe6\x9d\xbf\xe5\xb9\xb6\xe5\xaf\xbc\xe5\x87\xba"},
    };
    for (const auto &feat : features) {
        QHBoxLayout *featRow = new QHBoxLayout;
        featRow->setSpacing(10);
        featRow->setContentsMargins(0, 0, 0, 0);
        QLabel *dot = new QLabel;
        dot->setFixedSize(6, 6);
        dot->setStyleSheet("background: rgba(79,70,229,0.3); border-radius: 3px;");
        QVBoxLayout *featTextCol = new QVBoxLayout;
        featTextCol->setSpacing(2);
        QLabel *mainLbl = new QLabel(feat.main);
        mainLbl->setStyleSheet("font-size: 13px; font-weight: 600; color: #374151;");
        QLabel *descLbl = new QLabel(feat.desc);
        descLbl->setStyleSheet("font-size: 11px; color: #9CA3AF;");
        featTextCol->addWidget(mainLbl);
        featTextCol->addWidget(descLbl);
        featRow->addWidget(dot, 0, Qt::AlignTop);
        featRow->addLayout(featTextCol, 1);
        brandLayout->addLayout(featRow);
        brandLayout->addSpacing(8);
    }

    brandLayout->addStretch();

    // Tech tags (matching Vue)
    QHBoxLayout *tagRow = new QHBoxLayout;
    tagRow->setSpacing(6);
    for (const QString &tag : {"\xe5\xa4\xa7\xe8\xaf\xad\xe8\xa8\x80\xe6\xa8\xa1\xe5\x9e\x8b", "\xe5\xbe\xae\xe6\x9c\x8d\xe5\x8a\xa1\xe6\x9e\xb6\xe6\x9e\x84", "\xe6\x99\xba\xe8\x83\xbd\xe5\x8a\x9e\xe5\x85\xac"}) {
        QLabel *tagLbl = new QLabel(tag);
        tagLbl->setStyleSheet(
            "font-size: 10px; color: #6B7280; border: 1px solid #E5E7EB;"
            "border-radius: 16px; padding: 2px 8px;"
        );
        tagRow->addWidget(tagLbl);
    }
    tagRow->addStretch();
    brandLayout->addLayout(tagRow);

    // ---- Form Panel (Right) ----
    QWidget *formPanel = new QWidget;
    QVBoxLayout *formLayout = new QVBoxLayout(formPanel);
    formLayout->setContentsMargins(44, 36, 44, 32);
    formLayout->setSpacing(0);

    m_statusLabel = new QLabel;
    m_statusLabel->setStyleSheet("font-size: 12px; padding: 8px; border-radius: 6px;");
    m_statusLabel->setWordWrap(true);
    m_statusLabel->setVisible(false);

    m_formStack = new QStackedWidget;

    // ---- Login Form ----
    QWidget *loginForm = new QWidget;
    QVBoxLayout *loginLayout = new QVBoxLayout(loginForm);
    loginLayout->setContentsMargins(0, 0, 0, 0);
    loginLayout->setSpacing(12);

    QLabel *loginTitle = new QLabel("\xe6\xac\xa2\xe8\xbf\x8e\xe5\x9b\x9e\xe6\x9d\xa5");
    loginTitle->setStyleSheet("font-size: 22px; font-weight: 700; color: #111827;");
    QLabel *loginSubtitle = new QLabel("\xe7\x99\xbb\xe5\xbd\x95\xe6\x82\xa8\xe7\x9a\x84\xe8\xb4\xa6\xe6\x88\xb7\xef\xbc\x8c\xe5\xbc\x80\xe5\xa7\x8b\xe6\x99\xba\xe8\x83\xbd\xe6\x96\x87\xe6\xa1\xa3\xe5\xa4\x84\xe7\x90\x86");
    loginSubtitle->setStyleSheet("font-size: 13px; color: #9CA3AF;");

    auto makeInput = [](const QString &placeholder, bool isPassword = false) -> QLineEdit* {
        QLineEdit *e = new QLineEdit;
        e->setPlaceholderText(placeholder);
        e->setMinimumHeight(44);
        if (isPassword) e->setEchoMode(QLineEdit::Password);
        e->setStyleSheet(
            "QLineEdit { border: 1px solid #E5E7EB; border-radius: 10px; padding: 0 14px;"
            "  font-size: 14px; background: #F9FAFB; color: #111827; }"
            "QLineEdit:focus { border-color: #4F46E5; background: white; }"
        );
        return e;
    };

    auto makeLabel = [](const QString &text) -> QLabel* {
        QLabel *lbl = new QLabel(text);
        lbl->setStyleSheet("font-size: 12px; font-weight: 500; color: #6B7280;");
        return lbl;
    };

    m_loginUsername = makeInput("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe7\x94\xa8\xe6\x88\xb7\xe5\x90\x8d");
    m_loginPassword = makeInput("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe5\xaf\x86\xe7\xa0\x81", true);

    // Remember password & auto login checkboxes
    QString checkStyle =
        "QCheckBox { font-size: 12px; color: #6B7280; spacing: 6px; }"
        "QCheckBox::indicator { width: 16px; height: 16px; border: 1.5px solid #D1D5DB; border-radius: 4px; background: white; }"
        "QCheckBox::indicator:hover { border-color: #818CF8; }"
        "QCheckBox::indicator:checked { background: #4F46E5; border-color: #4F46E5; image: url(:/check.png); }";
    m_rememberCheck = new QCheckBox("\xe8\xae\xb0\xe4\xbd\x8f\xe5\xaf\x86\xe7\xa0\x81");
    m_rememberCheck->setStyleSheet(checkStyle);
    m_autoLoginCheck = new QCheckBox("\xe8\x87\xaa\xe5\x8a\xa8\xe7\x99\xbb\xe5\xbd\x95");
    m_autoLoginCheck->setStyleSheet(checkStyle);
    QHBoxLayout *checkRow = new QHBoxLayout;
    checkRow->setSpacing(16);
    checkRow->addWidget(m_rememberCheck);
    checkRow->addWidget(m_autoLoginCheck);
    checkRow->addStretch();

    connect(m_autoLoginCheck, &QCheckBox::toggled, [this](bool checked) {
        if (checked) m_rememberCheck->setChecked(true);
    });
    connect(m_rememberCheck, &QCheckBox::toggled, [this](bool checked) {
        if (!checked) m_autoLoginCheck->setChecked(false);
    });

    m_loginBtn = new QPushButton("\xe7\x99\xbb \xe5\xbd\x95");
    m_loginBtn->setMinimumHeight(44);
    m_loginBtn->setCursor(Qt::PointingHandCursor);
    m_loginBtn->setStyleSheet(
        "QPushButton { background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #4F46E5,stop:1 #7C3AED);"
        "  color: white; border: none; border-radius: 10px; font-size: 15px; font-weight: 600; }"
        "QPushButton:hover { background: qlineargradient(x1:0,y1:0,x2:1,y2:1,stop:0 #4338CA,stop:1 #6D28D9); }"
        "QPushButton:pressed { background: #3730A3; }"
        "QPushButton:disabled { background: #D1D5DB; }"
    );

    // Extra actions
    QHBoxLayout *extraLayout = new QHBoxLayout;
    QPushButton *emailAuthBtn = new QPushButton("\xe9\x82\xae\xe7\xae\xb1\xe9\xaa\x8c\xe8\xaf\x81\xe7\xa0\x81\xe7\x99\xbb\xe5\xbd\x95");
    emailAuthBtn->setFlat(true);
    emailAuthBtn->setCursor(Qt::PointingHandCursor);
    emailAuthBtn->setStyleSheet("color: #4F46E5; font-size: 12px; border: none;");
    QPushButton *resetBtn = new QPushButton("\xe5\xbf\x98\xe8\xae\xb0\xe5\xaf\x86\xe7\xa0\x81");
    resetBtn->setFlat(true);
    resetBtn->setCursor(Qt::PointingHandCursor);
    resetBtn->setStyleSheet("color: #4F46E5; font-size: 12px; border: none;");
    extraLayout->addWidget(emailAuthBtn);
    extraLayout->addStretch();
    extraLayout->addWidget(resetBtn);

    // Switch link
    QHBoxLayout *switchLayout = new QHBoxLayout;
    switchLayout->setAlignment(Qt::AlignCenter);
    QLabel *noAccLabel = new QLabel("\xe8\xbf\x98\xe6\xb2\xa1\xe6\x9c\x89\xe8\xb4\xa6\xe6\x88\xb7\xef\xbc\x9f");
    noAccLabel->setStyleSheet("color: #9CA3AF; font-size: 13px;");
    QPushButton *toRegBtn = new QPushButton("\xe7\xab\x8b\xe5\x8d\xb3\xe6\xb3\xa8\xe5\x86\x8c");
    toRegBtn->setFlat(true);
    toRegBtn->setCursor(Qt::PointingHandCursor);
    toRegBtn->setStyleSheet("color: #4F46E5; font-size: 13px; font-weight: 600; border: none;");

    loginLayout->addWidget(loginTitle);
    loginLayout->addWidget(loginSubtitle);
    loginLayout->addSpacing(16);
    loginLayout->addWidget(makeLabel("\xe7\x94\xa8\xe6\x88\xb7\xe5\x90\x8d"));
    loginLayout->addWidget(m_loginUsername);
    loginLayout->addWidget(makeLabel("\xe5\xaf\x86\xe7\xa0\x81"));
    loginLayout->addWidget(m_loginPassword);
    loginLayout->addLayout(checkRow);
    loginLayout->addSpacing(4);
    loginLayout->addWidget(m_loginBtn);
    loginLayout->addLayout(extraLayout);
    loginLayout->addStretch();
    loginLayout->addLayout(switchLayout);
    switchLayout->addWidget(noAccLabel);
    switchLayout->addWidget(toRegBtn);

    // ---- Register Form ----
    QWidget *regForm = new QWidget;
    QVBoxLayout *regLayout = new QVBoxLayout(regForm);
    regLayout->setContentsMargins(0, 0, 0, 0);
    regLayout->setSpacing(10);

    QLabel *regTitle = new QLabel("\xe5\x88\x9b\xe5\xbb\xba\xe8\xb4\xa6\xe6\x88\xb7");
    regTitle->setStyleSheet("font-size: 22px; font-weight: 700; color: #111827;");
    QLabel *regSubtitle = new QLabel("\xe6\xb3\xa8\xe5\x86\x8c\xe5\x90\x8e\xe4\xbd\xbf\xe7\x94\xa8\xe5\xae\x8c\xe6\x95\xb4\xe5\x8a\x9f\xe8\x83\xbd");
    regSubtitle->setStyleSheet("font-size: 13px; color: #9CA3AF;");

    m_regUsername = makeInput("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe7\x94\xa8\xe6\x88\xb7\xe5\x90\x8d");
    m_regNickname = makeInput("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe6\x98\xb5\xe7\xa7\xb0(\xe5\x8f\xaf\xe9\x80\x89)");
    m_regPassword = makeInput("\xe8\xaf\xb7\xe8\xbe\x93\xe5\x85\xa5\xe5\xaf\x86\xe7\xa0\x81", true);
    m_regConfirmPwd = makeInput("\xe8\xaf\xb7\xe5\x86\x8d\xe6\xac\xa1\xe8\xbe\x93\xe5\x85\xa5\xe5\xaf\x86\xe7\xa0\x81", true);

    m_regBtn = new QPushButton("\xe6\xb3\xa8 \xe5\x86\x8c");
    m_regBtn->setMinimumHeight(44);
    m_regBtn->setCursor(Qt::PointingHandCursor);
    m_regBtn->setStyleSheet(m_loginBtn->styleSheet());

    QHBoxLayout *switchLayout2 = new QHBoxLayout;
    switchLayout2->setAlignment(Qt::AlignCenter);
    QLabel *hasAccLabel = new QLabel("\xe5\xb7\xb2\xe6\x9c\x89\xe8\xb4\xa6\xe6\x88\xb7\xef\xbc\x9f");
    hasAccLabel->setStyleSheet("color: #9CA3AF; font-size: 13px;");
    QPushButton *toLoginBtn = new QPushButton("\xe7\xab\x8b\xe5\x8d\xb3\xe7\x99\xbb\xe5\xbd\x95");
    toLoginBtn->setFlat(true);
    toLoginBtn->setCursor(Qt::PointingHandCursor);
    toLoginBtn->setStyleSheet("color: #4F46E5; font-size: 13px; font-weight: 600; border: none;");

    regLayout->addWidget(regTitle);
    regLayout->addWidget(regSubtitle);
    regLayout->addSpacing(8);
    regLayout->addWidget(m_regUsername);
    regLayout->addWidget(m_regNickname);
    regLayout->addWidget(m_regPassword);
    regLayout->addWidget(m_regConfirmPwd);
    regLayout->addSpacing(4);
    regLayout->addWidget(m_regBtn);
    regLayout->addLayout(switchLayout2);
    switchLayout2->addWidget(hasAccLabel);
    switchLayout2->addWidget(toLoginBtn);

    m_formStack->addWidget(loginForm);
    m_formStack->addWidget(regForm);

    formLayout->addWidget(m_statusLabel);
    formLayout->addWidget(m_formStack, 1);

    authLayout->addWidget(brandPanel);
    authLayout->addWidget(formPanel, 1);

    mainLayout->addStretch();
    mainLayout->addWidget(authContainer, 0, Qt::AlignHCenter);
    mainLayout->addStretch();

    // Connections
    connect(m_loginBtn, &QPushButton::clicked, this, &LoginPage::handleLogin);
    connect(m_regBtn, &QPushButton::clicked, this, &LoginPage::handleRegister);
    connect(toRegBtn, &QPushButton::clicked, this, &LoginPage::switchToRegister);
    connect(toLoginBtn, &QPushButton::clicked, this, &LoginPage::switchToLogin);
    connect(emailAuthBtn, &QPushButton::clicked, this, &LoginPage::openEmailAuth);
    connect(resetBtn, &QPushButton::clicked, this, &LoginPage::openResetPassword);
    connect(m_loginPassword, &QLineEdit::returnPressed, this, &LoginPage::handleLogin);
    connect(m_regConfirmPwd, &QLineEdit::returnPressed, this, &LoginPage::handleRegister);
}

void LoginPage::handleLogin() {
    QString user = m_loginUsername->text().trimmed();
    QString pass = m_loginPassword->text();
    if (user.isEmpty() || pass.isEmpty()) {
        showError("请输入用户名和密码");
        return;
    }
    m_loading = true;
    m_loginBtn->setEnabled(false);
    m_loginBtn->setText("登录中...");
    m_statusLabel->setVisible(false);

    ApiClient::instance().login(user, pass, [this](bool ok, const QJsonObject &data, const QString &err) {
        m_loading = false;
        m_loginBtn->setEnabled(true);
        m_loginBtn->setText("登 录");
        if (!ok) {
            showError(err.isEmpty() ? "登录失败" : err);
            return;
        }
        QJsonObject d = data["data"].toObject();
        TokenManager::instance().setToken(d["token"].toString());
        TokenManager::instance().setUserId(QString::number(d["userId"].toInt()));
        TokenManager::instance().setUsername(d["username"].toString());
        TokenManager::instance().setNickname(d["nickname"].toString());
        saveCredentials();
        emit loginSuccess();
    });
}

void LoginPage::handleRegister() {
    QString user = m_regUsername->text().trimmed();
    QString nick = m_regNickname->text().trimmed();
    QString pass = m_regPassword->text();
    QString confirm = m_regConfirmPwd->text();
    if (user.length() < 3 || user.length() > 20) { showError("用户名需3-20位"); return; }
    if (pass.length() < 6) { showError("密码至少6位"); return; }
    if (pass != confirm) { showError("两次输入的密码不一致"); return; }

    m_regBtn->setEnabled(false);
    m_regBtn->setText("注册中...");
    ApiClient::instance().registerUser(user, pass, nick, [this](bool ok, const QJsonObject &data, const QString &err) {
        m_regBtn->setEnabled(true);
        m_regBtn->setText("注 册");
        if (!ok) { showError(err); return; }
        QJsonObject d = data["data"].toObject();
        TokenManager::instance().setToken(d["token"].toString());
        TokenManager::instance().setUserId(QString::number(d["userId"].toInt()));
        TokenManager::instance().setUsername(d["username"].toString());
        TokenManager::instance().setNickname(d["nickname"].toString());
        emit loginSuccess();
    });
}

void LoginPage::switchToRegister() { m_formStack->setCurrentIndex(1); m_statusLabel->setVisible(false); }
void LoginPage::switchToLogin() { m_formStack->setCurrentIndex(0); m_statusLabel->setVisible(false); }

void LoginPage::openEmailAuth() {
    QDialog dlg(this);
    dlg.setWindowTitle("邮箱验证码登录/注册");
    dlg.setFixedSize(420, 260);
    dlg.setStyleSheet("QDialog { background: white; border-radius: 12px; }"
                      "QLabel { font-size: 13px; color: #6B7280; }"
                      "QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 8px 12px; font-size: 13px; }"
                      "QLineEdit:focus { border-color: #818CF8; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(28, 24, 28, 24);

    QLabel *emailLabel = new QLabel("邮箱");
    QLineEdit *emailEdit = new QLineEdit;
    emailEdit->setPlaceholderText("请输入邮箱");

    QHBoxLayout *codeLayout = new QHBoxLayout;
    QLabel *codeLabel = new QLabel("验证码");
    QLineEdit *codeEdit = new QLineEdit;
    codeEdit->setPlaceholderText("请输入验证码");
    QPushButton *sendBtn = new QPushButton("发送验证码");
    sendBtn->setMinimumHeight(36);
    sendBtn->setStyleSheet("QPushButton { background: #818CF8; color: white; border: none; border-radius: 6px; padding: 0 16px; font-size: 13px; }"
                           "QPushButton:disabled { background: #C7D2FE; }");
    codeLayout->addWidget(codeEdit, 1);
    codeLayout->addWidget(sendBtn);

    QPushButton *submitBtn = new QPushButton("登录 / 注册");
    submitBtn->setMinimumHeight(42);
    submitBtn->setStyleSheet(m_loginBtn->styleSheet());

    QLabel *statusLbl = new QLabel;
    statusLbl->setVisible(false);
    statusLbl->setWordWrap(true);

    layout->addWidget(emailLabel);
    layout->addWidget(emailEdit);
    layout->addWidget(codeLabel);
    layout->addLayout(codeLayout);
    layout->addWidget(statusLbl);
    layout->addSpacing(8);
    layout->addWidget(submitBtn);

    connect(sendBtn, &QPushButton::clicked, [&]() {
        QString email = emailEdit->text().trimmed();
        if (email.isEmpty()) return;
        sendBtn->setEnabled(false);
        sendBtn->setText("发送中...");
        ApiClient::instance().sendVerificationCode(email, [&](bool ok, const QJsonObject &, const QString &err) {
            if (ok) {
                sendBtn->setText("已发送(60s)");
                int *countdown = new int(60);
                QTimer *timer = new QTimer(&dlg);
                connect(timer, &QTimer::timeout, [&, countdown, timer]() {
                    (*countdown)--;
                    if (*countdown <= 0) { timer->stop(); sendBtn->setEnabled(true); sendBtn->setText("发送验证码"); delete countdown; }
                    else sendBtn->setText(QString("已发送(%1s)").arg(*countdown));
                });
                timer->start(1000);
            } else {
                sendBtn->setEnabled(true);
                sendBtn->setText("发送验证码");
                statusLbl->setStyleSheet("color: #EF4444; font-size: 12px; background: #FEE2E2; padding: 6px; border-radius: 4px;");
                statusLbl->setText(err);
                statusLbl->setVisible(true);
            }
        });
    });

    connect(submitBtn, &QPushButton::clicked, [&]() {
        QString email = emailEdit->text().trimmed();
        QString code = codeEdit->text().trimmed();
        if (email.isEmpty() || code.isEmpty()) return;
        submitBtn->setEnabled(false);
        ApiClient::instance().emailAuth(email, code, [&](bool ok, const QJsonObject &data, const QString &err) {
            submitBtn->setEnabled(true);
            if (!ok) {
                statusLbl->setStyleSheet("color: #EF4444; font-size: 12px; background: #FEE2E2; padding: 6px; border-radius: 4px;");
                statusLbl->setText(err);
                statusLbl->setVisible(true);
                return;
            }
            QJsonObject d = data["data"].toObject();
            TokenManager::instance().setToken(d["token"].toString());
            TokenManager::instance().setUserId(QString::number(d["userId"].toInt()));
            TokenManager::instance().setUsername(d["username"].toString());
            TokenManager::instance().setNickname(d["nickname"].toString());
            dlg.accept();
            emit loginSuccess();
        });
    });

    dlg.exec();
}

void LoginPage::openResetPassword() {
    QDialog dlg(this);
    dlg.setWindowTitle("重置密码");
    dlg.setFixedSize(420, 320);
    dlg.setStyleSheet("QDialog { background: white; border-radius: 12px; }"
                      "QLabel { font-size: 13px; color: #6B7280; }"
                      "QLineEdit { border: 1.5px solid #D1D5DB; border-radius: 6px; padding: 8px 12px; font-size: 13px; }"
                      "QLineEdit:focus { border-color: #818CF8; }");
    QVBoxLayout *layout = new QVBoxLayout(&dlg);
    layout->setContentsMargins(28, 24, 28, 24);

    QLineEdit *emailEdit = new QLineEdit; emailEdit->setPlaceholderText("请输入注册邮箱");
    QLineEdit *codeEdit = new QLineEdit; codeEdit->setPlaceholderText("请输入验证码");
    QLineEdit *newPwdEdit = new QLineEdit; newPwdEdit->setPlaceholderText("请输入新密码"); newPwdEdit->setEchoMode(QLineEdit::Password);

    QPushButton *sendBtn = new QPushButton("发送验证码");
    sendBtn->setMinimumHeight(36);
    sendBtn->setStyleSheet("QPushButton { background: #818CF8; color: white; border: none; border-radius: 6px; padding: 0 16px; font-size: 13px; }");

    QHBoxLayout *codeRow = new QHBoxLayout;
    codeRow->addWidget(codeEdit, 1);
    codeRow->addWidget(sendBtn);

    QPushButton *resetBtn = new QPushButton("重置密码");
    resetBtn->setMinimumHeight(42);
    resetBtn->setStyleSheet(m_loginBtn->styleSheet());

    QLabel *statusLbl = new QLabel;
    statusLbl->setVisible(false);
    statusLbl->setWordWrap(true);

    layout->addWidget(new QLabel("邮箱"));
    layout->addWidget(emailEdit);
    layout->addWidget(new QLabel("验证码"));
    layout->addLayout(codeRow);
    layout->addWidget(new QLabel("新密码"));
    layout->addWidget(newPwdEdit);
    layout->addWidget(statusLbl);
    layout->addSpacing(8);
    layout->addWidget(resetBtn);

    connect(sendBtn, &QPushButton::clicked, [&]() {
        QString email = emailEdit->text().trimmed();
        if (email.isEmpty()) return;
        sendBtn->setEnabled(false);
        ApiClient::instance().sendVerificationCode(email, [&](bool ok, const QJsonObject &, const QString &err) {
            if (ok) { sendBtn->setText("已发送"); }
            else { sendBtn->setEnabled(true); statusLbl->setText(err); statusLbl->setVisible(true); }
        });
    });

    connect(resetBtn, &QPushButton::clicked, [&]() {
        ApiClient::instance().resetPassword(emailEdit->text().trimmed(), codeEdit->text().trimmed(),
            newPwdEdit->text(), [&](bool ok, const QJsonObject &, const QString &err) {
            if (ok) {
                QMessageBox::information(&dlg, "成功", "密码重置成功，请使用新密码登录");
                dlg.accept();
            } else {
                statusLbl->setStyleSheet("color: #EF4444; font-size: 12px; background: #FEE2E2; padding: 6px; border-radius: 4px;");
                statusLbl->setText(err);
                statusLbl->setVisible(true);
            }
        });
    });

    dlg.exec();
}

void LoginPage::showError(const QString &msg) {
    m_statusLabel->setStyleSheet("color: #EF4444; font-size: 12px; background: #FEE2E2; padding: 8px 12px; border-radius: 6px;");
    m_statusLabel->setText(msg);
    m_statusLabel->setVisible(true);
    QTimer::singleShot(5000, this, [this]() { m_statusLabel->setVisible(false); });
}

void LoginPage::showSuccess(const QString &msg) {
    m_statusLabel->setStyleSheet("color: #10B981; font-size: 12px; background: #D1FAE5; padding: 8px 12px; border-radius: 6px;");
    m_statusLabel->setText(msg);
    m_statusLabel->setVisible(true);
}

void LoginPage::mouseMoveEvent(QMouseEvent *event) {
    m_mousePos = event->pos();
    QWidget::mouseMoveEvent(event);
}

void LoginPage::loadSavedCredentials() {
    QSettings s("DocAI", "DocAI");
    bool remember = s.value("login/remember", false).toBool();
    bool autoLogin = s.value("login/autoLogin", false).toBool();
    m_rememberCheck->setChecked(remember);
    m_autoLoginCheck->setChecked(autoLogin);
    if (remember) {
        m_loginUsername->setText(s.value("login/username").toString());
        // Simple XOR obfuscation for stored password
        QByteArray enc = QByteArray::fromBase64(s.value("login/pwd").toByteArray());
        QByteArray key = "DocAI2025";
        QByteArray dec;
        for (int i = 0; i < enc.size(); ++i)
            dec.append(enc[i] ^ key[i % key.size()]);
        m_loginPassword->setText(QString::fromUtf8(dec));
        if (autoLogin && !m_loginUsername->text().isEmpty() && !m_loginPassword->text().isEmpty()) {
            QTimer::singleShot(500, this, &LoginPage::handleLogin);
        }
    }
}

void LoginPage::saveCredentials() {
    QSettings s("DocAI", "DocAI");
    s.setValue("login/remember", m_rememberCheck->isChecked());
    s.setValue("login/autoLogin", m_autoLoginCheck->isChecked());
    if (m_rememberCheck->isChecked()) {
        s.setValue("login/username", m_loginUsername->text().trimmed());
        QByteArray raw = m_loginPassword->text().toUtf8();
        QByteArray key = "DocAI2025";
        QByteArray enc;
        for (int i = 0; i < raw.size(); ++i)
            enc.append(raw[i] ^ key[i % key.size()]);
        s.setValue("login/pwd", enc.toBase64());
    } else {
        s.remove("login/username");
        s.remove("login/pwd");
    }
}
