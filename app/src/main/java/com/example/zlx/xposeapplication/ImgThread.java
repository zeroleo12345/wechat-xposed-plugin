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


public class ImgThread extends Thread {
    private BlockingQueue<List<String>> img_queue = new LinkedBlockingQueue<>(1000);


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
                if( this.img_queue.isEmpty() ) {
                    XLog.d("img queue is empty");
                    Thread.sleep(4000);
                } else {
                    int runing_count = this.get_uncomplete_row_count();
                    XLog.i("img runing_count: %d, max_concurrent: %d", runing_count, this.max_concurrent);
                    if( runing_count < this.max_concurrent ) {
                        this.run_new_task(this.max_concurrent - runing_count);
                    }
                    Thread.sleep(4000);
                }
            }
        } catch (Exception e) {
            XLog.e("ImgThread error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }


    private void run_new_task(int count) throws Exception{
        //note: take为阻塞读, 此外应保证能一直取出queue的内容, 否则当队列full的时候, 生产者线程put时一直阻塞!!
        for(int i = 0; i < count; i++) {
            List<String> msg = this.img_queue.poll();
            if (msg != null) {
                String json_string = msg.get(0);
                XLog.i("real send img. json_string: %s", json_string);  //f.getAbsoluteFile() 全路径的File对象
                // call
                JSONTokener jsonParser = new JSONTokener(json_string);
                JSONObject req_dict = (JSONObject) jsonParser.nextValue();
                req_dict.put("action", "invoke_send_img");
                WechatHook.recv_queue.put(req_dict.toString());
            }
        }
    }


    private int get_uncomplete_row_count() throws Exception{
        int one_minute_ago = (int) System.currentTimeMillis() / 1000 -  60;

        String sql = String.format(Locale.US, "SELECT id, iscomplete, msglocalid, createtime, offset, totalLen FROM ImgInfo2 WHERE iscomplete = 0 AND createtime > %d", one_minute_ago);
        //XLog.w("get uncomplete row, sql: %s", sql);

        Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, null);
        try {
            if (cursor == null) {
                XLog.w("get uncomplete img row FROM ImgInfo2 rawQuery ret: null");
                return 0;
            }
            if (!cursor.moveToFirst()) {
                XLog.i("get uncomplete img row empty");
                return 0;
            }

            return cursor.getCount();
        } catch (Exception e) {
            XLog.e("get uncomplete img row error. stack:%s", Log.getStackTraceString(e));
        } finally {
            if (cursor != null) cursor.close();
        }
        return 0;
    }


    void add_task(String json_string) throws Exception{
        this.img_queue.put( Arrays.asList(json_string) );
    }


    private void delete_history_uncomplete() throws Exception{
        /**
         * DbOperation insert table: ImgInfo2
         *     ImgInfo2 contentvalues:
         *     compressType=0
         *     createtime=1537689187
         *     thumbImgPath=THUMBNAIL_DIRPATH://th_ed9f00131f3eeaedabc086d0b1841ede
         *     id=34
         *     iscomplete=0
         *     origImgMD5=e4ef1127f9bdbb6607106cd6dc74110d
         *     forwardType=1
         *     totalLen=18646
         *     reserved2=0
         *     msglocalid=1138
         *     bigImgPath=592c01a79d04e59c74ecce636794b6c2.jpg
         */
        String sql;
        ArrayList<String> rowids = new ArrayList<>();
        ArrayList<String> msgids = new ArrayList<>();
        {
            int two_minute_ago = (int) System.currentTimeMillis() / 1000 - 120;
            sql = String.format(Locale.US, "SELECT rowid, msglocalid FROM ImgInfo2 WHERE iscomplete = 0 AND createtime < %d", two_minute_ago);
            Cursor cursor = WechatClass.EnMicroMsg.rawQuery(sql, null);
            try {
                if (cursor == null) {
                    XLog.w("SELECT ImgInfo2 rawQuery ret: null");
                    return;
                }
                if (!cursor.moveToFirst()) {
                    XLog.i("SELECT ImgInfo2 empty");
                    return;
                }
                while (cursor.isAfterLast() == false) {
                    String rowid = cursor.getString(cursor.getColumnIndex("rowid"));
                    String msglocalid = cursor.getString(cursor.getColumnIndex("msglocalid"));
                    rowids.add(rowid);
                    msgids.add(msglocalid);
                }
            } catch (Exception e) {
                XLog.e("SELECT ImgInfo2 error. stack:%s", Log.getStackTraceString(e));
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        // 删除
        {
            sql = String.format(Locale.US, "DELETE FROM ImgInfo2 WHERE rowid in (%s)", TextUtils.join(",", rowids));
            XLog.d("sql: %s", sql);
            WechatClass.EnMicroMsg.execSQL(sql);

            sql = String.format(Locale.US, "DELETE FROM message WHERE msgid in (%s)", TextUtils.join(",", msgids));
            XLog.d("sql: %s", sql);
            WechatClass.EnMicroMsg.execSQL(sql);
        }
    }

}
