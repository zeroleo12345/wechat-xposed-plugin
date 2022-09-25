package com.example.zlx.mybase;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedWriter;
import java.io.File;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;

import java.io.FileInputStream;
import java.io.FileWriter;

public class MyFile {
    public static String readAsString(String filepath) throws Exception {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filepath));
            // 一次读入一行，直到读入null为文件结束
            StringBuffer sb = new StringBuffer();
            String data = null;
            while ((data = reader.readLine()) != null) {
                sb.append(data);
            }
            reader.close();
            return sb.toString();
        }catch (FileNotFoundException e){
            // 文件不存在时返回NULL
            return null;
        }
    }

    public static void writeAsString(String filepath, String content) throws Exception {
        FileWriter writer = new FileWriter(filepath);
        BufferedWriter bw = new BufferedWriter(writer);
        bw.write(content);
        bw.close();
        writer.close();
    }

    public static JSONObject parseJson(String jsonString) throws Exception{
        JSONTokener jsonParser = new JSONTokener(jsonString);
        JSONObject param_dict = (JSONObject) jsonParser.nextValue();
        return param_dict;
    }

    public static JSONObject readUserConfig(String config_path) throws Exception{
        if( !new File(config_path).exists() )
            return null;

        String jsonString =  MyFile.readAsString(config_path);
        return parseJson(jsonString);
    }


    public static void writeUserConfig(String config_path, JSONObject params) throws Exception{
        String jsonString = params.toString();
        MyFile.writeAsString(config_path, jsonString);
    }
}
