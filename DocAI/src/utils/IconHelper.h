#ifndef ICONHELPER_H
#define ICONHELPER_H

#include <QPixmap>
#include <QPainter>
#include <QIcon>
#include <QtMath>

class IconHelper {
public:
    static QPixmap document(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        p.setBrush(Qt::NoBrush);
        int m = size / 5;
        int fold = size / 4;
        QPolygon poly;
        poly << QPoint(m, m) << QPoint(size - m - fold, m)
             << QPoint(size - m, m + fold) << QPoint(size - m, size - m)
             << QPoint(m, size - m);
        p.drawPolygon(poly);
        p.drawLine(size - m - fold, m, size - m - fold, m + fold);
        p.drawLine(size - m - fold, m + fold, size - m, m + fold);
        // text lines
        int lx = m + 2, rx = size - m - 2;
        for (int i = 0; i < 3; i++) {
            int ly = size / 2 + i * 3;
            if (ly < size - m - 1)
                p.drawLine(lx, ly, rx - i * 2, ly);
        }
        p.end();
        return pix;
    }

    static QPixmap folder(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        int tabW = size / 3;
        p.drawRect(m, m + 3, size - 2 * m, size - 2 * m - 3);
        p.drawLine(m, m + 3, m, m);
        p.drawLine(m, m, m + tabW, m);
        p.drawLine(m + tabW, m, m + tabW + 2, m + 3);
        p.end();
        return pix;
    }

    static QPixmap grid(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        int w = size - 2 * m;
        p.drawRect(m, m, w, w);
        p.drawLine(m + w / 2, m, m + w / 2, m + w);
        p.drawLine(m, m + w / 2, m + w, m + w / 2);
        p.end();
        return pix;
    }

    static QPixmap chat(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        p.setBrush(Qt::NoBrush);
        int m = size / 5;
        QRect r(m, m, size - 2 * m, size - 2 * m - 2);
        p.drawRoundedRect(r, 3, 3);
        // tail
        QPolygon tail;
        tail << QPoint(m + 3, r.bottom())
             << QPoint(m + 2, r.bottom() + 3)
             << QPoint(m + 7, r.bottom());
        p.drawPolyline(tail);
        p.end();
        return pix;
    }

    static QPixmap pencil(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 4;
        // pencil body (diagonal)
        p.drawLine(m + 2, size - m - 2, size - m - 2, m + 2);
        p.drawLine(m, size - m, m + 2, size - m - 2);
        p.drawLine(m, size - m, size - m - 4, m);
        p.drawLine(size - m - 4, m, size - m - 2, m + 2);
        p.end();
        return pix;
    }

    static QPixmap upload(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int cx = size / 2, m = size / 5;
        p.drawLine(cx, m + 1, cx, size - m - 2);
        // arrow head
        p.drawLine(cx, m + 1, cx - 3, m + 5);
        p.drawLine(cx, m + 1, cx + 3, m + 5);
        // base line
        p.drawLine(m, size - m, size - m, size - m);
        p.end();
        return pix;
    }

    static QPixmap download(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int cx = size / 2, m = size / 5;
        p.drawLine(cx, m + 1, cx, size - m - 4);
        p.drawLine(cx, size - m - 4, cx - 3, size - m - 8);
        p.drawLine(cx, size - m - 4, cx + 3, size - m - 8);
        p.drawLine(m, size - m, size - m, size - m);
        p.end();
        return pix;
    }

    static QPixmap refresh(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        qreal s = size;
        qreal cx = s / 2, cy = s / 2, r = s * 0.35;
        // Circular arrow: draw arc from ~40° spanning ~280°
        p.setPen(QPen(color, qMax(1.5, s / 10.0), Qt::SolidLine, Qt::RoundCap));
        p.drawArc(QRectF(cx - r, cy - r, 2 * r, 2 * r), 50 * 16, 280 * 16);
        // Arrow head at top-right end of arc (≈50°)
        qreal angle = 50.0 * 3.14159265 / 180.0;
        qreal ax = cx + r * qCos(angle);
        qreal ay = cy - r * qSin(angle);
        qreal alen = s * 0.22;
        p.setBrush(color);
        p.setPen(Qt::NoPen);
        QPolygonF arrow;
        arrow << QPointF(ax, ay)
              << QPointF(ax + alen, ay + alen * 0.15)
              << QPointF(ax + alen * 0.15, ay - alen);
        p.drawPolygon(arrow);
        p.end();
        return pix;
    }

