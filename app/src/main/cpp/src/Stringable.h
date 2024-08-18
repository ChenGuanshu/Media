#pragma once

#include <string>

class Stringable {
public:
     virtual std::string toString() const = 0;
};
