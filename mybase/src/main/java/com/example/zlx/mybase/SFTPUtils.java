package com.example.zlx.mybase;

import android.annotation.SuppressLint;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

/**
 * Created by zlx on 2017/7/27.
 */

public class SFTPUtils {

    private String TAG="SFTPUtils";
    private String host;
    private String username;
    private String password;
    private int timeout = 2000;
    private int port = 22;  // sftp默认使用22端口
    private ChannelSftp sftp = null;
    private Session sshSession = null;

    public SFTPUtils (String host, int port, String username, String password, int timeout) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.timeout = timeout;
    }

    /**
     * connect server via sftp
     */
    public ChannelSftp connect() throws Exception{
        JSch jsch = new JSch();
        try {
            sshSession = jsch.getSession(username, host, port);
            sshSession.setPassword(password);
            sshSession.setTimeout(timeout);
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            Channel channel = sshSession.openChannel("sftp");
            if (channel != null) {
                channel.connect();
            } else {
                throw new Exception("channel connecting failed");
            }
            sftp = (ChannelSftp) channel;
        } catch (Exception e) {  //JSchException
            //Log.e(TAG, android.util.Log.getStackTraceString(e));
            throw e;
        }
        return sftp;
    }

    /**
     * 断开服务器
     */
    public void disconnect() {
        if (this.sftp != null) {
            if (this.sftp.isConnected()) {
                this.sftp.disconnect();
                Log.d(TAG,"sftp is closed already");
            }
        }
        if (this.sshSession != null) {
            if (this.sshSession.isConnected()) {
                this.sshSession.disconnect();
                Log.d(TAG,"sshSession is closed already");
            }
        }
    }

    public void createDir(String createpath) throws Exception{
        try {
            if (isDirExist(createpath)) {
                this.sftp.cd(createpath);
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
                    sftp.cd(filePath.toString());
                } else {
                    sftp.mkdir(filePath.toString());
                    sftp.cd(filePath.toString());
                }
            }
            this.sftp.cd(createpath);
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
            sftp.put(in, tmpFileName);
            /** 修正tmp文件时间戳 */
            int lastmodify = (int)( file.lastModified() / 1000 );
            sftp.setMtime(tmpFileName, lastmodify);
            /** tmp文件 重命名为 正式文件 */
            sftp.rename( MyPath.join(remoteDir, tmpFileName), MyPath.join(remoteDir, remoteFileName));
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
     * 批量上传
     * @param remoteDir
     * @param localDir
     * @param del
     * @return
     */
    public boolean bacthUploadFile(String remoteDir, String localDir,
                                   boolean del) {
        try {
            File file = new File(localDir);
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isFile()  &&  files[i].getName().indexOf("bak") == -1) {
                    synchronized(remoteDir){
                        if (del) {
                            this.uploadFile(localDir, files[i].getName(), remoteDir, files[i].getName() );
                            deleteFile(localDir + files[i].getName());
                        }
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.disconnect();
        }
        return false;

    }

    /**
     * 批量下载文件
     *
     * @param remotPath     远程下载目录(以路径符号结束)
     * @param localDir     本地保存目录(以路径符号结束)
     * @param fileFormat    下载文件格式(以特定字符开头,为空不做检验)
     * @param del   下载后是否删除sftp文件
     * @return
     */
    @SuppressWarnings("rawtypes")
    public boolean batchDownLoadFile(String remotPath, String localDir,
                                     String fileFormat, boolean del) throws Exception{
        try {
            connect();
            Vector v = listFiles(remotPath);
            if (v.size() > 0) {

                Iterator it = v.iterator();
                while (it.hasNext()) {
                    ChannelSftp.LsEntry entry = (ChannelSftp.LsEntry) it.next();
                    String filename = entry.getFilename();
                    SftpATTRS attrs = entry.getAttrs();
                    if (!attrs.isDir()) {
                        if (fileFormat != null && !"".equals(fileFormat.trim())) {
                            if (filename.startsWith(fileFormat)) {
                                this.downloadFile(remotPath, filename, localDir, filename);
                                if (del) {
                                    deleteSFTP(remotPath, filename);
                                }
                            }
                        } else {
                            this.downloadFile(remotPath, filename, localDir, filename);
                            if (del) {
                                deleteSFTP(remotPath, filename);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {  //SftpException
            Log.e(TAG, String.format("remotPath:%s, localDir:%s", remotPath, localDir));
            throw e;
        } finally {
            this.disconnect();
        }
        return false;
    }

    /**
     * 单个文件下载
     * @param remoteDir
     * @param remoteFileName
     * @param localDir
     * @param localFileName
     * @return
     */
    public void downloadFile(String remoteDir, String remoteFileName, String localDir, String localFileName) throws Exception{
        try {
            sftp.cd(remoteDir);
            File file = new File(localDir, localFileName);
            mkdirs(localDir + localFileName);
            sftp.get(remoteFileName, new FileOutputStream(file));
        } catch (Exception e) { // FileNotFoundException, SftpException
            Log.e(TAG, String.format("remoteDir:%s, remoteFileName:%s, localDir:%s, localFileName:%s", remoteDir, remoteFileName, localDir, localFileName));
            throw e;
        }
    }


    /**
     * 删除文件
     * @param filePath
     * @return
     */
    public static boolean deleteFile(String filePath) {
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
     * 递归删除目录
     * @param fileOrDirectory
     */
    public static void deleteDir(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles())
                deleteDir(child);
        }
        fileOrDirectory.delete();
    }
    /**
     * 判断目录是否存在
     * @param directory
     * @return
     */
    @SuppressLint("DefaultLocale")
    public boolean isDirExist(String directory) throws Exception{
        boolean isExist = false;
        try {
            SftpATTRS sftpATTRS = sftp.lstat(directory);
            isExist = true;
            return sftpATTRS.isDir();
        } catch (Exception e) {
            if (e.getMessage().toLowerCase().equals("no such file")) {
                isExist = false;
            }else{
                Log.e(TAG, String.format("directory:%s", directory));
                throw e;
            }
        }
        return isExist;
    }

    public void deleteSFTP(String directory, String deleteFile) throws Exception{
        try {
            sftp.cd(directory);
            sftp.rm(deleteFile);
        } catch (Exception e) {;
            Log.e(TAG, String.format("directory:%s, deleteFile:%s", directory, deleteFile));
            throw e;
        }
    }


    /**
     * 目录不存在时, 创建目录; 存在时, 不做任何事
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

    /**
     * 列出目录文件
     * @param directory
     * @return
     * @throws SftpException
     */
    @SuppressWarnings("rawtypes")
    public Vector listFiles(String directory) throws SftpException {
        return sftp.ls(directory);
    }

}