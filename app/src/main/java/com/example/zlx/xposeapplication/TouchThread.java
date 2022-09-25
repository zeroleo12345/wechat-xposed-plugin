package com.example.zlx.xposeapplication;

import android.os.Environment;
import android.text.TextUtils;
import android.widget.Toast;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.example.zlx.mybase.MyLog;
import com.example.zlx.mybase.MyPath;
import com.example.zlx.mynative.AuthArg;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** 更新pid文件线程 */
class TouchThread extends Thread {
    static BlockingQueue<List<String>> sftp_queue = new LinkedBlockingQueue<>(1000);

    // 进程退出使用
    public SftpThread _thread_sftp = null;
    public VerifyThread _thread_verify;
    public Thread _thread_scan = null;

    static SendThread thread_send;
    static WechatAction thread_action;

    private Logger log = null;
    private String gateway = null;

    TouchThread (String gateway) {
        this.gateway = gateway;
        this.log = XLog.tag(BuildConfig.TAG2).build();
        MyLog.setPrefix( BuildConfig.TAG2, "scan" );
    }

    /** 认证进程 */
    void new_verify_thread() {
        if(_thread_verify == null || !_thread_verify.isAlive()) {
            _thread_verify = new VerifyThread(this.log);
            _thread_verify.start();
        }
    }

    /** 行动进程 */
    void new_action_thread() {
        if(TouchThread.thread_action == null || !TouchThread.thread_action.isAlive()) {
            TouchThread.thread_action.start();
        }
    }

    private void newOtherThread() throws Exception{
        if( BuildConfig.cut ){
            this.new_verify_thread();
        }
        //

        try{
            if(WechatHook.is_afterLogin){
                if(TouchThread.thread_send == null || !TouchThread.thread_send.isAlive()) {
                    String host = UserConfig.getString("rabbitmq", "ip");
                    int rabbitmq_port = UserConfig.getInt("rabbitmq", "port");
                    int connect_timeout = UserConfig.getInt("rabbitmq", "connect_timeout");  //毫秒
                    String rabbitmq_user = UserConfig.getString("rabbitmq", "user");
                    String rabbitmq_password = UserConfig.getString("rabbitmq", "password");
                    String virtual_host = UserConfig.getString("rabbitmq", "virtual_host");
                    TouchThread.thread_send = new SendThread(host, rabbitmq_port, virtual_host, rabbitmq_user, rabbitmq_password, connect_timeout, WechatHook.WxSend, WechatHook.WxRecv);
                    TouchThread.thread_send.start();
                }
            }
        }catch (Exception e){
            log.e("new OtherThread error. stack:%s", android.util.Log.getStackTraceString(e));
        }

        //
        if(_thread_sftp == null || !_thread_sftp.isAlive()) {
            int sftp_enable = UserConfig.getInt("sftp", "enable");
            log.i("sftp_enable: %d", sftp_enable);
            if( sftp_enable != 0) {
                log.i("new sftp thread");
                String sftp_host = UserConfig.getString("sftp", "ip");
                int port = UserConfig.getInt("sftp", "port");
                int connect_timeout = 2000;
                String sftp_user = UserConfig.getString("sftp", "user");
                String sftp_password = UserConfig.getString("sftp", "password");
                //if (BuildConfig.DEBUG) {// note. 本地测试使用, 使用 cygwin 下的 sshd, 并修改port为 2222}
                _thread_sftp = new SftpThread(sftp_host, port, sftp_user, sftp_password, connect_timeout, sftp_enable, this.log);
                _thread_sftp.start();
            }
        }

        //
        if(_thread_scan == null || !_thread_scan.isAlive()) {
            log.i("new scan image/attach/log thread");
            _thread_scan = new ScanThread(this.log);
            _thread_scan.start();
        }

        //log.i("cpuinfo:%s", _readCpuInfo());
        // note: 当其他线程都准备好的时候, 才启动action线程
        this.new_action_thread();
    }


    @Override
    public void run() {
        //long thread_id = Thread.currentThread().getId();

        File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "/myplugin.pid");
        if(!f.exists()){
            try {
                /** 创建父目录 */
                new File( f.getParent() ).mkdirs();
                /** 创建文件 */
                f.createNewFile();
            } catch (IOException e){
                log.e("TouchThread IOException. stack:%s", android.util.Log.getStackTraceString(e));
                return;
            }
        }

        while (true) {// note catch内层所有代码, 保证此线程不会退出, 用于维系内部其他线程
            /** f.setLastModified((System.currentTimeMillis() / 1000) * 1000); 在安卓下不能使用! */
            try {
                this.newOtherThread();

                //log.d("before lastModified:%d", f.lastModified());
                RandomAccessFile raf = new RandomAccessFile(f, "rw");
                long length = raf.length();
                raf.setLength(length + 1);
                raf.setLength(length);
                raf.close();
                //log.d("after lastModified:%d", f.lastModified());
            } catch (Throwable e) {
                log.e("TouchThread error. stack:%s", android.util.Log.getStackTraceString(e));
            }
            try {
                /** 睡眠 */
                Thread.sleep(10000);     //单位: 毫秒.
            } catch (Throwable e) {}
        }
    }

}   /** TouchThread 类结束 */
