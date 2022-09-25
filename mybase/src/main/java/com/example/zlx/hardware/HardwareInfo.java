package com.example.zlx.hardware;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.elvishew.xlog.XLog;
import com.example.zlx.mybase.MyPath;
import com.example.zlx.mybase.SystemUtil;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;

import static android.content.Context.WIFI_SERVICE;

public class HardwareInfo {
    private Context context;
    public static String hardwarePath = MyPath.join(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "hardware.json");
    public static String FAKE_CPUINFO = MyPath.join(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath(), "cpuinfo");
    public static JSONObject SharedPref    = null;
    public static String deviceinfo    = "";
    public static String softtype      = "";

    public static int heightPixels               = 0;//note. 长度:  1280
    public static int widthPixels                = 0;//note. 宽度:  760
    public static String IMEI              = null;
    public static String DISPLAY      = null;
    public static String PRODUCT      = null;//note 产品名: msm8974
    public static String DEVICE       = null;
    public static String BOARD        = null;
    public static String MANUFACTURER = null;//note 制造商: samsung
    public static String BRAND        = null;//note 品牌: samsung
    public static String MODEL        = null;//note 型号: SM-G7109
    public static String buildBootloader   = null;
    public static String RADIO        = null;
    public static String HARDWARE     = null;
    public static String SERIAL       = null;
    public static String buildType         = null;
    public static String buildUser         = null;
    public static String buildHost         = null;
    public static String buildTags         = null;
    public static String CPU_ABI       = null;
    public static String CPU_ABI2      = null;
    public static String RadioVersion      = null;
    public static String FINGERPRINT  = null;//note 指纹: samsung/msm8974/msm8974:4.4.2/JLS36C/3.8.017.0719:user/release-keys
    public static String INCREMENTAL = null;
    public static String RELEASE     = null;//note 系统版本: 4.4.2
    public static String SDK_INT      = null;
    public static String CODENAME    = null;
    public static String SDK         = null;
    public static String android_id = null;//note android_id: 2fcaa14289e69914
    public static String deviceId              = null;
    public static String telephonyGetDeviceSoftwareVersion = null;
    public static String telephonyGetLine1Number           = null;
    public static String NetworkCountryIso     = null;
    public static String NetworkOperator       = null;
    public static String NetworkOperatorName   = null;
    public static String NetworkType           = null;
    public static String PhoneType             = null;
    public static String SimCountryIso         = null;
    public static String SimOperator           = null;
    public static String SimOperatorName       = null;
    public static String SimSerialNumber       = null;
    public static String SimState              = null;
    public static String IMSI          = null;
    public static String Line1Number          = null;
    public static String webUserAgent = null;
    public static String SSID       = null;
    public static String gateway               = null;
    public static String BSSID      = null;
    public static String MacAddress = null;
    public static String wifiInfoGetNetworkId  = null;
    public static String wifiInfoGetIpAddress  = null;
    public static String displayDip = null;
    public static String cpuinfo  = null;//note CPU型号: Hardware : Qualcomm MSM 8974 HAMMERHEAD (Flattened Device Tree), 把前面的Hardware:都截断
    public static String systemLanguage = null;
    public static boolean isSave = false;

    public HardwareInfo(Context context){
        this.context = context;
        this.init_hardware_info();
    }

