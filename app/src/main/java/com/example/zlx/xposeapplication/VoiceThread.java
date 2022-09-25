package com.example.zlx.xposeapplication;

import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.elvishew.xlog.XLog;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class VoiceThread extends Thread {
    private BlockingQueue<List<String>> voice_queue = new LinkedBlockingQueue<>(1000);


    private int max_concurrent = 5;
    void set_max_concurrent(int max_concurrent) {
        this.max_concurrent = max_concurrent;
    }


    @Override
    public void run() {
        // TODO: 有异常时, 退出线程, 然后TouchThread会重新拉起此线程
        try{
            while (true) {// note catch外层代码, 此线程会退出, 不过退出后会被TouchThread重新拉起
                this.delete_history_uncomplete();
                if( this.voice_queue.isEmpty() ) {
                    XLog.d("voice queue is empty");
                    Thread.sleep(3000);
                } else {
                    int runing_count = this.get_uncomplete_row_count();
                    XLog.i("voice runing_count: %d, max_concurrent: %d", runing_count, this.max_concurrent);
                    if( runing_count < this.max_concurrent ) {
                        this.run_new_task(this.max_concurrent - runing_count);
                    }
                    Thread.sleep(3000);
                }
            }
        } catch (Exception e) {
            XLog.e("VoiceThread error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    private void run_new_task(int count) throws Exception{
        //note: take为阻塞读, 此外应保证能一直取出queue的内容, 否则当队列full的时候, 生产者线程put时一直阻塞!!
        for(int i = 0; i < count; i++) {
            List<String> msg = this.voice_queue.poll();
            if (msg != null) {
                String json_string = msg.get(0);
                XLog.d("real send voice. json_string: %s", json_string);  //f.getAbsoluteFile() 全路径的File对象
                // call
                JSONTokener jsonParser = new JSONTokener(json_string);
                JSONObject req_dict = (JSONObject) jsonParser.nextValue();
                req_dict.put("action", "invoke_send_voice");
                WechatHook.recv_queue.put(req_dict.toString());
            }
        }
    }


    private int get_uncomplete_row_count() throws Exception{
        int one_minute_ago = (int) System.currentTimeMillis() / 1000 -  60;

        String sql = String.format(Locale.US, "SELECT rowid, Status, MsgId, CreateTime, NetOffset, TotalLen FROM voiceinfo WHERE Status < 97 AND CreateTime > %d", one_minute_ago);
        //XLog.w("get uncomplete row, sql: %s", sql);

        Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, null);
        try {
            if (cursor == null) {
                XLog.w("get uncomplete voice row FROM voiceinfo rawQuery ret: null");
                return 0;
            }
            if (!cursor.moveToFirst()) {
                XLog.i("get uncomplete voice row empty");
                return 0;
            }

            return cursor.getCount();
        } catch (Exception e) {
            XLog.e("get uncomplete voice row error. stack:%s", Log.getStackTraceString(e));
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }


    void add_task(String json_string) throws Exception{
        this.voice_queue.put( Arrays.asList(json_string) );
    }


    private void delete_history_uncomplete() throws Exception{
        /**
         * DbOperation insert table: voiceinfo
         FileName: = 402149040717d25c875c65d100
         User: = weixin12345   发送目标
         MsgId: = 7862418102662959168
         NetOffset: = 9482
         TotalLen: = 9482               # 文件大小字节
         Status: = 99                    # 98 - amr文件不存在或没有message记录关联, 99 - 成功发送
         CreateTime: = 1491572980
         LastModifyTime: = 1491572987
         ClientId:＝ 402149040717d25c875c65d100
         VoiceLength: = 5610
         MsgLocalId: = 84
         Human: = wxid_w6f7i8zvvtbc12  自己
         */
        String sql;
        ArrayList<String> rowids = new ArrayList<>();
        ArrayList<String> msgids = new ArrayList<>();
        {
            int two_minute_ago = (int) System.currentTimeMillis() / 1000 - 120;
            sql = String.format(Locale.US, "SELECT rowid, MsgLocalId FROM voiceinfo WHERE Status < 97 AND CreateTime < %d", two_minute_ago);
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, null);
            try {
                if (cursor == null) {
                    XLog.w("SELECT voiceinfo rawQuery ret: null");
                    return;
                }
                if (!cursor.moveToFirst()) {
                    XLog.i("SELECT voiceinfo empty");
                    return;
                }
                while (cursor.isAfterLast() == false) {
                    String rowid = cursor.getString(cursor.getColumnIndex("rowid"));
                    String MsgLocalId = cursor.getString(cursor.getColumnIndex("MsgLocalId"));
                    rowids.add(rowid);
                    msgids.add(MsgLocalId);
                }
            } catch (Exception e) {
                XLog.e("SELECT voiceinfo error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        // 删除
        {
            sql = String.format(Locale.US, "DELETE FROM voiceinfo WHERE rowid in (%s)", TextUtils.join(",", rowids));
            WechatClass.EnMicroMsg.execSQL(sql);
            sql = String.format(Locale.US, "DELETE FROM message WHERE msgid in (%s)", TextUtils.join(",", msgids));
            WechatClass.EnMicroMsg.execSQL(sql);
        }
    }

}
