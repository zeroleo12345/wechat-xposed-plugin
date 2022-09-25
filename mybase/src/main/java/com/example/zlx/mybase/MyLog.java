package com.example.zlx.mybase;

import android.text.TextUtils;
import android.util.Log;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter2;
import com.elvishew.xlog.printer.file.backup.FileSizeBackupStrategy;

import java.io.File;

public class MyLog {
    static MyFileNameGenarator filenameGenarotor;

    public static void setPrefix(String tag, String prefix){
        filenameGenarotor.setPrefix( tag, prefix );
    }

    public static void setDefaultPrefix(String defaultPrefix){
        filenameGenarotor.setDefaultPrefix( defaultPrefix );
    }

    public static void init_xlog(String log_dir, String logfile_prefix, String tag, String log_level){
        // note 读配置, 初始化Log
        // 初始化Log.      https://github.com/elvishew/xLog
        // 日志级别:    VERBOSE = 2; DEBUG = 3; INFO = 4; WARN = 5; ERROR = 6;
        File xlog_dir = new File(log_dir);
        if ( !xlog_dir.exists() ) {
            Log.i(tag, "xlog mkdirs: ret:" + xlog_dir.mkdirs());
        }

        filenameGenarotor = new MyFileNameGenarator(logfile_prefix);
        Printer filePrinter = new FilePrinter2                      // 打印日志到文件的打印器
                .Builder(log_dir)                              // 指定保存日志文件的路径
                .fileNameGenerator(filenameGenarotor)        // 指定日志文件名生成器，默认为 ChangelessFileNameGenerator("log")
                .backupStrategy(new FileSizeBackupStrategy(100 * 1024 * 1024))  // 指定日志文件备份策略，默认为 FileSizeBackupStrategy(1024 * 1024)
                .logFlattener(new MyFlattener())                       // 指定日志平铺器，默认为 DefaultFlattener
                .build();

        LogConfiguration config = null;
        if (log_level.equalsIgnoreCase("DEBUG")) {
            config = new LogConfiguration.Builder()
                    .logLevel(LogLevel.DEBUG)     // 指定日志级别，默认为 LogLevel.ALL.  DEBUG级别即可不打印VERBOSE日志, 一般为心跳信息
                    .tag(tag)                // 指定 TAG，默认为 "X-LOG"
                    .build();
            Printer androidPrinter = new AndroidPrinter();             // 通过 android.util.Log 打印日志的打印器
            try{
                XLog.init(config, androidPrinter, filePrinter);
            }catch (IllegalStateException e){
            }
            Log.i(tag, "Log Level: Debug, TermPrint: Enable");
        } else {
            config = new LogConfiguration.Builder()
                    .logLevel(LogLevel.INFO)     // 指定日志级别，低于该级别的日志将不会被打印，默认为 LogLevel.ALL
                    .tag(tag)                // 指定 TAG，默认为 "X-LOG"
                    .build();
            try{
                XLog.init(config, filePrinter);
            }catch (IllegalStateException e){
            }
            Log.i(tag, "Log Level: Release, TermPrint: Disable");
        }
        XLog.i("XLog init success");
    }
}
