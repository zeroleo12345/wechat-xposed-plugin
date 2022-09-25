//
// Created by zlx on 2017/12/26.
//

#ifndef XPOSED_6510_BASE64_H
#define XPOSED_6510_BASE64_H

//#include <string>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>

extern char *b64encode(const unsigned char *, int);
extern char *b64decode(const unsigned char *, int&);

#endif //XPOSED_6510_BASE64_H
