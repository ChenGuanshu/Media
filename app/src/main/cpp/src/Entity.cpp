//#pragma

#include "string"
#include "Entity.h"
#include "Utils.h"
#include "iostream"
#include "sstream"

Entity::Entity(const std::string &_name) :
        name(_name),
        // 这里不能传递 _name，因为_name的生命周期只存在于构造函数，他的内存地址随后即是无效的
        str_pointer(&name),
        str_reference(name)
{
    num = 100;
    num_array[0] = 101;
    num_array[1] = 102;
    LOGD("init Entity %s", name.c_str());
}

Entity::~Entity()
{
    LOGD("release Entity %s", name.c_str());
}

std::string Entity::toString() const
{
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