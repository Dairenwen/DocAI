#ifndef DOCAI_H
#define DOCAI_H

#include <QWidget>

QT_BEGIN_NAMESPACE
namespace Ui { class docai; }
QT_END_NAMESPACE

class docai : public QWidget
{
    Q_OBJECT

public:
    docai(QWidget *parent = nullptr);
    ~docai();

private:
    Ui::docai *ui;
};
#endif // DOCAI_H
