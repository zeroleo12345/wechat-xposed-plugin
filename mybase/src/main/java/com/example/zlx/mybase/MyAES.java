package com.example.zlx.mybase;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import com.example.zlx.mybase.MyBase64;

public class MyAES {

     public static String _autofill(String key){
         // note: 当key或iv不足16个字符的时候, 后面补字符'0'; 当超过16个字符的时候, 截断为前面16个字符
         int key_len = key.length();
        if( key_len > 16 ){
            key = key.substring(0, 16);
            //System.out.println( ">16 char, auto cut to 16, key:" + key);
        }else if( key_len < 16 ){
            int fill_len = 16 - key_len;
            key = key + new String(new char[fill_len]).replace('\0', '0');
            //System.out.println( "<16 char, auto fill to 16, key:" + key);
        }else{
            //System.out.println( "=16 char, key:" + key);
        }
        return key;
    }

    public static String encrypt_string_and_b64(String str, String key, String initVector) {
        try {
            byte[] data = str.getBytes();
            key = _autofill(key);
            initVector = _autofill(initVector);
            byte[] _key = key.getBytes();
            byte[] _initVector = initVector.getBytes();
            IvParameterSpec iv = new IvParameterSpec(_initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(_key, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            byte[] result = cipher.doFinal(data);
            return MyBase64.b64encode(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] encrypt_byte(byte[] data, String key, String initVector) {
        byte[] result = null;
        try {
            key = _autofill(key);
            initVector = _autofill(initVector);
            byte[] _key = key.getBytes();
            byte[] _initVector = initVector.getBytes();
            IvParameterSpec iv = new IvParameterSpec(_initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(_key, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

            result = cipher.doFinal(data);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String decrypt_b64string(String b64string, String key, String initVector) {
        try {
            byte[] data = MyBase64.b64decode(b64string);
            key = _autofill(key);
            initVector = _autofill(initVector);
            byte[] _key = key.getBytes();
            byte[] _initVector = initVector.getBytes();
            IvParameterSpec iv = new IvParameterSpec(_initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(_key, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            byte[] result = cipher.doFinal( data );
            return new String(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decrypt_byte(byte[] data, String key, String initVector) {
        byte[] result = null;
        try {
            key = _autofill(key);
            initVector = _autofill(initVector);
            byte[] _key = key.getBytes();
            byte[] _initVector = initVector.getBytes();
            IvParameterSpec iv = new IvParameterSpec(_initVector);
            SecretKeySpec skeySpec = new SecretKeySpec(_key, "AES");

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

            result = cipher.doFinal( data );
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static String flips(String input)  throws java.io.UnsupportedEncodingException {
        byte[] bytes = input.getBytes("UTF-16BE");
        for(int i=0; i<bytes.length; i++){
            bytes[i] = (byte)~bytes[i];
        }
        return new String(bytes, "UTF-16BE");
    }

    public static void main(String[] args) {
        /* note: jave的AES算法标准库不能生成256字节, 会抛出异常: java.security.InvalidKeyException: Illegal key size or default parameters
         * 需要另外安装库: Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 7 Download
         * 所以与python协商使用"aes_128_cbc"算法
         * Usage:
         *   ./r.py AES.java "INIpLr7NJIhhsdBuMsIeQwtcyErTBHTCg6kG6hQcwLfG5doMnyIhheQbLkq/h5b8"
         * Output:
         *   00000000-74a8-7f57-ffff-fffff421e423
         */

        try{
            File file;
            BufferedInputStream buf;
            byte[] bytes;
            String encStr;
            String key = "hookGetMessage(h"; // must be 128 bit key. 1 byte = 8 bit, the string has 16 char
            // initVector: must be 128 bit IV. 1 byte = 8 bit, the string has 16 char, 等同于: { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
            //String initVector = "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000";
            String initVector = "afterHookFuctio";
            System.out.println( "ivBytes长度:" + initVector.getBytes().length );
            String encrypt_file = "encrypt.txt";

            // 加密
            encStr = args[0];
            System.out.println( "\n加密前(明文string):" + encStr );
            byte[] encBytes = MyAES.encrypt_byte( encStr.getBytes(), key, initVector );
            // 加密后字节写入文件保存
            FileOutputStream out = new FileOutputStream(new File(encrypt_file));
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            out.write( encBytes );
            out.close();
            //
            encStr = MyBase64.b64encode( encBytes );
            System.out.println( "加密后(base64编码打印, 本质是二进制串): " + encStr );
            System.out.println( "长度: " + encStr.length() );

            // 读取文件
            file = new File(encrypt_file);
            bytes = new byte[(int)file.length()];
            buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
            encStr = MyBase64.b64encode( bytes );
            System.out.println( "读取加密后文件(base64编码打印, 本质是二进制串): " + encStr );
            System.out.println( "长度: " + encStr.length() );

            // 解密
            byte[] decBytes = MyAES.decrypt_byte( MyBase64.b64decode(encStr), key, initVector );

            String decWord = new String(decBytes);
            System.out.println( "解密后(明文string):" + decWord );
            System.out.println( "长度: " + decWord.length() );
            System.out.println( "密文文件(二进制): " + encrypt_file );
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
