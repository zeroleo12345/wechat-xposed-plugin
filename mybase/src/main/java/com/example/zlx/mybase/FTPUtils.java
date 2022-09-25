package com.example.zlx.mybase;

import android.annotation.SuppressLint;
import android.util.Log;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by zlx on 2017/7/27.
 * note: https://commons.apache.org/proper/commons-net/apidocs/org/apache/commons/net/ftp/FTPClient.html
 */

public class FTPUtils {

    private String TAG="FTPUtils";
    private String host;
    private String username;
    private String password;
    private int port = 21;  // ftp默认使用22端口
    private FTPClient ftp;

    public FTPUtils (String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * connect server via ftp
     */
    public FTPClient connect() throws Exception{
        try {
            ftp = new FTPClient();
            //设定连接超时时间
            ftp.setConnectTimeout(5000);      // 单位: 毫秒
            ftp.connect(host);
            // 看返回的值是不是230，如果是，表示登陆成功
            int reply = ftp.getReplyCode();
            if ( !FTPReply.isPositiveCompletion(reply) ) {
                disconnect();
                throw new IOException("isPositiveCompletion fail");
            }
            //登录
            if ( !ftp.login(username, password) ) {
                disconnect();
                throw new IOException("login fail");
            }
            // 设置上传文件需要的一些基本信息
            ftp.setBufferSize(1024);
            ftp.setControlEncoding("UTF-8");
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTPClient.BINARY_FILE_TYPE);
        } catch (Exception e) {
            throw e;
        }
        return ftp;
    }

    /**
     * 断开服务器
     */
    public void disconnect() {
        if (this.ftp != null) {
            if (this.ftp.isConnected()) {
                try{this.ftp.disconnect();}
                catch(Exception e){}
                Log.d(TAG,"ftp is closed already");
            }
        }
    }

    public void createDir(String createpath) throws Exception{
        try {
            if (isDirExist(createpath)) {
                this.ftp.changeWorkingDirectory(createpath);
                return;
            }
            String pathArry[] = createpath.split("/");
            StringBuffer filePath = new StringBuffer("/");
            for (String path : pathArry) {
                if (path.equals("")) {
                    continue;
                }
                filePath.append(path + "/");
                if (isDirExist(filePath.toString())) {
                    ftp.changeWorkingDirectory(filePath.toString());
                } else {
                    ftp.makeDirectory(filePath.toString());
                    ftp.changeWorkingDirectory(filePath.toString());
                }
            }
            this.ftp.changeWorkingDirectory(createpath);
        } catch (Exception e) { //SftpException
            Log.e(TAG, String.format("createpath:%s", createpath));
            throw e;
        }
    }

    /**
     * 单个文件上传
     * @param remoteDir
     * @param remoteFileName
     * @param localDir
     * @param localFileName
     * @return
     */
    public void uploadFile(String localDir, String localFileName, String remoteDir, String remoteFileName) throws Exception{
        FileInputStream in = null;
        try {
            createDir(remoteDir);
            File file = new File(localDir, localFileName);
            in = new FileInputStream(file);
            /** 上传tmp文件 */
            String tmpFileName = remoteFileName + ".tmp";
            ftp.storeFile(tmpFileName, in);
            /** 修正tmp文件时间戳 */
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            String yyyyMMddHHmmss = formatter.format(new java.util.Date(file.lastModified()));
            ftp.setModificationTime(tmpFileName, yyyyMMddHHmmss);
            /** tmp文件 重命名为 正式文件 */
            ftp.rename( MyPath.join(remoteDir, tmpFileName), MyPath.join(remoteDir, remoteFileName));
        } catch (Exception e) { //FileNotFoundException, SftpException
            Log.e(TAG, String.format("localDir:%s, localFileName:%s, remoteDir:%s, remoteFileName:%s", localDir, localFileName, remoteDir, remoteFileName));
            throw e;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.e(TAG, String.format("localDir:%s, localFileName:%s, remoteDir:%s, remoteFileName:%s", localDir, localFileName, remoteDir, remoteFileName));
                    throw e;
                }
            }
        }
    }


    /**
     * 删除文件
     * @param filePath
     * @return
     */
    public boolean deleteLocalFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return false;
        }
        if (!file.isFile()) {
            return false;
        }
        return file.delete();
    }

    /**
     * 判断目录是否存在
     * @param directory
     * @return
     */
    @SuppressLint("DefaultLocale")
    public boolean isDirExist(String directory) throws Exception{
        try {
            return ftp.changeWorkingDirectory(directory);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 创建目录
     * @param path
     */
    public void mkdirs(String path) {
        File f = new File(path);
        String fs = f.getParent();
        f = new File(fs);
        if (!f.exists()) {
            f.mkdirs();
        }
    }

}