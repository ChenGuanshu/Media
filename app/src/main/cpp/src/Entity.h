#pragma

#include "Stringable.h"

class Entity : public Stringable {
private:
    std::string name;
    std::string *str_pointer;
    std::string &str_reference;
    int num;
    int num_array[2];

public:
    Entity(std::string &name);
    ~Entity();
    std::string toString() const override;
};