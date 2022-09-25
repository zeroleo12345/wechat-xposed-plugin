#include <stdio.h>
#include <stdlib.h>
#include "Base64.h"

//const char base[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
const char urlsafe_base[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_="; // 即在编/解码过程中使用'-'和'_'替代标准Base64字符集中的'+'和'/', 生成可以在URL中使用的Base64格式文本

char *b64encode(const unsigned char *data, const int data_len) {
    int prepare = 0;
    int ret_len;
    int temp = 0;
    char *ret = NULL;
    char *f = NULL;
    int tmp = 0;
    char changed[4];
    int i = 0;
    ret_len = data_len / 3;
    temp = data_len % 3;
    if (temp > 0) {
        ret_len += 1;
    }
    ret_len = ret_len * 4 + 1;
//    ret = (char *) malloc(ret_len);
    ret = new char[ret_len];

    if (ret == NULL) {
        //  LOGD("No enough memory.\n");
        exit(0);
    }
    memset(ret, 0, ret_len);
    f = ret;
    while (tmp < data_len) {
        temp = 0;
        prepare = 0;
        memset(changed, '\0', 4);
        while (temp < 3) {
            //printf("tmp = %d\n", tmp);
            if (tmp >= data_len) {
                break;
            }
            prepare = ((prepare << 8) | (data[tmp] & 0xFF));
            tmp++;
            temp++;
        }
        prepare = (prepare << ((3 - temp) * 8));
        //printf("before for : temp = %d, prepare = %d\n", temp, prepare);
        for (i = 0; i < 4; i++) {
            if (temp < i) {
                changed[i] = 0x40;
            }
            else {
                changed[i] = (prepare >> ((3 - i) * 6)) & 0x3F;
            }
            *f = urlsafe_base[changed[i]];
            //printf("%.2X", changed[i]);
            f++;
        }
    }
    *f = '\0';

    return ret;

}

/* */
static char find_pos(char ch) {
    char *ptr = (char *) strrchr(urlsafe_base, ch);//the last position (the only) in urlsafe_base[]
    return (ptr - urlsafe_base);
}

/* */
char *b64decode(const unsigned char *data, int &data_len) {
    int b64str_len = strlen((const char*)data);
    int ret_len = (b64str_len / 4) * 3;
    int equal_count = 0;
    char *ret = NULL;
    char *f = NULL;
    int tmp = 0;
    int temp = 0;
    char need[3];
    int prepare = 0;
    int i = 0;
    if (*(data + b64str_len - 1) == '=') {
        equal_count += 1;
    }
    if (*(data + b64str_len - 2) == '=') {
        equal_count += 1;
    }
    if (*(data + b64str_len - 3) == '=') {//seems impossible
        equal_count += 1;
    }
    switch (equal_count) {
        case 0:
            ret_len += 4;//3 + 1 [1 for NULL]
            break;
        case 1:
            ret_len += 4;//Ceil((6*3)/8)+1
            break;
        case 2:
            ret_len += 3;//Ceil((6*2)/8)+1
            break;
        case 3:
            ret_len += 2;//Ceil((6*1)/8)+1
            break;
    }
//    ret = (char *) malloc(ret_len);
    ret = new char[ret_len];
    if (ret == NULL) {
        exit(0);
    }
    memset(ret, 0, ret_len);
    f = ret;
    while (tmp < (b64str_len - equal_count)) {
        temp = 0;
        prepare = 0;
        memset(need, 0, 4);
        while (temp < 4) {
            if (tmp >= (b64str_len - equal_count)) {
                break;
            }
            prepare = (prepare << 6) | (find_pos(data[tmp]));
            temp++;
            tmp++;
        }
        prepare = prepare << ((4 - temp) * 6);
        for (i = 0; i < 3; i++) {
            if (i == temp) {
                break;
            }
            *f = (char) ((prepare >> ((2 - i) * 8)) & 0xFF);
            f++;
        }
    }
    *f = '\0';
    data_len = f-ret;
    //printf("real len: %d\n", data_len);
    return ret;
}