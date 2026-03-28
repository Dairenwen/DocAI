#include "docai.h"
#include "ui_docai.h"

docai::docai(QWidget *parent)
    : QWidget(parent)
    , ui(new Ui::docai)
{
    ui->setupUi(this);
}

docai::~docai()
{
    delete ui;
}

