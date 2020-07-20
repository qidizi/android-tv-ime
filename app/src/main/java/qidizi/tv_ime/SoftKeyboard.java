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

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class SoftKeyboard extends InputMethodService {
    final static int PORT = 11111;
    public volatile boolean httpd_running = false;
    private String apk_path;
    private boolean msg_i = false;
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
        httpd_running = true;
        Thread thread = new Thread() {
            @Override
            public void run() {
                ServerSocket httpd;
                try {
                    // 使用端口转发功能,把虚拟机avd端口转发到开发机的11111上,就可以使用 http://127.0.0.1:11111 来访问
                    // android/platform-tools/adb forward tcp:11111 tcp:11111
                    httpd = new ServerSocket(PORT);
                } catch (Exception e) {
                    toast("电视端启动失败:" + e.getMessage());
                    return;
                }

                if (!httpd.isBound()) {
                    //  todo 需要考虑如何再次尝试启动
                    httpd_running = false;
                    return;
                }

                toast("请打开【" + getResources().getString(R.string.app_name) + "】应用扫码控制电视");
                //noinspection InfiniteLoopStatement
                while (true) httpd_accept(httpd);
            }
        };
        thread.start();
    }

    private void httpd_accept(ServerSocket httpd) {
        try (
                Socket client = httpd.accept();
                DataInputStream input = new DataInputStream(client.getInputStream())
        ) {
            JSONObject response = new JSONObject();
            response.put("code", 200);
            response.put("msg", "已处理");
            try {
                // GET HEAD POST OPTIONS PUT DELETE TRACE CONNECT
                // 不能设置太大,防止取到body数据
                byte[] bytes = new byte[20];
//                //不能用,有时可能过慢导致
//                if (input.available() < 1)
//                    throw new Exception("httpd.accept数据不可用");

                if (input.read(bytes) < 1)
                    throw new Exception("httpd.accept读取首行失败");

                // 先快速读出部分
                boolean is_file;
                String method = new String(bytes).toLowerCase();

                if (method.startsWith("get /")) {
                    // 请求首页
                    String body = String.format(
                            "<script src='%s/html.js?r=%d'></script>",
                            get_client_url(), System.currentTimeMillis()
                    );
                    client.getOutputStream().write((
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: text/html;charset=UTF-8\r\n"
                                    + "Access-Control-Allow-Origin: *\r\n"
                                    + "Access-Control-Allow-Headers: *\r\n"
                                    + "Connection: keep-alive\r\n"
                                    + "Content-Length: " + body_length(body) + "\r\n"
                                    + "\r\n"
                                    + body
                    ).getBytes());
                    response = null;
                    return;
                } else if (method.startsWith("post /?file=1&")) {
                    is_file = true;
                } else if (method.startsWith("post /")) {
                    is_file = false;
                } else if (method.startsWith("options ")) {
                    // 浏览器查询是否支持某些方法,或是否允许跨域
                    client.getOutputStream().write((
                            "HTTP/1.1 204 No Content\r\n" +
                                    // 表示支持方法
                                    "Allow: GET, POST, OPTIONS\r\n" +
                                    // 允许跨域
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Access-Control-Allow-Methods: *\r\n" +
                                    "Access-Control-Allow-Headers: *\r\n" +
                                    "Connection: keep-alive\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "\r\n"
                    ).getBytes());
                    response = null;
                    return;
                } else {
                    // 其它方法不支持; 暂不确定响应头就这样是否就ok
                    client.getOutputStream().write((
                            "HTTP/1.1 405 Method Not Allowed\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: keep-alive\r\n" +
                                    "\r\n"
                    ).getBytes());
                    response = null;
                    return;
                }

                if (input.available() < 1)
                    throw new Exception("httpd.accept非法http的request headers");

                int mark;
                // \r == 13 \n === 10
                int rn_rn = 0;
                // 消耗到 \r\n\r\n;就可以只取body了
                while (input.available() > 0 && (mark = input.read()) != -1) {
                    if (13 == mark) {
                        // \r
                        if (0 == rn_rn) {
                            rn_rn = 1;
                            continue;
                        }

                        if (11 == rn_rn) {
                            rn_rn = 111;
                            continue;
                        }
                    } else if (10 == mark) {
                        // \n
                        if (1 == rn_rn) {
                            rn_rn = 11;
                            continue;
                        }

                        if (111 == rn_rn) {
                            // 找到\r\n\r\n
                            rn_rn = 1111;
                            break;
                        }
                    }
                    // 重置
                    rn_rn = 0;
                }
                // 消耗掉head头,剩下的就是body了

                if (1111 != rn_rn)
                    throw new Exception("http请求报文缺失\\r\\n\\r\\n");


                if (is_file) {
                    // 上传文件
                    install_apk(input);
                    response.put("msg", get_msg("已上传,请注意根据屏幕提示操作"));
                    return;
                }

                if (input.available() < 1)
                    throw new Exception("httpd.accept非法http的request body为空");

                // 最多支持*k数据
                bytes = new byte[input.available()];
                input.readFully(bytes);
                String body = new String(bytes, StandardCharsets.UTF_8);
                // post json
                JSONObject obj = new JSONObject(body);
                if (!obj.has("action")) throw new Exception("post的json.action缺失");

                switch (obj.getString("action")) {
                    case "send_text":
                        // 文字上屏
                        if (!obj.has("text")) throw new Exception("send_text 的 json.text 缺失");
                        send_text(obj.getString("text"));
                        response.put("msg", get_msg("已发送"));
                        break;
                    case "send_key":
                        // 发送按键事件
                        if (!obj.has("key")) throw new Exception("send_key 的 json.key 缺失");
                        send_key(obj.getString("key"));
                        response.put("msg", get_msg("已发送"));
                        break;
                    case "play_url":
                        // 播放远程视频
                        if (!obj.has("url")) throw new Exception("play_url 的 json.url 缺失");
                        play_url(obj.getString("url"));
                        response.put("msg", get_msg("已请求"));
                        break;
                    default:
                        throw new Exception(obj.getString("action") + " action不支持");
                }
            } catch (Exception e) {
                response.put("code", 500);
                response.put("msg", e.getMessage());
            } finally {
                if (response != null) {
                    String body = response.toString();
                    client.getOutputStream().write((
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: application/json;charset=utf-8\r\n"
                                    + "Access-Control-Allow-Origin: *\r\n"
                                    + "Access-Control-Allow-Headers: *\r\n"
                                    + "Content-Length: " + body_length(body) + "\r\n"
                                    + "Connection: keep-alive\r\n"
                                    + "\r\n"
                                    + body
                    ).getBytes());
                }
            }
        } catch (Exception e) {
            toast("处理客户端请求失败:" + e.getMessage());
        }
    }

    private int body_length(String body) {
        return body.getBytes().length;
    }

