#ifndef LOGINPAGE_H
#define LOGINPAGE_H

#include <QWidget>
#include <QLineEdit>
#include <QPushButton>
#include <QLabel>
#include <QStackedWidget>
#include <QDialog>
#include <QTimer>
#include <QCheckBox>

struct FloatingChar {
    double x, y;         // position
    double vx, vy;       // velocity
    double baseX, baseY; // rest position
    QString ch;
    double size;
    double opacity;
};

class LoginPage : public QWidget {
    Q_OBJECT
public:
    explicit LoginPage(QWidget *parent = nullptr);

signals:
    void loginSuccess();

protected:
    void paintEvent(QPaintEvent *event) override;
    void mouseMoveEvent(QMouseEvent *event) override;

private slots:
    void handleLogin();
    void handleRegister();
    void switchToRegister();
    void switchToLogin();
    void openEmailAuth();
    void openResetPassword();
    void animateTick();

private:
    void setupUI();
    void initParticles();
    void showError(const QString &msg);
    void showSuccess(const QString &msg);
    void loadSavedCredentials();
    void saveCredentials();

    QStackedWidget *m_formStack;
    // Login
    QLineEdit *m_loginUsername;
    QLineEdit *m_loginPassword;
    QPushButton *m_loginBtn;
    QCheckBox *m_rememberCheck;
    QCheckBox *m_autoLoginCheck;
    // Register
    QLineEdit *m_regUsername;
    QLineEdit *m_regNickname;
    QLineEdit *m_regPassword;
    QLineEdit *m_regConfirmPwd;
    QPushButton *m_regBtn;
    // Status
    QLabel *m_statusLabel;
    bool m_loading = false;

    // Floating characters
    QList<FloatingChar> m_particles;
    QTimer *m_animTimer;
    QPointF m_mousePos;
};

#endif // LOGINPAGE_H
