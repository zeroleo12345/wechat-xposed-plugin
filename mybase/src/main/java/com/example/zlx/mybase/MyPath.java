package com.example.zlx.mybase;

import java.io.File;

/**
 * Created by zlx on 2017/10/12.
 */

public class MyPath {
    public static String join(String... paths)
    {
        File file = new File(paths[0]);

        for (int i = 1; i < paths.length ; i++) {
            file = new File(file, paths[i]);
        }

        return file.getPath();
    }
}
