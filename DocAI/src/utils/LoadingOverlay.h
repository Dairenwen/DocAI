#ifndef LOADINGOVERLAY_H
#define LOADINGOVERLAY_H

#include <QWidget>
#include <QTimer>
#include <QPainter>
#include <QEvent>

class LoadingOverlay : public QWidget {
    Q_OBJECT
public:
    explicit LoadingOverlay(QWidget *parent = nullptr)
        : QWidget(parent), m_angle(0) {
        setAttribute(Qt::WA_TransparentForMouseEvents, false);
        setVisible(false);
        m_timer = new QTimer(this);
        connect(m_timer, &QTimer::timeout, [this]() {
            m_angle = (m_angle + 30) % 360;
            update();
        });
        if (parent) {
            resize(parent->size());
            parent->installEventFilter(this);
        }
    }

    void showLoading(const QString &text = QString()) {
        m_text = text;
        if (parentWidget()) resize(parentWidget()->size());
        raise();
        setVisible(true);
        m_timer->start(50);
    }

    void hideLoading() {
        setVisible(false);
        m_timer->stop();
    }

protected:
    bool eventFilter(QObject *obj, QEvent *event) override {
        if (obj == parentWidget() && event->type() == QEvent::Resize) {
            resize(parentWidget()->size());
        }
        return QWidget::eventFilter(obj, event);
    }

    void paintEvent(QPaintEvent *) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);

        // Semi-transparent overlay
        p.fillRect(rect(), QColor(249, 250, 251, 180));

        // Spinner
        int cx = width() / 2;
        int cy = height() / 2;
        int radius = 18;
        p.translate(cx, cy);
        p.rotate(m_angle);
        for (int i = 0; i < 12; i++) {
            int alpha = 40 + i * 18;
            p.setPen(Qt::NoPen);
            p.setBrush(QColor(79, 70, 229, alpha));
            p.drawRoundedRect(-2, -radius, 4, 10, 2, 2);
            p.rotate(30);
        }
        p.resetTransform();

        // Text
        if (!m_text.isEmpty()) {
            p.setPen(QColor("#4F46E5"));
            QFont font("Microsoft YaHei UI", 12);
            p.setFont(font);
            p.drawText(QRect(0, cy + 30, width(), 30), Qt::AlignHCenter, m_text);
        }
    }

private:
    QTimer *m_timer;
    int m_angle;
    QString m_text;
};

#endif // LOADINGOVERLAY_H
