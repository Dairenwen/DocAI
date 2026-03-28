#ifndef TOKENMANAGER_H
#define TOKENMANAGER_H

#include <QString>
#include <QSettings>

class TokenManager {
public:
    static TokenManager& instance();

    void setToken(const QString &token);
    QString token() const;
    void setUserId(const QString &id);
    QString userId() const;
    void setUsername(const QString &name);
    QString username() const;
    void setNickname(const QString &nick);
    QString nickname() const;
    bool isLoggedIn() const;
    void clear();

private:
    TokenManager();
    QSettings m_settings;
};

#endif // TOKENMANAGER_H
