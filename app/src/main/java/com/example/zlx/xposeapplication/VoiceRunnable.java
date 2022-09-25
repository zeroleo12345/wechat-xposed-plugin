package com.example.zlx.xposeapplication;

import com.elvishew.xlog.XLog;

import static de.robv.android.xposed.XposedHelpers.callMethod;

public class VoiceRunnable implements Runnable{
    VoiceRunnable(){}
    @Override
    public void run() {
        try {
            /** 搜索: "info.getLastModifyTime()  "        667
             * com/tencent/mm/c/b/i       =>      com/tencent/mm/e/b/i           // note 该类是: MicroMsg.VoiceStorage
             * .method public final pl()V     =>      .method static synthetic h(Lcom/tencent/mm/e/b/i;)V
             */
            callMethod(WechatClass.voiceinfo_handler, "h", WechatClass.voiceinfo_handler);
        } catch (Exception e) {
            XLog.e("VoiceRunnable error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }
}