    public static void debug() throws Exception{
        HardwareInfo.SharedPref = new JSONObject();
        HardwareInfo.SharedPref.put("IMEI", "860236515735529");// #1 序列号, 15个字符. note: work  "012345678912345"
        HardwareInfo.SharedPref.put("android_id", "1038042550552821");// #2 android_id, note: work  "android_id"
        HardwareInfo.SharedPref.put("Line1Number", "+8617620551725"); // #3 手机号码, note: work   "18925196056"
        HardwareInfo.SharedPref.put("SimSerialNumber", "89860028229592576800");// #4 手机卡序列号  note: work  "SimSerialNumber"
        HardwareInfo.SharedPref.put("IMSI", "460002853273123");// #5 IMSI, 15个字符. note: work    "123456789012345"
        HardwareInfo.SharedPref.put("SimCountryIso", "cn");// #6 手机卡国家. note: work    "simCountryIso"
        HardwareInfo.SharedPref.put("SimOperator", "46000"); // #7 运营商. note: work    "SimOperator"
        HardwareInfo.SharedPref.put("SimOperatorName", "China Mobile GSM");// #8 运营商名字, note work:   "中国联通"
        HardwareInfo.SharedPref.put("NetworkCountryIso", "");// #9 国家iso代码, note work:   "NetworkCountryIso"
        HardwareInfo.SharedPref.put("NetworkOperator", "46000"); // #10 网络运营商类型(网络类型), note work:   "NetworkOperator"
        HardwareInfo.SharedPref.put("NetworkOperatorName", "China Mobile GSM");// #11 网络类型名, note work:   "NetworkOperatorName"
        HardwareInfo.SharedPref.put("NetworkType", 3);// #12 网络类型(注意和10不一样), note work 6
        HardwareInfo.SharedPref.put("PhoneType", 1); // #13 手机类型  note work 5
        HardwareInfo.SharedPref.put("SimState", 5); // #14 手机卡状态  note work 10
        HardwareInfo.SharedPref.put("MacAddress", "08:00:27:d7:89:02"); // #15 mac地址    note work   "a8:a6:68:a3:d9:ef"
        HardwareInfo.SharedPref.put("SSID", "\"MEmuWiFi\""); // #16 无线路由器名     note work   "免费WIFI"
        HardwareInfo.SharedPref.put("BSSID", "01:80:c2:00:00:03"); // #17 无线路由器地址       note work   "ce:ea:8c:1a:5c:b2"
        HardwareInfo.SharedPref.put("RELEASE", "4.4.4");// # 18 系统版本 note work "4.4.4"
        HardwareInfo.SharedPref.put("SDK", "19"); // #19 系统版本值 note: work  "18"
        HardwareInfo.SharedPref.put("CPU_ABI", "x86"); //  设备指令集名称 1  // 20 系统架构 note work "armeabi-v7a"  两个abi之间使用'_'连接
        HardwareInfo.SharedPref.put("CPU_ABI2", "armeabi-v7a"); //   设备指令集名称 2  // 20 系统架构 note work "armeabi"
        HardwareInfo.SharedPref.put("widthPixels", "720");// # 21 屏幕分辨率, 宽X高 note: work  "200"
        HardwareInfo.SharedPref.put("heightPixels", "1280");// # 21 屏幕分辨率, 宽X高    note: work "100"
        HardwareInfo.SharedPref.put("RadioVersion", ""); // 22 固件版本     note  work      "REL"
        HardwareInfo.SharedPref.put("BRAND", "OnePlus");// # 23 品牌    note work "BRAND"
        HardwareInfo.SharedPref.put("MODEL", "ONEPLUS A3010");// # 24 型号    note work "MODEL"
        HardwareInfo.SharedPref.put("PRODUCT", "ONEPLUS A3010");// # 25 产品名    note work "PRODUCT"
        HardwareInfo.SharedPref.put("MANUFACTURER", "OnePlus");// # 26 制造商    note work "manufacture"
        // find / -name cpuinfo | xargs grep Hard
        // note. 模拟器读取cpu信息, 来自文件: /system/lib/arm/cpuinfo
        HardwareInfo.SharedPref.put("cpuinfo", "Processor\t: ARMv7 processor rev 1 (v7l)\nBogoMIPS\t: 1500.0\nFeatures\t: neon vfp swp half thumb fastmult edsp vfpv3 \nCPU implementer\t: 0x69\nCPU architecture: 7\nCPU variant\t: 0x1\nCPU part\t: 0x001\nCPU revision\t: 1\n\nHardware\t: placeholder\nRevision\t: 0001\nSerial\t\t: 0000000000000001\n");// # 26 Cpu     note work
        HardwareInfo.SharedPref.put("HARDWARE", "intel"); //# 27 硬件   note work "HARDWARE"
        HardwareInfo.SharedPref.put("FINGERPRINT", "OnePlus/OnePlus/ONEPLUS A3010:4.4.4/KTU84P/userdebug/release-keys");// # 28 指纹     note work "FINGERPRINT"
        HardwareInfo.SharedPref.put("DISPLAY", "OnePlus-userdebug 4.4.4 KTU84P release-keys");// # 自加   note work "DISPLAY"
        HardwareInfo.SharedPref.put("INCREMENTAL", "eng..20171207.192026");// # 自加    note work "INCREMENTAL"
        HardwareInfo.SharedPref.put("SERIAL", "15738532");// # 自加    note work "SERIAL"
    }
    public static void replace(final ClassLoader classLoader) {
        new XBuild(classLoader);
        new XDisplay(classLoader);  // 屏幕
        new XPhone(classLoader);      // Sim卡
        new XCpu(classLoader);      // CPU
    }

