package com.example.zlx.xposeapplication;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.example.zlx.hardware.HardwareInfo;
import com.example.zlx.mybase.MyFile;
import com.example.zlx.mybase.MyHttp;
import com.example.zlx.mybase.MyLog;
import com.example.zlx.mybase.MyMD5;
import com.example.zlx.mybase.MyPath;
import com.example.zlx.mybase.MyString;
import com.example.zlx.mybase.SystemUtil;
import com.example.zlx.mynative.AuthArg;
import com.example.zlx.mynative.JNIUtils;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity  {
    // note: 开启线程, 接收UI更新消息
    Handler selfHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int is_success = msg.what;
            String text = (String)msg.obj;
            text_tips.setText(text);
            if( is_success == 1){
                edit_token.setEnabled(false);
                //btn_authentic.setEnabled(false);
            }
            super.handleMessage(msg);
        }
    };

    EditText edit_token = null;
    EditText edit_rabbitmq_info = null;
    EditText edit_sftp_info = null;
    EditText edit_other_info = null;
    Button btn_authentic = null;
    TextView text_tips = null;
    TextView text_deviceId = null;
    TextView text_gateway = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        MyLog.init_xlog(WechatHook.xLogDir, BuildConfig.PROC_TYPE, BuildConfig.TAG, BuildConfig.LOG_LEVEL);

        edit_token = (EditText) findViewById(R.id.token);
        edit_rabbitmq_info = (EditText) findViewById(R.id.rabbitmq_info);
        edit_sftp_info = (EditText) findViewById(R.id.sftp_info);
        edit_other_info = (EditText) findViewById(R.id.other_info);

        text_tips = (TextView) findViewById(R.id.tips);
        text_deviceId = (TextView) findViewById(R.id.deviceId);
        text_gateway = (TextView) findViewById(R.id.gateway);
        btn_authentic = (Button) findViewById(R.id.authentic);

        btn_authentic.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                try {
                    text_tips.setText("");
                    String input_sftp = edit_sftp_info.getText().toString().trim();
                    String input_other = edit_other_info.getText().toString().trim();
                    String input_rabbitmq = edit_rabbitmq_info.getText().toString().trim();
                    String input_token = edit_token.getText().toString().trim();

                    if (TextUtils.isEmpty(input_token)) {
                        Message msg = selfHandler.obtainMessage();
                        msg.what = 0;
                        msg.obj = "输入token为空";
                        msg.sendToTarget();
                        return;
                    }


                    VerifyThread.gui_token = input_token;
                    XLog.d("Button click, token:" + VerifyThread.gui_token);
                    if (VerifyThread.gui_token.length() > 0) {
                        /** RABBITMQ */
                        JSONObject rabbitmq = MyFile.parseJson(input_rabbitmq);
                        UserConfig.put("rabbitmq", rabbitmq);

                        /** SFTP */
                        JSONObject sftp = MyFile.parseJson(input_sftp);
                        UserConfig.put("sftp", sftp);

                        /** 其他 */
                        JSONObject other = MyFile.parseJson(input_other);
                        UserConfig.put("other", other);

                        /** 发送验证 */
                        VerifyThread.post_authentic_simple(selfHandler, VerifyThread.gui_token);
                    }
                }catch (Throwable e){
                    XLog.e("onClick error. stack:" + android.util.Log.getStackTraceString(e));
                }
            }
        });
        Context context = getApplicationContext();
        HardwareInfo hw_info = new HardwareInfo(context);
        try {
            UserConfig.init_from_config(hw_info.gateway);
        }catch (Exception e){
            XLog.e("UserConfig.init error, gateway: %d", hw_info.gateway);
            return;
        }
        text_deviceId.setText(hw_info.deviceId);
        text_deviceId.setTextIsSelectable(true);
        text_gateway.setText(hw_info.gateway);
        text_gateway.setTextIsSelectable(true);
        if(BuildConfig.DEBUG) {
            try {
                VerifyThread.lib_dir = context.getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0).applicationInfo.nativeLibraryDir;
                XLog.d("MyPath:%s", VerifyThread.lib_dir);
                String sign = SystemUtil.getSign(context, BuildConfig.APPLICATION_ID);
                XLog.d("KeyHash: %s", sign);
                JNIUtils.init_so(VerifyThread.lib_dir, sign);
                // note: 多2个按钮, 用于测试I, U包
                ((Button) findViewById(R.id.test_i)).setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        VerifyThread.post_authentic_packet( AuthArg.authentic_i );    // note 测试代码
                                    } catch (Exception e) {
                                        XLog.e("post authentic_packet error. stack:" + android.util.Log.getStackTraceString(e));
                                    }
                                }
                            }).start(); //启动线程
                        } catch (Throwable e) {
                            XLog.e("onClick error. stack:" + android.util.Log.getStackTraceString(e));
                        }
                    }
                });
                ((Button) findViewById(R.id.test_u)).setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        VerifyThread.post_authentic_packet(AuthArg.authentic_u);    // note 测试代码
                                    } catch (Exception e) {
                                        XLog.e("post authentic_packet error. stack:" + android.util.Log.getStackTraceString(e));
                                    }
                                }
                            }).start(); //启动线程
                        } catch (Throwable e) {
                            XLog.e("onClick error. stack:" + android.util.Log.getStackTraceString(e));
                        }
                    }
                });
                ((Button) findViewById(R.id.test_other)).setOnClickListener(new Button.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            String string = JNIUtils.getString();
                            XLog.d("getString;%s", string);
                        } catch (Throwable e) {
                            XLog.e("onClick error. stack:" + android.util.Log.getStackTraceString(e));
                        }
                    }
                });
            }catch(Throwable e){
                XLog.e("stack:" + android.util.Log.getStackTraceString(e));
            }
        }else{
            // 隐藏按钮
            ((Button) findViewById(R.id.test_i)).setVisibility(View.GONE);  // View.GONE and View.VISIBLE
            ((Button) findViewById(R.id.test_u)).setVisibility(View.GONE);  // View.GONE and View.VISIBLE
            ((Button) findViewById(R.id.test_other)).setVisibility(View.GONE);  // View.GONE and View.VISIBLE
        }
        try {
            String user_token = UserConfig.getString("token");
            edit_token.setText(user_token);

            JSONObject sftp = UserConfig.user_config.getJSONObject("sftp");
            edit_sftp_info.setText(sftp.toString());

            JSONObject other = UserConfig.user_config.getJSONObject("other");
            edit_other_info.setText(other.toString());

            JSONObject rabbitmq = UserConfig.user_config.getJSONObject("rabbitmq");
            edit_rabbitmq_info.setText(rabbitmq.toString());
        }catch (Throwable e){}
    }

}
