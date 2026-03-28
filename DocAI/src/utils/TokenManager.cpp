#include "TokenManager.h"

TokenManager& TokenManager::instance() {
    static TokenManager inst;
    return inst;
}

TokenManager::TokenManager()
    : m_settings("DocAI", "DocAI") {}

void TokenManager::setToken(const QString &token) { m_settings.setValue("auth/token", token); }
QString TokenManager::token() const { return m_settings.value("auth/token").toString(); }
void TokenManager::setUserId(const QString &id) { m_settings.setValue("auth/userId", id); }
QString TokenManager::userId() const { return m_settings.value("auth/userId").toString(); }
void TokenManager::setUsername(const QString &name) { m_settings.setValue("auth/username", name); }
QString TokenManager::username() const { return m_settings.value("auth/username").toString(); }
void TokenManager::setNickname(const QString &nick) { m_settings.setValue("auth/nickname", nick); }
QString TokenManager::nickname() const { return m_settings.value("auth/nickname").toString(); }
bool TokenManager::isLoggedIn() const { return !token().isEmpty(); }

void TokenManager::clear() {
    m_settings.remove("auth/token");
    m_settings.remove("auth/userId");
    m_settings.remove("auth/username");
    m_settings.remove("auth/nickname");
}
