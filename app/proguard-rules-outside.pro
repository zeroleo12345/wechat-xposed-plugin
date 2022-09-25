# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization is turned off by default. Dex does not like code run
# through the ProGuard optimize and preverify steps (and performs some
# of these optimizations on its own).
#-dontoptimize

# Note that if you want to enable optimization, you cannot just
# include optimization flags in your own project configuration file;
# instead you will need to point to the
# "proguard-android-optimize.txt" file instead of this one from your
# project.properties file.

-keepattributes *Annotation*
-keep public class com.google.vending.licensing.ILicensingService
-keep public class com.android.vending.licensing.ILicensingService

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}

# keep setters in Views so that animations can still work.
# see http://proguard.sourceforge.net/manual/examples.html#beans
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator CREATOR;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

# Understand the @Keep support annotation.
-keep class android.support.annotation.Keep

-keep @android.support.annotation.Keep class * {*;}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <methods>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <fields>;
}

-keepclasseswithmembers class * {
    @android.support.annotation.Keep <init>(...);
}

###########################################################################
-dontpreverify      # 混淆时是否做预校验
#-dontoptimize       # 不优化, note. 不开启的话, 可能会出现Proguard BUG: https://stackoverflow.com/questions/9282011/proguard-illegalargumentexception-stacks-have-different-current-sizes
-optimizationpasses 5   # 指定执行几次优化，默认情况下，只执行一次优化     note: 参考: http://www.jianshu.com/p/60e82aafcfd0
#-dontshrink         # 不压缩指定的文件

-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*  # 混淆时所采用的算法

# note 保持入口类不混淆
-keep public class com.example.zlx.xposeapplication.Main { *; }
-keep public class com.example.zlx.mynative.AuthArg { *; }
-keep public class com.example.zlx.mynative.JNIUtils { *; }
-keep public class com.example.zlx.xposeapplication.MySync { *; }
# note: 因为native里面通过class:method调用MyAES和MyRandom内的函数, 所以不能混淆下面MyAES类, MyRandom类
-keep public class com.example.zlx.mybase.MyAES { <methods>; }
-keep public class com.example.zlx.mybase.MyRandom { <methods>; }
-keep class com.example.zlx.xposeapplication.WechatAction {
    <methods>;
}
-keep class com.example.zlx.xposeapplication.WechatHook {
    public void hookInitMtimerObject(java.lang.ClassLoader);
    public void hookInitVoiceInfoObject(java.lang.ClassLoader);
    public void hookInitVideoInfoObject(java.lang.ClassLoader);
    public void hookHandleAuthResponse(java.lang.ClassLoader);
    public void hookManualAuthResponse(java.lang.ClassLoader);
    public void hookWebwxObject(java.lang.ClassLoader);
    public void hookAppPanel(java.lang.ClassLoader);
    public void hookMenuOnClick(java.lang.ClassLoader);
}
-keep class javax.** { *; }
-dontwarn javax.**
-keep class java.** { *; }
-dontwarn java.**
-keep class io.** { *; }
-dontwarn io.**
-dontwarn net.**
-keep class net.** { *; }
-keep class org.** { *; }
-dontwarn org.**
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-keep class com.codahale.** { *; }
-dontwarn com.codahale.**
#-dontwarn org.apache.commons.pool2.**
#-keep class org.apache.commons.pool2.** { *; }

#-keep interface net.sf.** { *; }
#-keep interface org.apache.** { *; }

# note: 保留文件名, 行号
#-keepattributes *Annotation*,SourceFile,LineNumberTable

# rename the source files to something meaningless, but it must be retained
-renamesourcefileattribute ''
# note: 指定混淆替换字符字典
#-obfuscationdictionary proguard-dict.txt
#-classobfuscationdictionary proguard-dict.txt
#-packageobfuscationdictionary proguard-dict.txt

# note: 关闭日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}
-assumenosideeffects class com.elvishew.xlog.XLog {
     public static void v(...);
#     public static void i(...);
     public static void w(...);
     public static void d(...);
#     public static void e(...);
}
-assumenosideeffects class com.elvishew.xlog.Logger {
     public static void v(...);
     public static void i(...);
     public static void w(...);
     public static void d(...);
     public static void e(...);
}