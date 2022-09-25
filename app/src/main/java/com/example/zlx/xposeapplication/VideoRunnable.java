package com.example.zlx.xposeapplication;

import com.elvishew.xlog.XLog;
import static de.robv.android.xposed.XposedHelpers.callMethod;

public class VideoRunnable implements Runnable{
    VideoRunnable(){}
    @Override
    public void run() {
        try {
            /** 向上搜索: "sceneUp should null"
             */
            callMethod(WechatClass.videoinfo2_handler, "a", WechatClass.videoinfo2_handler);
        } catch (Exception e) {
            XLog.e("VideoRunnable error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }
}