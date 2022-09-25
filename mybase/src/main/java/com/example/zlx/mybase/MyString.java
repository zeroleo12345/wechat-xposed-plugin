package com.example.zlx.mybase;

/**
 * note 出处: http://www.cnblogs.com/freeliver54/archive/2012/07/30/2615149.html
 */

import java.lang.reflect.Field;

/**
 * Base64.CRLF 它就是Win风格的换行符，意思就是使用CR LF这一对作为一行的结尾而不是Unix风格的LF
 * Base64.DEFAULT 使用默认的方法来加密
 * Base64.NO_PADDING 略去加密字符串最后的=
 * Base64.NO_WRAP 略去所有的换行符 (设置后CRLF就没用了)
 * Base64.URL_SAFE 加密时不使用对URL和文件名有特殊意义的字符来作为加密字符, 以-和_取代+和/
 * */
public class MyString {
    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }
    /**
     * Convert hex string to byte[]
     * @param hexString the hex string
     * @return byte[]
     */
    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }
    /**
     * Convert char to byte
     * @param c char
     * @return byte
     */
    static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    //将指定byte数组以16进制的形式打印到控制台
    public static void printHexString( byte[] b) {
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            System.out.print(hex.toUpperCase() );
        }
    }

    public static String stringToHexString(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = (int) s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + "\\u" + String.format("%4s", s4).replace(' ','0');
        }
        return str;
    }

    /**
     * 作用: 用于打印String[] 数组:
     *  把String[] 转成 String
     */
    public static String StringArraytoString(String[] strings) {
        if( strings == null) return "null";
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (String string : strings) {
            if (first)
                first = false;
            else
                sb.append(",");

            if (string != null)
                sb.append(string);
            else
                sb.append("null");
        }
        sb.append(")");
        return sb.toString();
    }

    public static String dump(Object targetObject, String... filters) throws Exception{
        StringBuilder result = new StringBuilder();
        String newLine = System.getProperty("line.separator");

        result.append( targetObject.getClass().getName() );
        result.append( " Object {" );
        result.append(newLine);

        Class clazz = targetObject.getClass();
        do {
            for (Field field : clazz.getDeclaredFields()) {
                result.append("  ");
                try {
                    String fieldName = field.getName();
                    boolean ispass = false;
                    for(String filter : filters) {
                        if( fieldName.matches(filter) )
                            ispass = true;
                    }
                    if( ispass ) continue;
                    //
                    result.append( fieldName );
                    result.append(": ");
                    // requires access to private field:
                    field.setAccessible(true);
                    result.append( field.get(targetObject) );
                } catch ( IllegalAccessException e ) {
                    //System.out.println(ex);
                    throw e;
                }
                result.append(newLine);
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);
        result.append("}");
        return result.toString();
    }
}
