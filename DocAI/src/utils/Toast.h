#ifndef TOAST_H
#define TOAST_H

#include <QWidget>
#include <QLabel>
#include <QTimer>
#include <QApplication>
#include <QPropertyAnimation>
#include <QGraphicsOpacityEffect>
#include <QHBoxLayout>

class Toast : public QWidget {
    Q_OBJECT
public:
    enum Type { Info, Success, Warning, Error };

    static void showMessage(QWidget *parent, const QString &message, Type type = Info, int durationMs = 2500) {
        Toast *t = new Toast(parent, message, type, durationMs);
        t->popup();
    }

private:
    explicit Toast(QWidget *parent, const QString &message, Type type, int durationMs)
        : QWidget(parent), m_duration(durationMs) {
        setWindowFlags(Qt::Widget);
        setAttribute(Qt::WA_DeleteOnClose);
        setAttribute(Qt::WA_TransparentForMouseEvents);

        QString bgColor, textColor, icon;
        switch (type) {
        case Success: bgColor = "#D1FAE5"; textColor = "#065F46"; icon = "\xe2\x9c\x93"; break;
        case Warning: bgColor = "#FEF3C7"; textColor = "#92400E"; icon = "\xe2\x9a\xa0"; break;
        case Error:   bgColor = "#FEE2E2"; textColor = "#991B1B"; icon = "\xe2\x9c\x97"; break;
        default:      bgColor = "#EEF2FF"; textColor = "#3730A3"; icon = "\xe2\x84\xb9"; break;
        }

        setStyleSheet(QString("background: %1; border-radius: 8px; border: 1px solid %2;").arg(bgColor, textColor));

        QHBoxLayout *lay = new QHBoxLayout(this);
        lay->setContentsMargins(16, 10, 16, 10);
        lay->setSpacing(8);

        QLabel *iconLbl = new QLabel(icon);
        iconLbl->setStyleSheet(QString("color: %1; font-size: 14px; background: transparent;").arg(textColor));
        QLabel *msgLbl = new QLabel(message);
        msgLbl->setStyleSheet(QString("color: %1; font-size: 13px; font-weight: 500; background: transparent;").arg(textColor));
        msgLbl->setWordWrap(true);

        lay->addWidget(iconLbl);
        lay->addWidget(msgLbl, 1);

        adjustSize();
        setFixedHeight(sizeHint().height());
        setMinimumWidth(240);
        setMaximumWidth(400);

        m_opacity = new QGraphicsOpacityEffect(this);
        m_opacity->setOpacity(0.0);
        setGraphicsEffect(m_opacity);
    }

    void popup() {
        if (!parentWidget()) { deleteLater(); return; }
        int pw = parentWidget()->width();
        int x = (pw - width()) / 2;
        move(x, 20);
        setVisible(true);
        raise();

        // Fade in
        QPropertyAnimation *fadeIn = new QPropertyAnimation(m_opacity, "opacity", this);
        fadeIn->setDuration(200);
        fadeIn->setStartValue(0.0);
        fadeIn->setEndValue(1.0);
        fadeIn->start(QAbstractAnimation::DeleteWhenStopped);

        // Auto close
        QTimer::singleShot(m_duration, this, [this]() {
            QPropertyAnimation *fadeOut = new QPropertyAnimation(m_opacity, "opacity", this);
            fadeOut->setDuration(300);
            fadeOut->setStartValue(1.0);
            fadeOut->setEndValue(0.0);
            connect(fadeOut, &QPropertyAnimation::finished, this, &QWidget::close);
            fadeOut->start(QAbstractAnimation::DeleteWhenStopped);
        });
    }

    int m_duration;
    QGraphicsOpacityEffect *m_opacity;
};

#endif // TOAST_H
