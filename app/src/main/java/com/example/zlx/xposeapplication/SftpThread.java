package com.example.zlx.xposeapplication;

import android.text.TextUtils;

import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.example.zlx.mybase.MyPath;
import com.example.zlx.mybase.SFTPUtils;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;

import java.io.File;
import java.util.Arrays;
import java.util.List;

class SftpThread extends Thread {
    // 服务器IP, 端口, 用户, 密码
    private String m_ip;
    private int m_port;
    private String m_user;
    private String m_password;
    private int  connect_timeout = 2000;
    private int  enable = 1;

    private Logger log;
    private SFTPUtils sftp = null;
    SftpThread(String ip, int port, String user, String password, int connect_timeout, int enable, Logger log) throws Exception {
        this.m_ip = ip;
        this.m_port = port;
        this.m_user = user;
        this.m_password = password;
        this.connect_timeout = connect_timeout;
        this.enable = enable;

        this.log = log;
        this.connect_sftp();
    }


    void connect_sftp() throws Exception {
        try {
            this.sftp = new SFTPUtils(this.m_ip, this.m_port, this.m_user, this.m_password, this.connect_timeout);
            log.w("ready to connect sftp, ip: %s, port: %s, user: %s, password: %s, timeout: %d",
                    this.m_ip, this.m_port, this.m_user, this.m_password, this.connect_timeout);
            this.sftp.connect();
            log.i("SftpThread connect success");
        }catch (Exception e){
            log.e("connect sftp error. ip:%s, port:%d, user:%s, password:%s", m_ip, m_port, m_user, m_password);
            throw e;
        }
    }


    void disconnect_sftp(){
        if(this.sftp != null){
            this.sftp.disconnect();
            this.sftp = null;
        }
    }


    @Override
    public void run() {
        // TODO: 有异常时, 退出线程, 然后TouchThread会重新拉起此线程
        try{
            if( this.enable == 0 ) {
                return;
            }
            List<String> msg;
            String localDir, localFilename, remoteDir, remoteFilename, put_or_get, auto_delete;
            while (true) {// note catch外层代码, 此线程会退出, 不过退出后会被TouchThread重新拉起
                /** take为阻塞读, 此外应保证能一直取出queue的内容, 否则当队列full的时候, 生产者线程put时一直阻塞!! */
                msg = TouchThread.sftp_queue.take();
                localDir = msg.get(0);
                localFilename = msg.get(1);
                remoteDir = msg.get(2);
                remoteFilename = msg.get(3);
                put_or_get = msg.get(4);
                auto_delete = msg.get(5);
                switch(put_or_get){
                    case "put":{
                        File f = new File( localDir, localFilename );
                        if( !f.exists() ){
                            log.w("not exists, filepath:%s", f.getAbsolutePath());
                            continue;
                        }
                        if(f.length() == 0) {
                            log.w("delete 0 length file:%s", f.getAbsolutePath());
                            if( auto_delete.equals("1") ) f.delete();
                            continue;
                        }

                        log.i("uploadFile. localDir:%s, remoteDir:%s, localFilename:%s, remoteFilename:%s", localDir, remoteDir, localFilename, remoteFilename);  //f.getAbsoluteFile() 全路径的File对象
                        if( TextUtils.equals(BuildConfig.FLAVOR, "taobaoke") ){
                            if( auto_delete.equals("1") ) f.delete();
                        }else{
                            sftp.uploadFile(localDir, localFilename, remoteDir, remoteFilename);
                            if( auto_delete.equals("1") ) f.delete();
                        }
                    }break;
                    case "get":{
                        sftp.downloadFile(remoteDir, remoteFilename, localDir, localFilename);
                        String basename = FilenameUtils.getBaseName(localFilename);
                        JSONObject res_dict = new JSONObject();
                        res_dict.put("msgtype", "sftp_geted");
                        res_dict.put("filepath", MyPath.join(localDir, localFilename));
                        res_dict.put("session_id", basename);
                        XLog.i("rpush msgtype: sftp_geted");
                        WechatHook.rpush_queue.put( Arrays.asList("", WechatHook.WxRecv, res_dict.toString(), "0") );
                        XLog.d("res dict: %s", res_dict.toString());
                    }break;
                }
            }
        } catch (Exception e) {
            log.e("SftpThread error. stack:%s", android.util.Log.getStackTraceString(e));
        } finally{
            this.disconnect_sftp();
        }
    }
}   /** SftpThread */