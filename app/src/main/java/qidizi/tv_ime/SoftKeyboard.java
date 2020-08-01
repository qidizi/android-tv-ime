/*
 * Copyright (C) 2008-2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package qidizi.tv_ime;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Enumeration;

public class SoftKeyboard extends InputMethodService {
    final static int PORT = 11111;
    public volatile boolean httpd_running = false;
    private String apk_path;
    final static String HOST_LOCAL = "php.local.qidizi.com";
    // avd网关(开发电脑)
    final static String HOST_DEV = "10.0.2.2";
    final static String HOST_REMOTE = "www-public.qidizi.com";

    @Override
    public void onCreate() {
        // 必须调用super
        super.onCreate();
        create_httpd();
        apk_path = getCacheDir().getAbsolutePath() + "/tmp.apk";
    }

    static boolean is_android_avd() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || "QC_Reference_Phone".equals(Build.BOARD)//bluestacks
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build") //MSI App Player
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT);
    }

    private void create_httpd() {
        if (httpd_running) return;
        final SoftKeyboard me = this;
        httpd_running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerSocket httpd;
                try {
                    // 使用端口转发功能,把虚拟机avd端口转发到开发机的11111上,就可以使用 http://127.0.0.1:11111 来访问
                    // android/platform-tools/adb forward tcp:11111 tcp:11111
                    httpd = new ServerSocket(PORT, 1);
                    httpd.setReuseAddress(false);
                    Log.d("qidizi_debug", "httpd 启动成功 " + httpd.isBound());
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("电视端启动失败:" + e.getMessage(), me);
                    httpd_running = false;
                    return;
                }

                toast("电视IP " + get_lan_ipv4(), me);
                //noinspection
                while (true) accept(httpd);
            }
        }).start();
    }

    private void accept(ServerSocket httpd) {
        try (
                Socket client = httpd.accept();
                InputStream is = client.getInputStream()
        ) {
            try {
                client.setSoTimeout(1000 * 5);
                // get 支持的长度
                byte[] bytes = new byte[1024];
                int len = is.read(bytes);
                if (-1 == len)
                    throw new Exception("http报文读取异常");

                // 找到 rn rn标志
                int rn_rn = 0, i = 0;

                do {
                    if (13 == bytes[i] || 10 == bytes[i])
                        rn_rn++;
                    else
                        rn_rn = 0;

                    if (4 == rn_rn) {
                        // 把游标移到到body首个byte
                        i++;
                        break;
                    }
                } while (i++ < len);

                if (0 == rn_rn)
                    throw new Exception("http报文缺失\\r\\n\\r\\n");
                int body_index = len - i;
                // 取header
                String str = new String(bytes, 0, i);

                if (str.startsWith("OPTIONS "))
                    // 获取是否允许跨域
                    throw new Exception("允许跨域");

                if (!str.startsWith("POST /?"))
                    throw new Exception("此操作未实现");

                UrlQuerySanitizer urlQuerySanitizer = new UrlQuerySanitizer();
                urlQuerySanitizer.setAllowUnregisteredParamaters(true);// 支持_划线
                urlQuerySanitizer.parseQuery(str.substring(str.indexOf("?") + 1, str.indexOf(" HTTP/")));
                str = "";
                String action = urlQuerySanitizer.getValue("_do");

                if (null == urlQuerySanitizer.getValue("_size"))
                    throw new Exception("queryString必须提供body大小参数_size");

                if (null == action)
                    throw new Exception("queryString必须提供操作参数_do");
                int body_size = Integer.parseInt(urlQuerySanitizer.getValue("_size"));

                if (body_size > 0) {
                    byte[] body = new byte[body_size];
                    // 复制多读出的 body
                    System.arraycopy(bytes, i, body, 0, body_index);
                    body_size -= body_index;
                    //noinspection UnusedAssignment
                    bytes = null;

                    if (body_size > 0) {
                        bytes = new byte[100];
                        // 只有没有读全时才需要继续读取
                        while (body_size > 0 && -1 != (len = is.read(bytes))) {
                            System.arraycopy(bytes, 0, body, body_index, len);
                            body_size -= len;
                            body_index += len;
                        }

                        //noinspection UnusedAssignment
                        bytes = null;
                    }

                    if ("send_file".equals(action)) {
                        install_apk(body);
                        httpd_response(client.getOutputStream(), "请按屏幕提示安装!");
                        return;
                    }

                    // 这个逻辑,可能属于重复 byte 转 str
                    str = new String(body);
                    //noinspection UnusedAssignment
                    body = null;
                }

                switch (action) {
                    case "send_text":
                        // 文字上屏
                        send_text(str);
                        httpd_response(client.getOutputStream(), "文字已发送");
                        break;
                    case "send_key":
                        // 发送按键事件
                        send_key(str);
                        httpd_response(client.getOutputStream(), "按键已发送");
                        break;
                    case "send_url":
                        // 播放远程视频
                        play_url(str, urlQuerySanitizer.getValue("seek"));
                        httpd_response(client.getOutputStream(), "操作已完成");
                        break;
                    default:
                        throw new Exception("该操作未实现");
                }
            } catch (Exception e) {
                e.printStackTrace();
                httpd_response(client.getOutputStream(), e.getMessage());
            }
        } catch (Exception e) {
            toast("建立socket失败:" + e.getMessage(), this);
            e.printStackTrace();
        }
    }

    private void install_apk(byte[] body) throws Exception {
        // 上传文件
        File file = new File(apk_path);

        try (OutputStream out = new FileOutputStream(file)) {
            out.write(body);
            //noinspection UnusedAssignment
            body = null;
            out.flush();
        } catch (Exception e) {
            throw new Exception("保存失败:" + e.getMessage());
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri apkUri;
        // 这个要放到前面,防止替换掉 intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 注意当前通过 file_paths.xml 授权cache目录下可分享,所以file必须属于该目录下文件
            apkUri = FileProvider.getUriForFile(this, "qidizi.tv_ime.provider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(file);
        }
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        startActivity(intent);
    }

    private void httpd_response(OutputStream os, String body) {
        try {
            if (null == body) body = "不明错误";
            os.write((
                    "HTTP/1.1 200 OK\r\n"
                            + "Allow: PUT, GET, POST, OPTIONS\r\n"
                            + "Content-Type: text/html;charset=utf-8\r\n"
                            + "Access-Control-Allow-Origin: *\r\n"
                            + "Access-Control-Allow-Headers: *\r\n"
                            + "Content-Length: " + body.getBytes().length + "\r\n"
                            + "\r\n"
                            + body
            ).getBytes());
        } catch (Exception e) {
            toast("应答失败:" + e.getMessage(), this);
        }
    }

    private void destroy_httpd() {
    }

    @Override
    public void onDestroy() {
        destroy_httpd();
        super.onDestroy();
    }

    private void send_key(final String key) {
        // 向当前绑定到键盘的应用发送键盘事件
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    int key_code = KeyEvent.keyCodeFromString(key);

                    switch (key_code) {
                        case KeyEvent.KEYCODE_BACK:
                            // 返回事件

                            break;
                        case KeyEvent.KEYCODE_HOME:
                            // 主页事件
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addCategory(Intent.CATEGORY_HOME);
                            startActivity(intent);
                            return;
                    }

                    if (KeyEvent.KEYCODE_UNKNOWN == key_code)
                        throw new Exception("按钮码无效");

                    me.sendDownUpKeyEvents(key_code);
                    // 用adb shell 的input keyevent 和 Instrumentation.sendKeyDownUpSync需要root权限
                    // 用输入法方式,需要建立 getCurrentInputConnection 才行;
                } catch (Exception e) {
                    toast("模拟按钮失败:" + e.getMessage(), me);
                }
            }
        });
    }

    static void toast(final String msg, final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                // new Toast(context) 出来的实例时间不爱控制
                Toast toast = Toast.makeText(context, msg, Toast.LENGTH_LONG);
                try {
                    ViewGroup group = (ViewGroup) toast.getView();
                    TextView text = (TextView) group.getChildAt(0);
                    text.setTextSize(30);
                    text.setSingleLine(false);
                    text.setMaxWidth(99999);
                    text.setPadding(5, 5, 5, 5);
                    // 因为只能设置长与短,其它时间需要自己维护,比如使用定时器来显示
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    text.setTextColor(Color.WHITE);
                    text.setBackgroundColor(Color.BLACK);
                } catch (Exception e) {
                    toast.setText(e.getMessage());
                }

                toast.show();
                Looper.loop();
            }
        }).start();
    }

    private void send_text(final String text) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // 发送的是字符串，非android支持的KEYCODE
                InputConnection ic = me.getCurrentInputConnection();
                if (ic == null) {
                    toast("无输入框", me);
                    return;
                }
                // 先删除整个输入框内容
                ic.deleteSurroundingText(100000, 100000);
                ic.commitText(text, 1);
                ic.closeConnection();
            }
        });
    }

    private void play_url(final String url, String seek) {
        final SoftKeyboard me = this;
        Intent intent = new Intent(me, MainActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("seek", seek);
        // 在当前堆中,只能有一个
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        me.startActivity(intent);
    }

    static String get_client_url() {
        return String.format(
                "http://%s/android-tv-ime/client",
                is_android_avd() ? HOST_LOCAL : HOST_REMOTE
        );
    }

    static String get_webview_url() {
        return String.format(
                "http://%s/android-tv-ime/client",
                is_android_avd() ? HOST_DEV : HOST_REMOTE
        );
    }

    static String get_lan_ipv4() {
        if (is_android_avd())
            return "127.0.0.1";

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface i_face = en.nextElement();
                // 如果是移动网络不要
                if (i_face.getName().toLowerCase().startsWith("radio0"))
                    continue;
                for (Enumeration<InetAddress> enumIpAddr = i_face
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    // for getting IPV4 format
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