    static QPixmap trash(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 4;
        // lid
        p.drawLine(m - 1, m + 2, size - m + 1, m + 2);
        p.drawLine(cx(size) - 2, m, cx(size) + 2, m);
        // body
        p.drawRect(m + 1, m + 2, size - 2 * m - 2, size - 2 * m - 2);
        // lines inside
        int bTop = m + 5, bBot = size - m - 2;
        p.drawLine(cx(size) - 2, bTop, cx(size) - 2, bBot);
        p.drawLine(cx(size) + 2, bTop, cx(size) + 2, bBot);
        p.end();
        return pix;
    }

    static QPixmap search(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int r = size / 4;
        int cx = size / 2 - 1, cy = size / 2 - 1;
        p.drawEllipse(cx - r, cy - r, 2 * r, 2 * r);
        p.drawLine(cx + r, cy + r, size - size / 5, size - size / 5);
        p.end();
        return pix;
    }

    static QPixmap link(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        p.setBrush(Qt::NoBrush);
        int m = size / 4;
        p.drawRoundedRect(m, m + 2, size / 3, size - 2 * m - 4, 3, 3);
        p.drawRoundedRect(size - m - size / 3, m + 2, size / 3, size - 2 * m - 4, 3, 3);
        p.drawLine(m + size / 6, size / 2, size - m - size / 6, size / 2);
        p.end();
        return pix;
    }

    static QPixmap lightning(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 4;
        QPolygon bolt;
        bolt << QPoint(size / 2 + 2, m) << QPoint(m + 1, size / 2)
             << QPoint(size / 2, size / 2) << QPoint(size / 2 - 2, size - m)
             << QPoint(size - m - 1, size / 2) << QPoint(size / 2, size / 2);
        p.drawPolyline(bolt);
        p.end();
        return pix;
    }

    static QPixmap robot(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        p.drawRoundedRect(m, m + 2, size - 2 * m, size - 2 * m - 2, 3, 3);
        // eyes
        int ey = m + (size - 2 * m) / 3 + 2;
        p.setBrush(color);
        p.drawEllipse(size / 3 - 1, ey, 3, 3);
        p.drawEllipse(2 * size / 3 - 2, ey, 3, 3);
        // antenna
        p.drawLine(size / 2, m + 2, size / 2, m - 1);
        p.drawEllipse(size / 2 - 1, m - 3, 3, 3);
        p.end();
        return pix;
    }

    static QPixmap chart(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 5;
        // bars
        int barW = (size - 2 * m) / 5;
        int base = size - m;
        p.drawRect(m + barW * 0, base - 4, barW - 1, 4);
        p.drawRect(m + barW * 1 + 1, base - 8, barW - 1, 8);
        p.drawRect(m + barW * 2 + 2, base - 6, barW - 1, 6);
        p.drawRect(m + barW * 3 + 3, base - 11, barW - 1, 11);
        p.end();
        return pix;
    }

    static QPixmap copy(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        p.setBrush(Qt::NoBrush);
        int m = size / 5;
        p.drawRect(m + 2, m, size - 2 * m - 2, size - 2 * m - 2);
        p.drawRect(m, m + 2, size - 2 * m - 2, size - 2 * m - 2);
        p.end();
        return pix;
    }

    static QPixmap table(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        int w = size - 2 * m, h = size - 2 * m;
        p.drawRect(m, m, w, h);
        p.drawLine(m, m + h / 3, m + w, m + h / 3);
        p.drawLine(m, m + 2 * h / 3, m + w, m + 2 * h / 3);
        p.drawLine(m + w / 3, m, m + w / 3, m + h);
        p.end();
        return pix;
    }

    static QPixmap home(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        // roof
        QPolygon roof;
        roof << QPoint(size / 2, m) << QPoint(m, size / 2) << QPoint(size - m, size / 2);
        p.drawPolyline(roof);
        // walls
        p.drawRect(m + 2, size / 2, size - 2 * m - 4, size - m - size / 2);
        p.end();
        return pix;
    }

    static QPixmap announce(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 4;
        // megaphone shape
        QPolygon mega;
        mega << QPoint(m, size / 2 - 2) << QPoint(size / 2, m)
             << QPoint(size / 2, size - m) << QPoint(m, size / 2 + 2);
        p.drawPolygon(mega);
        p.drawLine(size / 2, size / 2, size - m, size / 2);
        p.end();
        return pix;
    }