    public void init_hardware_info() {
        try {
            XLog.d("Hardware info()");
            // note. 获取系统长宽. 来源: http://blog.csdn.net/Miehalu/article/details/52217345
            DisplayMetrics app_metrics = new DisplayMetrics();
            WindowManager manager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
            manager.getDefaultDisplay().getMetrics(app_metrics);
            XLog.d("app heightPixels:%d, widthPixels:%d", app_metrics.heightPixels, app_metrics.widthPixels);
            DisplayMetrics system_metrics = this.context.getResources().getDisplayMetrics();
            HardwareInfo.heightPixels = system_metrics.heightPixels;
            HardwareInfo.widthPixels = system_metrics.widthPixels;
            XLog.d("system heightPixels:%d, widthPixels:%d", HardwareInfo.heightPixels, HardwareInfo.widthPixels);

            HardwareInfo.DISPLAY = Build.DISPLAY;
            XLog.d("DISPLAY:%s", HardwareInfo.DISPLAY);

            HardwareInfo.PRODUCT = Build.PRODUCT;
            XLog.d("PRODUCT:%s", HardwareInfo.PRODUCT);

            HardwareInfo.DEVICE = Build.DEVICE;
            XLog.d("DEVICE:%s", HardwareInfo.DEVICE);
            HardwareInfo.BOARD = Build.BOARD;
            XLog.d("BOARD:%s", HardwareInfo.BOARD);
            HardwareInfo.MANUFACTURER = Build.MANUFACTURER;
            XLog.d("MANUFACTURER:%s", HardwareInfo.MANUFACTURER);
            HardwareInfo.BRAND = Build.BRAND;
            XLog.d("BRAND:%s", HardwareInfo.BRAND);
            HardwareInfo.MODEL = Build.MODEL;
            XLog.d("MODEL:%s", HardwareInfo.MODEL);
            HardwareInfo.buildBootloader = Build.BOOTLOADER;
            XLog.d("buildBootloader:%s", HardwareInfo.buildBootloader);
            HardwareInfo.RADIO = Build.RADIO;
            XLog.d("RADIO:%s", HardwareInfo.RADIO);
            HardwareInfo.HARDWARE = Build.HARDWARE;
            XLog.d("HARDWARE:%s", HardwareInfo.HARDWARE);
            HardwareInfo.SERIAL = Build.SERIAL;
            XLog.d("SERIAL:%s", HardwareInfo.SERIAL);
            HardwareInfo.FINGERPRINT = Build.FINGERPRINT;
            XLog.d("FINGERPRINT:%s", HardwareInfo.FINGERPRINT);

            HardwareInfo.INCREMENTAL = Build.VERSION.INCREMENTAL;
            XLog.d("INCREMENTAL:%s", HardwareInfo.INCREMENTAL);
            HardwareInfo.RELEASE = Build.VERSION.RELEASE;
            XLog.d("RELEASE:%s", HardwareInfo.RELEASE);

            HardwareInfo.SDK = Build.VERSION.SDK;
            XLog.d("SDK:%s", HardwareInfo.SDK);
            HardwareInfo.CODENAME = Build.VERSION.CODENAME;
            XLog.d("CODENAME:%s", HardwareInfo.CODENAME);
            HardwareInfo.SDK_INT = String.valueOf(Build.VERSION.SDK_INT);
            XLog.d("SDK_INT:%s", HardwareInfo.SDK_INT);

            HardwareInfo.CPU_ABI = Build.CPU_ABI;
            XLog.d("CPU_ABI:%s", HardwareInfo.CPU_ABI);
            HardwareInfo.CPU_ABI2 = Build.CPU_ABI2;
            XLog.d("CPU_ABI2:%s", HardwareInfo.CPU_ABI2);

            HardwareInfo.RadioVersion = Build.getRadioVersion();
            XLog.d("RadioVersion:%s", HardwareInfo.RadioVersion);

            TelephonyManager telephonyManager = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
            WifiInfo wifiInfo = ((WifiManager) this.context.getSystemService(Context.WIFI_SERVICE)).getConnectionInfo();
            // if ("SM-G900F".equals(systemModel) || "GT-I9500".equals(systemModel)) {
            //ContextWrapper contextwrapper = new ContextWrapper(WechatClass.wechatContext);
            WifiManager wifiManager = (WifiManager) this.context.getSystemService(WIFI_SERVICE);  // note. 注意添加权限:   <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            long lGateway = dhcpInfo.gateway;
            HardwareInfo.gateway = SystemUtil.long2ip(lGateway);//网关地址
            XLog.d("gateway:%s", HardwareInfo.gateway);

            HardwareInfo.IMEI = telephonyManager.getDeviceId();
            XLog.d("IMEI:%s", HardwareInfo.IMEI);
            HardwareInfo.deviceId = telephonyManager.getDeviceId();
            XLog.d("deviceId:%s", HardwareInfo.deviceId);

            HardwareInfo.telephonyGetDeviceSoftwareVersion = telephonyManager.getDeviceSoftwareVersion();
            XLog.d("telephonyGetDeviceSoftwareVersion:%s", HardwareInfo.telephonyGetDeviceSoftwareVersion);
            HardwareInfo.telephonyGetLine1Number = telephonyManager.getLine1Number();
            XLog.d("telephonyGetLine1Number:%s", HardwareInfo.telephonyGetLine1Number);
            HardwareInfo.NetworkCountryIso = telephonyManager.getNetworkCountryIso();
            XLog.d("NetworkCountryIso:%s", HardwareInfo.NetworkCountryIso);

            HardwareInfo.NetworkOperator = telephonyManager.getNetworkOperator();
            XLog.d("NetworkOperator:%s", HardwareInfo.NetworkOperator);
            HardwareInfo.NetworkOperatorName = telephonyManager.getNetworkOperatorName();
            XLog.d("NetworkOperatorName:%s", HardwareInfo.NetworkOperatorName);
            HardwareInfo.SimCountryIso = telephonyManager.getSimCountryIso();
            XLog.d("SimCountryIso:%s", HardwareInfo.SimCountryIso);
            HardwareInfo.SimOperator = telephonyManager.getSimOperator();
            XLog.d("SimOperator:%s", HardwareInfo.SimOperator);
            HardwareInfo.SimOperatorName = telephonyManager.getSimOperatorName();
            XLog.d("SimOperatorName:%s", HardwareInfo.SimOperatorName);
            HardwareInfo.SimSerialNumber = telephonyManager.getSimSerialNumber();
            XLog.d("SimSerialNumber:%s", HardwareInfo.SimSerialNumber);
            HardwareInfo.IMSI = telephonyManager.getSubscriberId();
            XLog.d("IMSI:%s", HardwareInfo.IMSI);
            HardwareInfo.PhoneType = String.valueOf(telephonyManager.getPhoneType());
            XLog.d("PhoneType:%s", HardwareInfo.PhoneType);
            HardwareInfo.NetworkType = String.valueOf(telephonyManager.getNetworkType());
            XLog.d("NetworkType:%s", HardwareInfo.NetworkType);
            HardwareInfo.SimState = String.valueOf(telephonyManager.getSimState());
            XLog.d("SimState:%s", HardwareInfo.SimState);

            HardwareInfo.Line1Number = telephonyManager.getLine1Number();
            XLog.d("Line1Number:%s", HardwareInfo.Line1Number);

            HardwareInfo.SSID = wifiInfo.getSSID();
            XLog.d("SSID:%s", HardwareInfo.SSID);
            HardwareInfo.BSSID = wifiInfo.getBSSID();
            XLog.d("BSSID:%s", HardwareInfo.BSSID);
            HardwareInfo.MacAddress = wifiInfo.getMacAddress();
            XLog.d("MacAddress:%s", HardwareInfo.MacAddress);
            HardwareInfo.wifiInfoGetNetworkId = String.valueOf(wifiInfo.getNetworkId());
            XLog.d("wifiInfoGetNetworkId:%s", HardwareInfo.wifiInfoGetNetworkId);
//            HardwareInfo.wifiInfoGetIpAddress = OtherHelper.getInstance().intToIp(wifiInfo.getIpAddress());

            HardwareInfo.android_id = Settings.Secure.getString(this.context.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);  //  "android_id"
            XLog.d("android_id:%s", HardwareInfo.android_id);

            if( new File("/system/lib/arm/cpuinfo").exists() ){
                HardwareInfo.cpuinfo = FileUtils.readFileToString(new File("/system/lib/arm/cpuinfo"));
            } else {
                HardwareInfo.cpuinfo = FileUtils.readFileToString(new File("/proc/cpuinfo"));
            }

            XLog.d("cpuinfo:%s", HardwareInfo.cpuinfo);
        } catch (Exception e) {
            XLog.e("[while loop] %s", android.util.Log.getStackTraceString(e));
        }
    }
}

