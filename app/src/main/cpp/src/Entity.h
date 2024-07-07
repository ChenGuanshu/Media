#pragma

#include "Stringable.h"

class Entity : public Stringable
{
private:
    const std::string name;

    const std::string *str_pointer;

    const std::string &str_reference;

    int num;

    int num_array[2];

public:
    Entity(const std::string &_name);

    ~Entity();

    std::string toString() const override;
};