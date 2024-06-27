#pragma

#include "Stringable.h"

class Entity : public Stringable {
public:
    std::string toString() const override;
};