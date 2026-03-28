/****************************************************************************
** Meta object code from reading C++ file 'AIChatPage.h'
**
** Created by: The Qt Meta Object Compiler version 67 (Qt 5.14.2)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include <memory>
#include "../../DocAI/src/pages/AIChatPage.h"
#include <QtCore/qbytearray.h>
#include <QtCore/qmetatype.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'AIChatPage.h' doesn't include <QObject>."
#elif Q_MOC_OUTPUT_REVISION != 67
#error "This file was generated using the moc from 5.14.2. It"
#error "cannot be used with the include files from this version of Qt."
#error "(The moc has changed too much.)"
#endif

QT_BEGIN_MOC_NAMESPACE
QT_WARNING_PUSH
QT_WARNING_DISABLE_DEPRECATED
struct qt_meta_stringdata_AIChatPage_t {
    QByteArrayData data[29];
    char stringdata0[361];
};
#define QT_MOC_LITERAL(idx, ofs, len) \
    Q_STATIC_BYTE_ARRAY_DATA_HEADER_INITIALIZER_WITH_OFFSET(len, \
    qptrdiff(offsetof(qt_meta_stringdata_AIChatPage_t, stringdata0) + ofs \
        - idx * sizeof(QByteArrayData)) \
    )
static const qt_meta_stringdata_AIChatPage_t qt_meta_stringdata_AIChatPage = {
    {
QT_MOC_LITERAL(0, 0, 10), // "AIChatPage"
QT_MOC_LITERAL(1, 11, 17), // "setLinkedDocument"
QT_MOC_LITERAL(2, 29, 0), // ""
QT_MOC_LITERAL(3, 30, 5), // "docId"
QT_MOC_LITERAL(4, 36, 7), // "docName"
QT_MOC_LITERAL(5, 44, 23), // "linkDocumentAndOpenChat"
QT_MOC_LITERAL(6, 68, 11), // "sendMessage"
QT_MOC_LITERAL(7, 80, 11), // "sendCommand"
QT_MOC_LITERAL(8, 92, 3), // "cmd"
QT_MOC_LITERAL(9, 96, 14), // "stopGeneration"
QT_MOC_LITERAL(10, 111, 21), // "createNewConversation"
QT_MOC_LITERAL(11, 133, 16), // "loadConversation"
QT_MOC_LITERAL(12, 150, 6), // "convId"
QT_MOC_LITERAL(13, 157, 18), // "deleteConversation"
QT_MOC_LITERAL(14, 176, 14), // "selectDocument"
QT_MOC_LITERAL(15, 191, 9), // "onSseText"
QT_MOC_LITERAL(16, 201, 4), // "text"
QT_MOC_LITERAL(17, 206, 13), // "onSseComplete"
QT_MOC_LITERAL(18, 220, 8), // "excelUrl"
QT_MOC_LITERAL(19, 229, 10), // "onSseError"
QT_MOC_LITERAL(20, 240, 3), // "err"
QT_MOC_LITERAL(21, 244, 12), // "exportResult"
QT_MOC_LITERAL(22, 257, 20), // "loadConversationList"
QT_MOC_LITERAL(23, 278, 9), // "clearChat"
QT_MOC_LITERAL(24, 288, 20), // "regenerateLastPrompt"
QT_MOC_LITERAL(25, 309, 16), // "continueDialogue"
QT_MOC_LITERAL(26, 326, 14), // "previewContent"
QT_MOC_LITERAL(27, 341, 7), // "content"
QT_MOC_LITERAL(28, 349, 11) // "sendToEmail"

    },
    "AIChatPage\0setLinkedDocument\0\0docId\0"
    "docName\0linkDocumentAndOpenChat\0"
    "sendMessage\0sendCommand\0cmd\0stopGeneration\0"
    "createNewConversation\0loadConversation\0"
    "convId\0deleteConversation\0selectDocument\0"
    "onSseText\0text\0onSseComplete\0excelUrl\0"
    "onSseError\0err\0exportResult\0"
    "loadConversationList\0clearChat\0"
    "regenerateLastPrompt\0continueDialogue\0"
    "previewContent\0content\0sendToEmail"
};
#undef QT_MOC_LITERAL

static const uint qt_meta_data_AIChatPage[] = {

 // content:
       8,       // revision
       0,       // classname
       0,    0, // classinfo
      19,   14, // methods
       0,    0, // properties
       0,    0, // enums/sets
       0,    0, // constructors
       0,       // flags
       0,       // signalCount

 // slots: name, argc, parameters, tag, flags
       1,    2,  109,    2, 0x0a /* Public */,
       5,    2,  114,    2, 0x0a /* Public */,
       6,    0,  119,    2, 0x08 /* Private */,
       7,    1,  120,    2, 0x08 /* Private */,
       9,    0,  123,    2, 0x08 /* Private */,
      10,    0,  124,    2, 0x08 /* Private */,
      11,    1,  125,    2, 0x08 /* Private */,
      13,    1,  128,    2, 0x08 /* Private */,
      14,    0,  131,    2, 0x08 /* Private */,
      15,    1,  132,    2, 0x08 /* Private */,
      17,    2,  135,    2, 0x08 /* Private */,
      19,    1,  140,    2, 0x08 /* Private */,
      21,    0,  143,    2, 0x08 /* Private */,
      22,    0,  144,    2, 0x08 /* Private */,
      23,    0,  145,    2, 0x08 /* Private */,
      24,    0,  146,    2, 0x08 /* Private */,
      25,    0,  147,    2, 0x08 /* Private */,
      26,    1,  148,    2, 0x08 /* Private */,
      28,    1,  151,    2, 0x08 /* Private */,

 // slots: parameters
    QMetaType::Void, QMetaType::Int, QMetaType::QString,    3,    4,
    QMetaType::Void, QMetaType::Int, QMetaType::QString,    3,    4,
    QMetaType::Void,
    QMetaType::Void, QMetaType::QString,    8,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::Int,   12,
    QMetaType::Void, QMetaType::Int,   12,
    QMetaType::Void,
    QMetaType::Void, QMetaType::QString,   16,
    QMetaType::Void, QMetaType::QString, QMetaType::QString,   16,   18,
    QMetaType::Void, QMetaType::QString,   20,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::QString,   27,
    QMetaType::Void, QMetaType::QString,   27,

       0        // eod
};

