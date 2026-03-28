/****************************************************************************
** Meta object code from reading C++ file 'TopBar.h'
**
** Created by: The Qt Meta Object Compiler version 67 (Qt 5.14.2)
**
** WARNING! All changes made in this file will be lost!
*****************************************************************************/

#include <memory>
#include "../../DocAI/src/widgets/TopBar.h"
#include <QtCore/qbytearray.h>
#include <QtCore/qmetatype.h>
#if !defined(Q_MOC_OUTPUT_REVISION)
#error "The header file 'TopBar.h' doesn't include <QObject>."
#elif Q_MOC_OUTPUT_REVISION != 67
#error "This file was generated using the moc from 5.14.2. It"
#error "cannot be used with the include files from this version of Qt."
#error "(The moc has changed too much.)"
#endif

QT_BEGIN_MOC_NAMESPACE
QT_WARNING_PUSH
QT_WARNING_DISABLE_DEPRECATED
struct qt_meta_stringdata_TopBar_t {
    QByteArrayData data[7];
    char stringdata0[89];
};
#define QT_MOC_LITERAL(idx, ofs, len) \
    Q_STATIC_BYTE_ARRAY_DATA_HEADER_INITIALIZER_WITH_OFFSET(len, \
    qptrdiff(offsetof(qt_meta_stringdata_TopBar_t, stringdata0) + ofs \
        - idx * sizeof(QByteArrayData)) \
    )
static const qt_meta_stringdata_TopBar_t qt_meta_stringdata_TopBar = {
    {
QT_MOC_LITERAL(0, 0, 6), // "TopBar"
QT_MOC_LITERAL(1, 7, 13), // "logoutClicked"
QT_MOC_LITERAL(2, 21, 0), // ""
QT_MOC_LITERAL(3, 22, 21), // "changePasswordClicked"
QT_MOC_LITERAL(4, 44, 15), // "minimizeClicked"
QT_MOC_LITERAL(5, 60, 15), // "maximizeClicked"
QT_MOC_LITERAL(6, 76, 12) // "closeClicked"

    },
    "TopBar\0logoutClicked\0\0changePasswordClicked\0"
    "minimizeClicked\0maximizeClicked\0"
    "closeClicked"
};
#undef QT_MOC_LITERAL

static const uint qt_meta_data_TopBar[] = {

 // content:
       8,       // revision
       0,       // classname
       0,    0, // classinfo
       5,   14, // methods
       0,    0, // properties
       0,    0, // enums/sets
       0,    0, // constructors
       0,       // flags
       5,       // signalCount

 // signals: name, argc, parameters, tag, flags
       1,    0,   39,    2, 0x06 /* Public */,
       3,    0,   40,    2, 0x06 /* Public */,
       4,    0,   41,    2, 0x06 /* Public */,
       5,    0,   42,    2, 0x06 /* Public */,
       6,    0,   43,    2, 0x06 /* Public */,

 // signals: parameters
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,
    QMetaType::Void,

       0        // eod
};

void TopBar::qt_static_metacall(QObject *_o, QMetaObject::Call _c, int _id, void **_a)
{
    if (_c == QMetaObject::InvokeMetaMethod) {
        auto *_t = static_cast<TopBar *>(_o);
        Q_UNUSED(_t)
        switch (_id) {
        case 0: _t->logoutClicked(); break;
        case 1: _t->changePasswordClicked(); break;
        case 2: _t->minimizeClicked(); break;
        case 3: _t->maximizeClicked(); break;
        case 4: _t->closeClicked(); break;
        default: ;
        }
    } else if (_c == QMetaObject::IndexOfMethod) {
        int *result = reinterpret_cast<int *>(_a[0]);
        {
            using _t = void (TopBar::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&TopBar::logoutClicked)) {
                *result = 0;
                return;
            }
        }
        {
            using _t = void (TopBar::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&TopBar::changePasswordClicked)) {
                *result = 1;
                return;
            }
        }
        {
            using _t = void (TopBar::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&TopBar::minimizeClicked)) {
                *result = 2;
                return;
            }
        }
        {
            using _t = void (TopBar::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&TopBar::maximizeClicked)) {
                *result = 3;
                return;
            }
        }
        {
            using _t = void (TopBar::*)();
            if (*reinterpret_cast<_t *>(_a[1]) == static_cast<_t>(&TopBar::closeClicked)) {
                *result = 4;
                return;
            }
        }
    }
    Q_UNUSED(_a);
}

QT_INIT_METAOBJECT const QMetaObject TopBar::staticMetaObject = { {
    QMetaObject::SuperData::link<QWidget::staticMetaObject>(),
    qt_meta_stringdata_TopBar.data,
    qt_meta_data_TopBar,
    qt_static_metacall,
    nullptr,
    nullptr
} };


const QMetaObject *TopBar::metaObject() const
{
    return QObject::d_ptr->metaObject ? QObject::d_ptr->dynamicMetaObject() : &staticMetaObject;
}

void *TopBar::qt_metacast(const char *_clname)
{
    if (!_clname) return nullptr;
    if (!strcmp(_clname, qt_meta_stringdata_TopBar.stringdata0))
        return static_cast<void*>(this);
    return QWidget::qt_metacast(_clname);
}

int TopBar::qt_metacall(QMetaObject::Call _c, int _id, void **_a)
{
    _id = QWidget::qt_metacall(_c, _id, _a);
    if (_id < 0)
        return _id;
    if (_c == QMetaObject::InvokeMetaMethod) {
        if (_id < 5)
            qt_static_metacall(this, _c, _id, _a);
        _id -= 5;
    } else if (_c == QMetaObject::RegisterMethodArgumentMetaType) {
        if (_id < 5)
            *reinterpret_cast<int*>(_a[0]) = -1;
        _id -= 5;
    }
    return _id;
}

// SIGNAL 0
void TopBar::logoutClicked()
{
    QMetaObject::activate(this, &staticMetaObject, 0, nullptr);
}

// SIGNAL 1
void TopBar::changePasswordClicked()
{
    QMetaObject::activate(this, &staticMetaObject, 1, nullptr);
}

// SIGNAL 2
void TopBar::minimizeClicked()
{
    QMetaObject::activate(this, &staticMetaObject, 2, nullptr);
}

// SIGNAL 3
void TopBar::maximizeClicked()
{
    QMetaObject::activate(this, &staticMetaObject, 3, nullptr);
}

// SIGNAL 4
void TopBar::closeClicked()
{
    QMetaObject::activate(this, &staticMetaObject, 4, nullptr);
}
QT_WARNING_POP
QT_END_MOC_NAMESPACE
