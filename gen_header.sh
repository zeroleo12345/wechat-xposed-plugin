#!/usr/bin/env sh

set -o verbose

cd  /home/zlx/android-study/XposeApplication/app/src/main;
javah -d jni -classpath "E:\cygwin64\home\zlx\android-study\XposeApplication\app\build\intermediates\classes\debug"   com.example.zlx.mynative.JNIUtils
