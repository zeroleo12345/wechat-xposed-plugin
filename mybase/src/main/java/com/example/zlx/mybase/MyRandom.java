package com.example.zlx.mybase;

import java.util.Random;

public class MyRandom {
    private static final String CHAR_LIST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";

    /**
     * This method generates random string
     * @return
     */
    public static String randomStr(int length) {
        StringBuffer randStr = new StringBuffer();
        for(int i=0; i<length; i++){
            int number = MyRandom.getRandomNumber();
            char ch = CHAR_LIST.charAt(number);
            randStr.append(ch);
        }
        return randStr.toString();
    }

    /**
     * This method generates random numbers
     * @return int
     */
    private static int getRandomNumber() {
        int randomInt = 0;
        Random randomGenerator = new Random();
        randomInt = randomGenerator.nextInt(CHAR_LIST.length());
        if (randomInt - 1 == -1) {
            return randomInt;
        } else {
            return randomInt - 1;
        }
    }

    public static void main(String a[]){
        MyRandom myrandom = new MyRandom();
        String str;
        str = myrandom.randomStr(10);
        System.out.println(str);
        str = myrandom.randomStr(15);
        System.out.println(str);
    }
}