void AIChatPage::qt_static_metacall(QObject *_o, QMetaObject::Call _c, int _id, void **_a)
{
    if (_c == QMetaObject::InvokeMetaMethod) {
        auto *_t = static_cast<AIChatPage *>(_o);
        Q_UNUSED(_t)
        switch (_id) {
        case 0: _t->setLinkedDocument((*reinterpret_cast< int(*)>(_a[1])),(*reinterpret_cast< const QString(*)>(_a[2]))); break;
        case 1: _t->linkDocumentAndOpenChat((*reinterpret_cast< int(*)>(_a[1])),(*reinterpret_cast< const QString(*)>(_a[2]))); break;
        case 2: _t->sendMessage(); break;
        case 3: _t->sendCommand((*reinterpret_cast< const QString(*)>(_a[1]))); break;
        case 4: _t->stopGeneration(); break;
        case 5: _t->createNewConversation(); break;
        case 6: _t->loadConversation((*reinterpret_cast< int(*)>(_a[1]))); break;
        case 7: _t->deleteConversation((*reinterpret_cast< int(*)>(_a[1]))); break;
        case 8: _t->selectDocument(); break;
        case 9: _t->onSseText((*reinterpret_cast< const QString(*)>(_a[1]))); break;
        case 10: _t->onSseComplete((*reinterpret_cast< const QString(*)>(_a[1])),(*reinterpret_cast< const QString(*)>(_a[2]))); break;
        case 11: _t->onSseError((*reinterpret_cast< const QString(*)>(_a[1]))); break;
        case 12: _t->exportResult(); break;
        case 13: _t->loadConversationList(); break;
        case 14: _t->clearChat(); break;
        case 15: _t->regenerateLastPrompt(); break;
        case 16: _t->continueDialogue(); break;
        case 17: _t->previewContent((*reinterpret_cast< const QString(*)>(_a[1]))); break;
        case 18: _t->sendToEmail((*reinterpret_cast< const QString(*)>(_a[1]))); break;
        default: ;
        }
    }
}

QT_INIT_METAOBJECT const QMetaObject AIChatPage::staticMetaObject = { {
    QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
    qt_meta_stringdata_AIChatPage.data,
    qt_meta_data_AIChatPage,
    qt_static_metacall,
    nullptr,
    nullptr
} };


const QMetaObject *AIChatPage::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void *AIChatPage::qt_metacast(const char *_clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_AIChatPage.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int AIChatPage::qt_metacall(QMetaObject::Call _c, int _id, void **_a)
{
    _id = QWidget::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod) {
        if (_id < 19)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 19;
    } else if (_c == QMetaObject::RegisterMethodArgumentMetaType) {
        if (_id < 19)
            *reinterpret_cast<int*>(_a[0]) = -1;
        _id -= 19;
    }
    return _id;
}
QT_WARNING_POP
QT_END_MOC_NAMESPACE