    static QPixmap mail(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 4;
        p.drawRect(m, m + 1, size - 2 * m, size - 2 * m - 2);
        p.drawLine(m, m + 1, size / 2, size / 2);
        p.drawLine(size - m, m + 1, size / 2, size / 2);
        p.end();
        return pix;
    }

    static QPixmap bulb(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int r = size / 3;
        p.drawEllipse(size / 2 - r, size / 5, 2 * r, 2 * r);
        p.drawLine(size / 2 - 2, size / 5 + 2 * r, size / 2 - 2, size - size / 4);
        p.drawLine(size / 2 + 2, size / 5 + 2 * r, size / 2 + 2, size - size / 4);
        p.drawLine(size / 2 - 2, size - size / 4, size / 2 + 2, size - size / 4);
        p.end();
        return pix;
    }

    static QPixmap news(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 5;
        p.drawRect(m, m, size - 2 * m, size - 2 * m);
        // header line
        p.drawLine(m + 2, m + 4, size - m - 2, m + 4);
        // text lines
        for (int i = 0; i < 3; i++)
            p.drawLine(m + 2, m + 8 + i * 3, size - m - 4, m + 8 + i * 3);
        p.end();
        return pix;
    }

    static QPixmap plus(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 4;
        p.drawLine(size / 2, m, size / 2, size - m);
        p.drawLine(m, size / 2, size - m, size / 2);
        p.end();
        return pix;
    }

    static QPixmap globe(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int r = size / 3;
        p.drawEllipse(size / 2 - r, size / 2 - r, 2 * r, 2 * r);
        p.drawLine(size / 2, size / 2 - r, size / 2, size / 2 + r);
        p.drawLine(size / 2 - r, size / 2, size / 2 + r, size / 2);
        p.end();
        return pix;
    }

    static QPixmap ruler(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.2));
        int m = size / 4;
        p.drawRect(m, m, size - 2 * m, size - 2 * m);
        // tick marks
        for (int i = 1; i < 4; i++) {
            int x = m + i * (size - 2 * m) / 4;
            p.drawLine(x, m, x, m + 3);
        }
        p.end();
        return pix;
    }

    static QPixmap closeX(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 4;
        p.drawLine(m, m, size - m, size - m);
        p.drawLine(size - m, m, m, size - m);
        p.end();
        return pix;
    }

    static QPixmap rocket(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        int m = size / 5;
        // simple rocket shape - triangle + body
        p.drawLine(size / 2, m, size - m - 2, size / 2);
        p.drawLine(size / 2, m, m + 2, size / 2);
        p.drawLine(m + 2, size / 2, m + 2, size - m - 2);
        p.drawLine(size - m - 2, size / 2, size - m - 2, size - m - 2);
        p.drawLine(m + 2, size - m - 2, size - m - 2, size - m - 2);
        p.end();
        return pix;
    }

    static QPixmap eye(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5));
        p.setBrush(Qt::NoBrush);
        int cy = size / 2;
        // eye outline (ellipse)
        p.drawEllipse(size / 6, cy - size / 5, size * 2 / 3, size * 2 / 5);
        // pupil
        p.setBrush(color);
        int r = size / 7;
        p.drawEllipse(QPointF(size / 2.0, cy), r, r);
        p.end();
        return pix;
    }

    static QPixmap chevronDown(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.5, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
        int m = size / 4;
        p.drawLine(m, size * 2 / 5, size / 2, size * 3 / 5);
        p.drawLine(size / 2, size * 3 / 5, size - m, size * 2 / 5);
        p.end();
        return pix;
    }

    static QPixmap pin(int size = 20, const QColor &color = QColor("#6B7280")) {
        QPixmap pix(size, size);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        p.setPen(QPen(color, 1.3));
        p.setBrush(Qt::NoBrush);
        int m = size / 5;
        // pin head (circle)
        p.drawEllipse(size / 2 - m, m, 2 * m, 2 * m);
        // pin needle
        p.drawLine(size / 2, m + 2 * m, size / 2, size - m);
        p.end();
        return pix;
    }

private:
    static int cx(int size) { return size / 2; }
};

#endif // ICONHELPER_H
