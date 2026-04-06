/****************************************************************************
** Meta object code from reading C++ file 'DocumentListPage.h'
**
** Created by: The Qt Meta Object Compiler version 67 (Qt 5.14.2)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include <memory>
#include "../../DocAI/src/pages/DocumentListPage.h"
#include <QtCore/qbytearray.h>
#include <QtCore/qmetatype.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'DocumentListPage.h' doesn't include <QObject>."
#elif Q_MOC_OUTPUT_REVISION != 67
#error "This file was generated using the moc from 5.14.2. It"
#error "cannot be used with the include files from this version of Qt."
#error "(The moc has changed too much.)"
#endif

QT_BEGIN_MOC_NAMESPACE
QT_WARNING_PUSH
QT_WARNING_DISABLE_DEPRECATED
struct qt_meta_stringdata_DocumentListPage_t {
    QByteArrayData data[13];
    char stringdata0[169];
};
#define QT_MOC_LITERAL(idx, ofs, len) \
    Q_STATIC_BYTE_ARRAY_DATA_HEADER_INITIALIZER_WITH_OFFSET(len, \
    qptrdiff(offsetof(qt_meta_stringdata_DocumentListPage_t, stringdata0) + ofs \
        - idx * sizeof(QByteArrayData)) \
    )
static const qt_meta_stringdata_DocumentListPage_t qt_meta_stringdata_DocumentListPage = {
    {
QT_MOC_LITERAL(0, 0, 16), // "DocumentListPage"
QT_MOC_LITERAL(1, 17, 14), // "navigateToChat"
QT_MOC_LITERAL(2, 32, 0), // ""
QT_MOC_LITERAL(3, 33, 5), // "docId"
QT_MOC_LITERAL(4, 39, 7), // "docName"
QT_MOC_LITERAL(5, 47, 16), // "documentsChanged"
QT_MOC_LITERAL(6, 64, 13), // "loadDocuments"
QT_MOC_LITERAL(7, 78, 15), // "uploadDocuments"
QT_MOC_LITERAL(8, 94, 14), // "deleteSelected"
QT_MOC_LITERAL(9, 109, 16), // "downloadSelected"
QT_MOC_LITERAL(10, 126, 18), // "onSelectionChanged"
QT_MOC_LITERAL(11, 145, 15), // "toggleSelectAll"
QT_MOC_LITERAL(12, 161, 7) // "checked"

    },
    "DocumentListPage\0navigateToChat\0\0docId\0"
    "docName\0documentsChanged\0loadDocuments\0"
    "uploadDocuments\0deleteSelected\0"
    "downloadSelected\0onSelectionChanged\0"
    "toggleSelectAll\0checked"
};
#undef QT_MOC_LITERAL

static const uint qt_meta_data_DocumentListPage[] = {

 // content:
       8,       // revision
       0,       // classname
       0,    0, // classinfo
       8,   14, // methods
       0,    0, // properties
       0,    0, // enums/sets
       0,    0, // constructors
       0,       // flags
       2,       // signalCount

 // signals: name, argc, parameters, tag, flags
       1,    2,   54,    2, 0x06 /* Public */,
       5,    0,   59,    2, 0x06 /* Public */,

 // slots: name, argc, parameters, tag, flags
       6,    0,   60,    2, 0x08 /* Private */,
       7,    0,   61,    2, 0x08 /* Private */,
       8,    0,   62,    2, 0x08 /* Private */,
       9,    0,   63,    2, 0x08 /* Private */,
      10,    0,   64,    2, 0x08 /* Private */,
      11,    1,   65,    2, 0x08 /* Private */,

 // signals: parameters
    QMetaType::Void, QMetaType::Int, QMetaType::QString,    3,    4,
    QMetaType::Void,

 // slots: parameters
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void, QMetaType::Bool,   12,

       0        // eod
};

void DocumentListPage::qt_static_metacall(QObject *_o, QMetaObject::Call _c, int _id, void **_a)
{
    if (_c == QMetaObject::InvokeMetaMethod) {
        auto *_t = static_cast<DocumentListPage *>(_o);
        Q_UNUSED(_t)
        switch (_id) {
        case 0: _t->navigateToChat((*reinterpret_cast< int(*)>(_a[1])),(*reinterpret_cast< const QString(*)>(_a[2]))); break;
        case 1: _t->documentsChanged(); break;
        case 2: _t->loadDocuments(); break;
        case 3: _t->uploadDocuments(); break;
        case 4: _t->deleteSelected(); break;
        case 5: _t->downloadSelected(); break;
        case 6: _t->onSelectionChanged(); break;
        case 7: _t->toggleSelectAll((*reinterpret_cast< bool(*)>(_a[1]))); break;
        default: ;
        }
    } else if (_c == QMetaObject::IndexOfMethod) {
        int *result = reinterpret_cast<int *>(_a[0]);
        {
            using _t = void (DocumentListPage::*)(int , const QString & );
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&DocumentListPage::navigateToChat)) {
                *result = 0;
                return;
            }
        }
        {
            using _t = void (DocumentListPage::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&DocumentListPage::documentsChanged)) {
                *result = 1;
                return;
            }
        }
    }
}

QT_INIT_METAOBJECT const QMetaObject DocumentListPage::staticMetaObject = { {
    QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
    qt_meta_stringdata_DocumentListPage.data,
    qt_meta_data_DocumentListPage,
    qt_static_metacall,
    nullptr,
    nullptr
} };


const QMetaObject *DocumentListPage::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void *DocumentListPage::qt_metacast(const char *_clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_DocumentListPage.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int DocumentListPage::qt_metacall(QMetaObject::Call _c, int _id, void **_a)
{
    _id = QWidget::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod) {
        if (_id < 8)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 8;
    } else if (_c == QMetaObject::RegisterMethodArgumentMetaType) {
        if (_id < 8)
            *reinterpret_cast<int*>(_a[0]) = -1;
        _id -= 8;
    }
    return _id;
}

// SIGNAL 0
void DocumentListPage::navigateToChat(int _t1, const QString & _t2)
{
    void *_a[] = { nullptr, const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t1))), const_cast<void*>(reinterpret_cast<const void*>(std::addressof(_t2))) };
    QMetaObject::activate(this, &staticMetaObject, 0, _a);
}

// SIGNAL 1
void DocumentListPage::documentsChanged()
{
    QMetaObject::activate(this, &staticMetaObject, 1, nullptr);
}
QT_WARNING_POP
QT_END_MOC_NAMESPACE
