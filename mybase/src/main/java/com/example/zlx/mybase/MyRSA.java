package com.example.zlx.mybase;

/**
 * Created by zlx on 2017/3/23 0023.
 */


import java.io.*;
//import java.util.Base64;
import android.util.Base64;

import javax.crypto.Cipher;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * RSA 加解密工具类
 */
public class MyRSA {
    /**
     * 定义加密方式
     */
    private final static String KEY_RSA = "RSA";
    /**
     * 定义签名算法
     */
    private final static String KEY_RSA_SIGNATURE = "MD5withRSA";

    /**
     * 用私钥对信息生成数字签名
     * @param data 加密数据
     * @param privateKey 私钥
     * @return
     */
    public static String sign(byte[] data, String privateKey) {
        String str = "";
        try {
            // 解密由base64编码的私钥
            byte[] bytes = decryptBase64(privateKey);
            // 构造PKCS8EncodedKeySpec对象
            PKCS8EncodedKeySpec pkcs = new PKCS8EncodedKeySpec(bytes);
            // 指定的加密算法
            KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
            // 取私钥对象
            PrivateKey key = factory.generatePrivate(pkcs);
            // 用私钥对信息生成数字签名
            Signature signature = Signature.getInstance(KEY_RSA_SIGNATURE);
            signature.initSign(key);
            signature.update(data);
            str = encryptBase64(signature.sign());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return str;
    }

    /**
     * 校验数字签名
     * @param data 加密数据
     * @param publicKey 公钥
     * @param sign 数字签名
     * @return 校验成功返回true，失败返回false
     */
    public static boolean verify(byte[] data, String publicKey, String sign) {
        boolean flag = false;
        try {
            // 解密由base64编码的公钥
            byte[] bytes = decryptBase64(publicKey);
            // 构造X509EncodedKeySpec对象
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
            // 指定的加密算法
            KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
            // 取公钥对象
            PublicKey key = factory.generatePublic(keySpec);
            // 用公钥验证数字签名
            Signature signature = Signature.getInstance(KEY_RSA_SIGNATURE);
            signature.initVerify(key);
            signature.update(data);
            flag = signature.verify(decryptBase64(sign));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

    /**
     * 私钥解密
     * @param data 加密数据
     * @param key 私钥
     * @return
     */
    public static byte[] decryptByPrivateKey(byte[] data, String key) throws Exception{
        byte[] result = null;
        // 对私钥解密
        byte[] bytes = decryptBase64(key);
        // 取得私钥
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
        KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
        PrivateKey privateKey = factory.generatePrivate(keySpec);
        // 对数据解密
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        result = cipher.doFinal(data);
        return result;
    }

    /**
     * 私钥解密
     * @param data 加密数据
     * @param key 公钥
     * @return
     */
    public static byte[] decryptByPublicKey(byte[] data, String key) {
        byte[] result = null;
        try {
            // 对公钥解密
            byte[] bytes = decryptBase64(key);
            // 取得公钥
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
            KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
            PublicKey publicKey = factory.generatePublic(keySpec);
            // 对数据解密
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, publicKey);
            result = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 公钥加密
     * @param data 待加密数据
     * @param key 公钥
     * @return
     */
    public static byte[] encryptByPublicKey(byte[] data, String key) {
        byte[] result = null;
        try {
            byte[] bytes = decryptBase64(key);
            // 取得公钥
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
            KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
            PublicKey publicKey = factory.generatePublic(keySpec);
            // 对数据加密
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            result = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 私钥加密
     * @param data 待加密数据
     * @param key 私钥
     * @return
     */
    public static byte[] encryptByPrivateKey(byte[] data, String key) {
        byte[] result = null;
        try {
            byte[] bytes = decryptBase64(key);
            // 取得私钥
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
            KeyFactory factory = KeyFactory.getInstance(KEY_RSA);
            PrivateKey privateKey = factory.generatePrivate(keySpec);
            // 对数据加密
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
            result = cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * 获取公钥
     * @param PUBLICKEY
     * @return
     */
    /**
     * BASE64 解密
     * @param key 需要解密的字符串
     * @return 字节数组
     * @throws Exception
     */
    public static byte[] decryptBase64(String key) throws Exception {
        //return Base64.getDecoder().decode(key);
        return Base64.decode(key, Base64.NO_PADDING);
    }

    /**
     * BASE64 加密
     * @param key 需要加密的字节数组
     * @return 字符串
     * @throws Exception
     */
    public static String encryptBase64(byte[] key) throws Exception {
        //return Base64.getEncoder().encodeToString(key);
        return Base64.encodeToString(key, Base64.NO_PADDING);
    }

    /**
     * 生成公钥, 私钥
     */
    public static List<String> genKeypair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_RSA);
            generator.initialize(1024);
            KeyPair keyPair = generator.generateKeyPair();
            // 公钥
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            // 私钥
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            // 使用Base64转成可见字符
            String str_publicKey = encryptBase64(publicKey.getEncoded());
            String str_privateKey = encryptBase64(privateKey.getEncoded());
            // 返回
            List<String> keypair = new ArrayList<>();
            keypair.add(str_publicKey);
            keypair.add(str_privateKey);
            return keypair;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) {
        class test_gen_key{
            public test_gen_key() {
                List<String> keypair = genKeypair();
                System.out.println("公钥:\n\r" + keypair.get(0));
                System.out.println("私钥:\n\r" + keypair.get(1));

                try{
                    // 写入文件
                    BufferedWriter bw;
                    FileWriter writer;
                    writer = new FileWriter("RSAPublicKey");
                    bw = new BufferedWriter(writer);
                    bw.write(keypair.get(0));
                    bw.close();
                    writer.close();

                    writer = new FileWriter("RSAPrivateKey");
                    bw = new BufferedWriter(writer);
                    bw.write(keypair.get(1));
                    bw.close();
                    writer.close();

                    System.out.println("Success write!");
                }catch(Exception e){ e.printStackTrace(); }
            }
        }
        class test_encrypt{
            public test_encrypt() {
                String publicKey;
                String privateKey;
                File file;
                int size;
                byte [] bytes;
                BufferedInputStream buf;
                try{
                    file = new File("RSAPublicKey");
                    size = (int) file.length();
                    bytes = new byte[size];
                    buf = new BufferedInputStream(new FileInputStream(file));
                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                    publicKey = new String(bytes);

                    file = new File("RSAPrivateKey");
                    size = (int) file.length();
                    bytes = new byte[size];
                    buf = new BufferedInputStream(new FileInputStream(file));
                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                    privateKey = new String(bytes);

                    System.out.println("公钥:\n\r" + publicKey);
                    System.out.println("私钥:\n\r" + privateKey);

                    // 测试加解密
                    System.out.println("公钥加密--------私钥解密");
                    String word = "你好，世界!";
                    byte[] encWord = encryptByPublicKey(word.getBytes(), publicKey);
                    String decWord = new String(decryptByPrivateKey(encWord, privateKey));
                    System.out.println("加密前: " + word + "\n\r" + "解密后: " + decWord);
                    System.out.println("私钥加密--------公钥解密");
                    String english = "Hello, World!";
                    byte[] encEnglish = encryptByPrivateKey(english.getBytes(), privateKey);
                    String decEnglish = new String(decryptByPublicKey(encEnglish, publicKey));
                    System.out.println("加密前: " + english + "\n\r" + "解密后: " + decEnglish);
                    System.out.println("私钥签名--公钥验证签名");
                    // 产生签名
                    String sign = sign(encEnglish, privateKey);
                    System.out.println("签名:\r" + sign);
                    // 验证签名
                    boolean status = verify(encEnglish, publicKey, sign);
                    System.out.println("状态:\r" + status);
                }catch(Exception e){
                    e.printStackTrace();
                    return;
                }
            }
        }
        switch(args[0]){
            case "test_gen_key":{
                test_gen_key a = new test_gen_key();
                break;
            }
            case "test_encrypt":{
                new test_encrypt();
            }
        }
    }
}