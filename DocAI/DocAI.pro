QT       += core gui network

greaterThan(QT_MAJOR_VERSION, 4): QT += widgets

CONFIG += c++11
QMAKE_CXXFLAGS += -finput-charset=UTF-8 -fexec-charset=UTF-8

# The following define makes your compiler emit warnings if you use
# any Qt feature that has been marked deprecated (the exact warnings
# depend on your compiler). Please consult the documentation of the
# deprecated API in order to know how to port your code away from it.
DEFINES += QT_DEPRECATED_WARNINGS

# You can also make your code fail to compile if it uses deprecated APIs.
# In order to do so, uncomment the following line.
# You can also select to disable deprecated APIs only up to a certain version of Qt.
#DEFINES += QT_DISABLE_DEPRECATED_BEFORE=0x060000    # disables all the APIs deprecated before Qt 6.0.0

SOURCES += \
    main.cpp \
    src/utils/TokenManager.cpp \
    src/network/ApiClient.cpp \
    src/network/SseClient.cpp \
    src/pages/LoginPage.cpp \
    src/pages/DashboardPage.cpp \
    src/pages/DocumentListPage.cpp \
    src/pages/AIChatPage.cpp \
    src/pages/AutoFillPage.cpp \
    src/widgets/Sidebar.cpp \
    src/widgets/TopBar.cpp \
    src/MainWindow.cpp

HEADERS += \
    src/utils/TokenManager.h \
    src/utils/IconHelper.h \
    src/utils/LoadingOverlay.h \
    src/utils/Toast.h \
    src/models/DataModels.h \
    src/network/ApiClient.h \
    src/network/SseClient.h \
    src/pages/LoginPage.h \
    src/pages/DashboardPage.h \
    src/pages/DocumentListPage.h \
    src/pages/AIChatPage.h \
    src/pages/AutoFillPage.h \
    src/widgets/Sidebar.h \
    src/widgets/TopBar.h \
    src/MainWindow.h

RESOURCES += \
    src/resources/resources.qrc

RC_FILE = docai.rc

# Default rules for deployment.
qnx: target.path = /tmp/$${TARGET}/bin
else: unix:!android: target.path = /opt/$${TARGET}/bin
!isEmpty(target.path): INSTALLS += target
