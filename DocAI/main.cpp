#include "src/MainWindow.h"
#include <QApplication>
#include <QFont>
#include <QTextCodec>
#include <QFile>
#include <QLockFile>
#include <QDir>
#include <QMessageBox>
#include <QIcon>
#include <QPainter>

static QIcon createAppIcon() {
    QIcon icon;
    for (int s : {16, 32, 48, 64, 128, 256}) {
        QPixmap pix(s, s);
        pix.fill(Qt::transparent);
        QPainter p(&pix);
        p.setRenderHint(QPainter::Antialiasing);
        // Background rounded rect
        double r = s / 5.0;
        p.setBrush(QColor("#4F46E5"));
        p.setPen(Qt::NoPen);
        p.drawRoundedRect(0, 0, s, s, r, r);
        // Document body
        int m = (int)(s * 0.22);
        int fold = (int)(s * 0.15);
        int top = (int)(s * 0.15);
        int bot = (int)(s * 0.85);
        QPolygon doc;
        doc << QPoint(m, top) << QPoint(s - m - fold, top) << QPoint(s - m, top + fold)
            << QPoint(s - m, bot) << QPoint(m, bot);
        p.setBrush(Qt::white);
        p.drawPolygon(doc);
        // Corner fold
        QPolygon fld;
        fld << QPoint(s - m - fold, top) << QPoint(s - m - fold, top + fold) << QPoint(s - m, top + fold);
        p.setBrush(QColor("#C7D2FE"));
        p.drawPolygon(fld);
        // Text lines
        int lx1 = m + (int)(s * 0.1);
        int lx2 = s - m - (int)(s * 0.1);
        int ly = top + fold + (int)(s * 0.12);
        int sp = qMax((int)(s * 0.11), 3);
        int lw = qMax(s / 25, 1);
        p.setBrush(QColor("#A5B4FC"));
        for (int i = 0; i < 3; i++) {
            int y = ly + i * sp;
            if (y + lw < bot - (int)(s * 0.08)) {
                int ex = (i < 2) ? lx2 : lx1 + (int)((lx2 - lx1) * 0.65);
                p.drawRect(lx1, y, ex - lx1, lw);
            }
        }
        p.end();
        icon.addPixmap(pix);
    }
    return icon;
}

int main(int argc, char *argv[])
{
    QTextCodec::setCodecForLocale(QTextCodec::codecForName("UTF-8"));

    QApplication a(argc, argv);
    a.setApplicationName("DocAI");
    a.setOrganizationName("DocAI");

    // Singleton mode
    QLockFile lockFile(QDir::tempPath() + "/DocAI.lock");
    if (!lockFile.tryLock(100)) {
        QMessageBox::warning(nullptr, "DocAI", "DocAI \xe5\xb7\xb2\xe5\x9c\xa8\xe8\xbf\x90\xe8\xa1\x8c\xe4\xb8\xad");
        return 0;
    }

    QFont font("Microsoft YaHei UI", 9);
    a.setFont(font);

    QIcon appIcon = createAppIcon();
    a.setWindowIcon(appIcon);

    // Load global stylesheet from resource
    QFile qssFile(":/style.qss");
    if (qssFile.open(QFile::ReadOnly | QFile::Text)) {
        a.setStyleSheet(qssFile.readAll());
        qssFile.close();
    }

    MainWindow w;
    w.setWindowIcon(appIcon);
    w.show();
    return a.exec();
}