/**
 12-15 10:43:59.550 21822-21840/? D/zzzs: Hardware info()
 12-15 10:43:59.550 21822-21840/? D/zzzs: app heightPixels:1280, widthPixels:720
 12-15 10:43:59.550 21822-21840/? D/zzzs: system heightPixels:1280, widthPixels:720
 12-15 10:43:59.550 21822-21840/? D/zzzs: buildDisplay:OPPO
 12-15 10:43:59.550 21822-21840/? D/zzzs: PRODUCT:msm8974
 12-15 10:43:59.560 21822-21840/? D/zzzs: DEVICE:msm8974
 12-15 10:43:59.560 21822-21840/? D/zzzs: BOARD:MSM8974
 12-15 10:43:59.560 21822-21840/? D/zzzs: MANUFACTURER:samsung
 12-15 10:43:59.560 21822-21840/? D/zzzs: BRAND:samsung
 12-15 10:43:59.560 21822-21840/? D/zzzs: MODEL:SM-G7109
 12-15 10:43:59.560 21822-21840/? D/zzzs: buildBootloader:unknown
 12-15 10:43:59.560 21822-21840/? D/zzzs: RADIO:unknown
 12-15 10:43:59.560 21822-21840/? D/zzzs: HARDWARE:qcom
 12-15 10:43:59.560 21822-21840/? D/zzzs: SERIAL:14289e699142fcaa
 12-15 10:43:59.560 21822-21840/? D/zzzs: FINGERPRINT:samsung/msm8974/msm8974:4.4.2/JLS36C/3.8.017.0719:user/release-keys
 12-15 10:43:59.560 21822-21840/? D/zzzs: INCREMENTAL:3.8.017.0719
 12-15 10:43:59.560 21822-21840/? D/zzzs: RELEASE:4.4.2
 12-15 10:43:59.560 21822-21840/? D/zzzs: SDK:19
 12-15 10:43:59.560 21822-21840/? D/zzzs: CODENAME:REL
 12-15 10:43:59.560 21822-21840/? D/zzzs: SDK_INT:19
 12-15 10:43:59.570 21822-21840/? D/zzzs: gateway:172.17.100.2
 12-15 10:43:59.570 21822-21840/? D/zzzs: IMEI:352284049235603
 12-15 10:43:59.570 21822-21840/? D/zzzs: telephonyGetDeviceSoftwareVersion:null
 12-15 10:43:59.570 21822-21840/? D/zzzs: telephonyGetLine1Number:
 12-15 10:43:59.570 21822-21840/? D/zzzs: NetworkCountryIso:CN
 12-15 10:43:59.570 21822-21840/? D/zzzs: NetworkOperator:46007
 12-15 10:43:59.570 21822-21840/? D/zzzs: NetworkOperatorName:CHINA MOBILE
 12-15 10:43:59.570 21822-21840/? D/zzzs: SimCountryIso:CN
 12-15 10:43:59.570 21822-21840/? D/zzzs: SimOperator:46007
 12-15 10:43:59.570 21822-21840/? D/zzzs: SimOperatorName:CMCC
 12-15 10:43:59.570 21822-21840/? D/zzzs: SimSerialNumber:89860081012252170142
 12-15 10:43:59.570 21822-21840/? D/zzzs: IMSI:460072252170142
 12-15 10:43:59.570 21822-21840/? D/zzzs: PhoneType:1
 12-15 10:43:59.570 21822-21840/? D/zzzs: NetworkType:3
 12-15 10:43:59.570 21822-21840/? D/zzzs: SimState:5
 12-15 10:43:59.570 21822-21840/? D/zzzs: SSID:vivo X710L2fcaa14289e69914
 12-15 10:43:59.570 21822-21840/? D/zzzs: BSSID:FC:AA:14:28:9E:69
 12-15 10:43:59.570 21822-21840/? D/zzzs: MacAddress:FC:AA:14:28:9E:69
 12-15 10:43:59.570 21822-21840/? D/zzzs: wifiInfoGetNetworkId:0
 12-15 10:43:59.570 21822-21840/? D/zzzs: android_id:2fcaa14289e69914
 12-15 10:43:59.580 21822-21840/? D/zzzs: cpuinfo:Processor	: ARMv7 Processor rev 0 (v7l)
 processor	: 0
 BogoMIPS	: 38.40

 processor	: 1
 BogoMIPS	: 38.40

 processor	: 2
 BogoMIPS	: 38.40

 processor	: 3
 BogoMIPS	: 38.40

 Features	: swp half thumb fastmult vfp edsp neon vfpv3 tls vfpv4 idiva idivt
 CPU implementer	: 0x51
 CPU architecture: 7
 CPU variant	: 0x2
 CPU part	: 0x06f
 CPU revision	: 0

 Hardware	: Qualcomm MSM 8974 HAMMERHEAD (Flattened Device Tree)
 Revision	: 000b
 Serial		: 0000000000000000
 */