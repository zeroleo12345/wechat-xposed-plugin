package com.example.zlx.mybase;

import java.security.MessageDigest;

public class MyMD5 {

    public static String calmd5(String key) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update( key.getBytes("UTF-8") );
        String hex_str = MyString.bytesToHexString( md5.digest() );
        return hex_str;
    }

    private static String getString(byte[] b){
        StringBuffer sb = new StringBuffer();
        for(int i = 0; i < b.length; i ++){
            sb.append(b[i]);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        /*
         * Usage:
         *   ./r.py MyMD5.java "123"
         * Output:
         *   00000000-74a8-7f57-ffff-fffff421e423
         */
        try{
            String key = "3e4b9a13857e53aa9cbf9e0e9fe38b01";
            String result = MyMD5.calmd5( key );
            System.out.println( "\n加密前(明文string):" + key );
            System.out.println( "密文文件(二进制): " + result );
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}

