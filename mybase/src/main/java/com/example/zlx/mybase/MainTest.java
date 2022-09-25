package com.example.zlx.mybase;

import com.example.zlx.mybase.MyMD5;

import java.util.Locale;

public class MainTest {
    public static void main(String[] args) {
        System.out.print("android module2222");
        try{
            String md5_key = "3e4b9a13857e53aa9cbf9e0e9fe38b01";
            String result = MyMD5.calmd5( md5_key );
            System.out.println( "\n加密前(明文string):" + md5_key );
            System.out.println( "密文文件(二进制): " + result );

            md5_key = "3e4b9a13857e53aa9cbf9e0e9fe38b01" + "please stop crack";
            result = MyMD5.calmd5( md5_key );
            System.out.println( "\n加密前(明文string):" + md5_key );
            System.out.println( "密文文件(二进制): " + result );

            //
            MyRandom msr = new MyRandom();
            System.out.println(msr.randomStr(10));
            System.out.println(msr.randomStr(15));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
