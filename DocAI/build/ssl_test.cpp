#include <QCoreApplication>
#include <QSslSocket>
#include <QDebug>
int main(int argc, char *argv[]) {
    QCoreApplication a(argc, argv);
    qDebug() << "SSL supported:" << QSslSocket::supportsSsl();
    qDebug() << "SSL build version:" << QSslSocket::sslLibraryBuildVersionString();
    qDebug() << "SSL runtime version:" << QSslSocket::sslLibraryVersionString();
    return 0;
}
