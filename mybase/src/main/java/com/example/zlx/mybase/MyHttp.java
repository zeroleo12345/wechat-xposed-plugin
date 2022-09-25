package com.example.zlx.mybase;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * note. 参考文章:    http://www.cnblogs.com/menlsh/archive/2013/05/22/3091983.html
 */

public class MyHttp {

    /**
     * Post 表单
     * @param url   如: "http://{URL}/wxapi/hwinfo.json"
     * @param params 请求体内容
     * @return      成功: 处理服务器的响应结果;     失败: ""
     * @throws Exception
     */
    public static String post_text(String url, Map<String, String> params) throws Exception {
        String encode = "utf-8";                            // 固定编码格式. "utf-8"
        URL _URL = new URL(url);
        HttpURLConnection httpURLConnection = (HttpURLConnection)_URL.openConnection();
        httpURLConnection.setConnectTimeout(3000);           //设置连接超时时间
        httpURLConnection.setDoInput(true);                  //打开输入流，以便从服务器获取数据
        httpURLConnection.setDoOutput(true);                 //打开输出流，以便向服务器提交数据
        httpURLConnection.setRequestMethod("POST");          //设置以Post方式提交数据
        httpURLConnection.setUseCaches(false);               //使用Post方式不能使用缓存
        //设置请求体的类型是文本类型
        httpURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        //
        if( params != null ) {
            byte[] data = getRequestData(params, encode).toString().getBytes();//获得请求体
            //设置请求体的长度
            httpURLConnection.setRequestProperty("Content-Length", String.valueOf(data.length));
            //获得输出流，向服务器写入数据
            OutputStream outputStream = httpURLConnection.getOutputStream();
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();
        }

        int response = httpURLConnection.getResponseCode();            //获得服务器的响应码
        if(response == HttpURLConnection.HTTP_OK) {
            InputStream inptStream = httpURLConnection.getInputStream();
            return dealResponseResult(inptStream);                     //处理服务器的响应结果
        }else {
            return "";
        }
    }


    public static String post_json(String url, JSONObject jsonParam, HashMap<String,String> header, String encode) throws Exception {
//    public static String post_json(String url, JSONObject jsonParam, String encode) throws Exception {
        /** 参考: https://stackoverflow.com/questions/13911993/sending-a-json-http-post-request-from-android
         * url: "http://{URL}/wxapi/hwinfo.json"
         * encode:  "utf-8"
         */
        URL _URL = new URL(url);
        HttpURLConnection httpURLConnection = (HttpURLConnection)_URL.openConnection();
        httpURLConnection.setConnectTimeout(3000);           //设置连接超时时间
        httpURLConnection.setDoInput(true);                  //打开输入流，以便从服务器获取数据
        httpURLConnection.setDoOutput(true);                 //打开输出流，以便向服务器提交数据
        httpURLConnection.setRequestMethod("POST");          //设置以Post方式提交数据
        httpURLConnection.setUseCaches(false);               //使用Post方式不能使用缓存
        httpURLConnection.setRequestProperty("Content-Type", "application/json");//设置请求体的类型是JSON
        httpURLConnection.setRequestProperty("Accept", "application/json");
        httpURLConnection.setRequestProperty("Charset", "UTF-8");// 设置文件字符集:
        if( header != null ) {
            for (String key : header.keySet()) {
                httpURLConnection.addRequestProperty(key, header.get(key));
            }
        }
        //获得输出流，向服务器写入数据
        byte[] data = (jsonParam.toString()).getBytes();    //转换为字节数组
        httpURLConnection.setRequestProperty("Content-Length", String.valueOf(data.length));    // 设置文件长度
        OutputStream outputStream = httpURLConnection.getOutputStream();
        outputStream.write(data);
        outputStream.flush();
        outputStream.close();

        int response = httpURLConnection.getResponseCode();            //获得服务器的响应码
        if(response == HttpURLConnection.HTTP_OK) {
            InputStream inptStream = httpURLConnection.getInputStream();
            return dealResponseResult(inptStream);                     //处理服务器的响应结果
        }else {
            return "";
        }
    }


    /*
     * Function  :   封装请求体信息
     * Param     :   params请求体内容，encode编码格式
     */
    public static StringBuffer getRequestData(Map<String, String> params, String encode) {
        StringBuffer stringBuffer = new StringBuffer();        //存储封装好的请求体信息
        try {
            for(Map.Entry<String, String> entry : params.entrySet()) {
                stringBuffer.append(entry.getKey())
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), encode))
                        .append("&");
            }
            stringBuffer.deleteCharAt(stringBuffer.length() - 1);    //删除最后的一个"&"
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuffer;
    }


    /*
     * Function  :   处理服务器的响应结果（将输入流转化成字符串）
     * Param     :   inputStream服务器的响应输入流
     */
    public static String dealResponseResult(InputStream inputStream) {
        String resultData = null;      //存储处理结果
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int len = 0;
        try {
            while((len = inputStream.read(data)) != -1) {
                byteArrayOutputStream.write(data, 0, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        resultData = new String(byteArrayOutputStream.toByteArray());
        return resultData;
    }
}
