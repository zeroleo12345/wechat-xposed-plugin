package com.example.zlx.xposeapplication;

import com.elvishew.xlog.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;

class ScanThread extends Thread {
    // 绑定
    static boolean redoSftpLog = false;
    private boolean _term = false;
    private Logger log;

    ScanThread(Logger log) {
        this.log = log;
    }

    void work(){
        scanLog();
        scanDownload();
        scanImage2();
        scanVoice2();
        scanVideo();
    }

    @Override
    public void run() {
        try{
            while(true) {// note catch外层代码, 此线程会退出, 不过退出后会被TouchThread重新拉起
                long sleep_millisecond = (5 * 60 * 1000);   // 休眠5分钟再检测. = 5 * 60 * 1000
                work();
                /** 睡眠 */
                log.i("ScanThread sleep 5 minute");
                Thread.sleep(sleep_millisecond);     //单位: 毫秒.
            }
        } catch (Exception e) {
            log.e("ScanThread error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }

    private long lastScanLogTime = 0;
    public void scanLog() {
        /** note 因为文件日志一直在写, 时间戳一直在更新!! 距离上次sftp超过2.5小时, 把所有文件发送到queue,
         *  1) 非当前进程的日志文件, auto_delete = 1
         *  2) 当前进程的日志文件, auto_delete = 0
         * monitor_20171013_1554.log
         * service_20171013_3773.log
         * service_wxid_w6f7i8zvvtbc12_20171013_3773.log
         */
        try {
            long sleep_millisecond = (long) (2.5 * 60 * 60 * 1000);   // 休眠2.5小时再检测. = 2.5 * 60 * 60 * 1000, 避免跨天问题
            String filename, local_dir;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd", Locale.US);
            File log_dir = new File(WechatHook.xLogDir);
            if( !log_dir.exists() ){
                log.e("log dir not exist, xLog Dir:%s", log_dir.getAbsolutePath());
                return;
            }
            long now = System.currentTimeMillis();
            if( now - lastScanLogTime <  sleep_millisecond){
                log.i("scanLog return beacuse < 2.5 hour, now:%d, lastScanLogTime:%d", now, lastScanLogTime);
                return;
            }
            for (File f : log_dir.listFiles()) {   //File[] list = new File(xLogDir).listFiles();
                filename = f.getName();
                if (f.isDirectory()) {
                    log.e("scanLog ignore dir:%s", filename);
                    continue;
                }
                local_dir = f.getParent();
                String yyyymmdd = formatter.format(new java.util.Date());     // 昨天: System.currentTimeMillis() - 1 * 24 * 60 * 60 * 1000
                int pid = android.os.Process.myPid();
                String regex = String.format( Locale.US, ".*_%s_%d\\.log$", yyyymmdd, pid );
                if ( filename.matches(regex) ) {
                    /** 当前进程的日志文件 delete = 0 */
                    WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteLogDir, filename, "put", "0" ) );
                    log.i("put log in sftp_queue, auto_delete:0, file:%s", filename);
                }else{
                    /** 不是今天的, 或不是当前进程号的日志文件, delete = 1 */
                    WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteLogDir, filename, "put", "1" ) );
                    log.i("put log in sftp_queue, auto_delete:1, file:%s", filename);
                }
            }
            lastScanLogTime = now;
        } catch (Exception e) {
            log.e("scanLog run error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** scanLog 结束 */

    public void scanDownload() {
        /** 扫描出超时5分钟, 非"_tmp"结尾的文件, 然后发送到queue */
        try{
            long sleep_millisecond = (5 * 60 * 1000);
            String filename, local_dir;
            File attach_dir = new File(WechatHook.localAttachDir); //f.getAbsoluteFile() 全路径的File对象
            if( !attach_dir.exists() ){
                log.e("attach dir not exist, localDownloadDir:%s", attach_dir.getAbsolutePath());
                return;
            }
            for (File f : attach_dir.listFiles()) {
                filename = f.getName();
                if (f.isDirectory()) {
                    log.i("scanDownload ignore dir:%s", filename);
                    continue;
                }
                if (filename.endsWith("_tmp")) {
                    log.i("scanDownload ignore tmp file:%s", filename);
                    continue;
                }
                /** 判断文件是否超时 */
                if( System.currentTimeMillis() - f.lastModified() <  sleep_millisecond){
                    log.i("scanDownload file lastModified < 5 minute, file:%s", filename);
                    continue;
                }
                local_dir = f.getParent();
                WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteAttachDir, filename, "put", "1" ) );
                log.i("put attach in sftp_queue, file:%s", filename);
            }
        } catch (Exception e) {
            log.e("scanDownload error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** scanDownload 结束 */

    public void scanImage2() {
        /** 扫描出超时5分钟image2目录下的文件
         * 1) 文件名是图片后缀, 发送到queue
         * 2) 文件名不是图片后缀, 删除
         *  */
        try{
            //String picture_path = WechatClass.getBigImgFullPath("1fcd2d2b00e0e3fbf89e324eb0ea291a.jpg");   // note 返回: /storage/emulated/0/tencent/MicroMsg/dfff3dad3708ac623be6a30eb3fe5ba6/image2/1f/cd/1fcd2d2b00e0e3fbf89e324eb0ea291a.jpg
            long sleep_millisecond = (5 * 60 * 1000);
            File image2_dir = new File( WechatClass.getPersonalDir("image2") );
            if( !image2_dir.exists() ) {
                log.e("image2 dir not exists, dir:%s", image2_dir.getAbsolutePath());
                return;
            }
            log.i("match image2 dir:%s", image2_dir.getAbsolutePath());
            /** 遍历image2子目录下的jpg, png文件 */
            String[] extensions = new String[] { "jpg", "png" };
            List<File> files = (List<File>) FileUtils.listFiles(image2_dir, extensions, true);
            String filename, local_dir, remote_dir;
            for (File f : files) {
                filename = f.getName();
                if(f.length() == 0) {
                    log.d("scanImage2 delete 0 length file:%s", filename);
                    f.delete();
                    continue;
                }
                /** 判断文件是否超时 */
                if( System.currentTimeMillis() - f.lastModified() <  sleep_millisecond){
                    log.i("scanImage2 file lastModified < 5 minute, file:%s", filename);
                    continue;
                }
                local_dir = f.getParent();
                remote_dir = local_dir.substring(local_dir.indexOf("/MicroMsg"));   // 截断到以 "/MicroMsg" 开始
                WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteImageDir, filename, "put", "1" ) );
                log.i("put image in sftp_queue, local_dir:%s, filename:%s", local_dir, filename);
            }
        } catch (Exception e) {
            log.e("scanImage2 error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** scanImage2 结束 */

    public void scanVideo() {
        /** 扫描出超时5分钟, 非"_tmp"结尾的文件, 然后发送到queue */
        try{
            long sleep_millisecond = (5 * 60 * 1000);
            String filename, local_dir;
            File video_dir = new File( WechatClass.getPersonalDir("video") );
            if( !video_dir.exists() ){
                log.e("video dir not exist, localVideoDir:%s", video_dir.getAbsolutePath());
                return;
            }
            for (File f : video_dir.listFiles()) {
                filename = f.getName();
                if (f.isDirectory()) {
                    log.i("scanVideo ignore dir:%s", filename);
                    continue;
                }
                if (filename.endsWith("_tmp")) {
                    log.i("scanVideo ignore tmp file:%s", filename);
                    continue;
                }
                /** 判断文件是否超时 */
                if( System.currentTimeMillis() - f.lastModified() <  sleep_millisecond){
                    log.i("scanVideo file lastModified < 5 minute, file:%s", filename);
                    continue;
                }
                local_dir = f.getParent();
                WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteVideoDir, filename, "put", "1" ) );
                log.i("put video in sftp_queue, file:%s", filename);
            }
        } catch (Exception e) {
            log.e("scanVideo error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** scanVideo 结束 */

    public void scanVoice2() {
        /** 扫描出超时5分钟voice2目录下的文件
         * 1) 文件名是图片后缀, 发送到queue
         * 2) 文件名不是图片后缀, 删除
         *  */
        try{
            long sleep_millisecond = (5 * 60 * 1000);
            File voice2_dir = new File( WechatClass.getPersonalDir("voice2") );
            if( !voice2_dir.exists() ) {
                log.e("voice2 dir not exists, dir:%s", voice2_dir.getAbsolutePath());
                return;
            }
            log.i("match voice2 dir:%s", voice2_dir.getAbsolutePath());
            /** 遍历voice2子目录下的amr文件 */
            String[] extensions = new String[] { "amr"};
            List<File> files = (List<File>) FileUtils.listFiles(voice2_dir, extensions, true);
            String filename, local_dir, remote_dir;
            for (File f : files) {
                filename = f.getName();
                if(f.length() == 0) {
                    log.d("scanVoice2 delete 0 length file:%s", filename);
                    f.delete();
                    continue;
                }
                /** 判断文件是否超时 */
                if( System.currentTimeMillis() - f.lastModified() <  sleep_millisecond){
                    log.i("scanVoice2 file lastModified < 5 minute, file:%s", filename);
                    continue;
                }
                local_dir = f.getParent();
                remote_dir = local_dir.substring(local_dir.indexOf("/MicroMsg"));   // 截断到以 "/MicroMsg" 开始
                WechatHook.thread_touch.sftp_queue.put( Arrays.asList( local_dir, filename, WechatHook.remoteVoiceDir, filename, "put", "1" ) );
                log.i("put image in sftp_queue, local_dir:%s, filename:%s", local_dir, filename);
            }
        } catch (Exception e) {
            log.e("scanVoice2 error. stack:%s", android.util.Log.getStackTraceString(e));
        }
    }   /** scanVoice2 结束 */

}   /** ScanThread 类结束 */