//    /**
//     * onStartInputView方法 输入视图正在显示并且编辑框输入已经开始的时候回调该方法，
//     * onStartInputView方法总会在onStartInput方法之后被调用，普通的设置可以在onStartInput方法中进行，
//     * 在onStartInputView方法中进行视图相关的设置，开发者应该保证onCreateInputView方法在该方法被调用之前调用。
//     *
//     * @param info       输入框的信息
//     * @param restarting 是否重新启动
//     */
//    @Override
//    public void onStartInputView(EditorInfo info, boolean restarting) {
//        super.onStartInputView(info, restarting);
//    }

    private void destroy_httpd() {
    }

    private String get_msg(String txt) {
        msg_i = !msg_i;
        return txt + "<sup>" + (msg_i ? 1 : 0) + "</sup>";
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
                    toast("模拟按钮失败:" + e.getMessage());
                }
            }
        });
    }

    void toast(final String msg) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(
                        me, msg,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void send_text(final String text) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // 发送的是字符串，非android支持的KEYCODE
                InputConnection ic = me.getCurrentInputConnection();
                if (ic == null) {
                    toast("无输入框");
                    return;
                }
                // 先删除整个输入框内容
                ic.deleteSurroundingText(100000, 100000);
                ic.commitText(text, 1);
                ic.closeConnection();
            }
        });
    }

    private void play_url(final String url) {
        final SoftKeyboard me = this;
        Intent intent = new Intent(me, MainActivity.class);
        intent.putExtra("url", url);
        // 在当前堆中,只能有一个
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        me.startActivity(intent);
    }


    private void install_apk(DataInputStream input) throws Exception {
        File file = new File(apk_path);
        OutputStream out = new FileOutputStream(file);
        int byte_len = 1024;
        byte[] bytes = new byte[byte_len];
        int len;

        while ((len = input.read(bytes)) > -1) {
            out.write(bytes, 0, len);

            // 读完
            if (len < byte_len)
                break;
        }

        out.flush();
        out.close();
        toast("上传成功,准备安装...");
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
