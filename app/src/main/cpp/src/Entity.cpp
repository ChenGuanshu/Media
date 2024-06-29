#pragma

#include "string"
#include "Entity.h"
#include "Utils.h"
#include "iostream"
#include "sstream"

Entity::Entity(std::string &name) :
        name(name),
        str_reference(name),
        str_pointer(&name) {

    num = 100;
    num_array[0] = 101;
    num_array[1] = 102;
    LOGD("init Entity");
}

Entity::~Entity() {
    LOGD("release Entity");
}

std::string Entity::toString() const {
    std::ostringstream str;
    str << " [name 1:" << name << "]";
    str << " [name 2:" << str_reference << "]";
    str << " [name 3:" << *str_pointer << "]";
    str << " [num:" << num << "]";
    str << " [num_array:";
    for (int i: num_array) {
        str << i << " ";
    }
    str << "]\n";
    return "Entity:" + str.str();
}