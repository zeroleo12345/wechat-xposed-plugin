package com.example.zlx.mybase;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.flattener.Flattener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by Administrator on 2017/5/27 0027.
 */

public class MyFlattener implements Flattener {

    private ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        }
    };

    @Override
    public CharSequence flatten(int logLevel, String tag, String message) {
        return String.format("[%s] %s %s %s",
                LogLevel.getShortLevelName(logLevel),
                formatter.get().format(new Date()),
                tag,
                message
                );
    }
}