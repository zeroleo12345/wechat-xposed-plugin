package com.example.zlx.mybase;

import android.util.Log;

//import com.elvishew.xlog.printer.file.naming.FileNameGenerator;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator2;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;

public class MyFileNameGenarator implements FileNameGenerator2{
    private HashMap<String, String> tag2prefix = new HashMap<>();
    private String defaultPrefix = null;

    private ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMdd", Locale.US);
        }
    };

    public MyFileNameGenarator(String defaultPrefix) {
        this.defaultPrefix = defaultPrefix;
    }

    /** 设置文件头 */
    public void setDefaultPrefix(String defaultPrefix) {
        this.defaultPrefix = defaultPrefix;
    }

    /** 设置文件头 */
    public void setPrefix(String tag, String prefix) {
        this.tag2prefix.put( tag, prefix );
    }
    /**
     * Whether the generated file name will change or not.
     * @return 假如文件名会变化, 返回true
     */
    @Override
    public boolean isFileNameChangeable(){
        return true;
    }

    /**
     * Generate file name for specified log level and timestamp.
     *
     * @param logLevel  the level of the log
     * @param timestamp the timestamp when the logging happen
     * @return the generated file name
     * 猜测每次打印的时候, 自动调用检测文件名是否有变化
     */
    @Override
    public String generateFileName(int logLevel, String tag, long timestamp){
        String _prefix = this.tag2prefix.get(tag);
        if( _prefix != null ){
            int pid = android.os.Process.myPid();
            String yyyymmdd = formatter.get().format(new java.util.Date(timestamp));
            return String.format(Locale.US, "%s_%s_%d.log", _prefix, yyyymmdd, pid);
        }else {
            int pid = android.os.Process.myPid();
            String yyyymmdd = formatter.get().format(new java.util.Date(timestamp));
            return String.format(Locale.US, "%s_%s_%d.log", this.defaultPrefix, yyyymmdd, pid);
        }
    }
}