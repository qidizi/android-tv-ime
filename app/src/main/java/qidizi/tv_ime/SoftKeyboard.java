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
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SoftKeyboard extends InputMethodService {
    final static int PORT = 11111;
    public volatile boolean httpd_running = false;
    private String apk_path;

    @Override
    public void onCreate() {
        // 必须调用super
        super.onCreate();
        create_httpd();
        apk_path = getCacheDir().getAbsolutePath() + "/tmp.apk";
    }

    /**
     * 方法用于用户界面初始化，主要用于service运行过程中配置信息发生改变的情况（横竖屏转换等）。
     */
    @Override
    public void onInitializeInterface() {
        super.onInitializeInterface();

    }

    /**
     * 方法用于创建并返回（input area）输入区域的层次视图，该方法只被调用一次（输入区域第一次显示时），
     * 该方法可以返回null，此时输入法不存在输入区域，InputMethodService的默认方法实现返回值为空，
     * 想要改变已经创建的输入区域视图，我们可以调用setInputView(View)方法，想要控制何时显示输入视图，
     * 我们可以实现onEvaluateInputViewShown方法，该方法用来判断输入区域是否应该显示，
     * 在updateInputViewShown方法中会调用onEvaluateInputViewShown方法来判断是否显示输入区域。
     *
     * @return View
     */
    @Override
    public View onCreateInputView() {
        return super.onCreateInputView();
    }


    private void create_httpd() {
        if (httpd_running) return;
        httpd_running = true;
        Thread thread = new Thread() {
            @Override
            public void run() {
                ServerSocket httpd;
                try {
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
                InputStreamReader isr = new InputStreamReader(client.getInputStream())
        ) {
            JSONObject response = new JSONObject();
            response.put("code", 200);
            response.put("msg", "已处理");
            response.put("time",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date())
            );
            try {
                StringBuilder builder = new StringBuilder();
                // GET HEAD POST OPTIONS PUT DELETE TRACE CONNECT
                char[] charBuf = new char[20];
                int mark;
                // 先快速读出部分
                mark = isr.read(charBuf);

                if (mark < 1) {
                    throw new Exception("httpd.accept数据为空");
                }

                builder.append(charBuf, 0, mark);
                String method = builder.toString().toLowerCase();

                if (method.startsWith("get /")) {
                    // 请求首页
                    // 特殊方式来判断是真机还是avd中
                    String host = method.startsWith("get /?avd=1") ?
                            "php.local.qidizi.com" : "www-public.qidizi.com";
                    client.getOutputStream().write((
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: text/html;charset=UTF-8\r\n"
                                    + "Access-Control-Allow-Origin: *\r\n"
                                    + "Access-Control-Allow-Headers: *\r\n"
                                    + "\r\n"
                                    + getResources().getText(R.string.index_html)
                                    .toString().replace("{host}", host)
                                    + "\r\n"
                    ).getBytes());
                    response = null;
                    return;
                } else if (method.startsWith("post /")) {
                    // 增加缓冲
                    charBuf = new char[1024];
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
                                    "\r\n" +
                                    // 暂放空
                                    " " +
                                    "\r\n"
                    ).getBytes());
                    response = null;
                    return;
                } else {
                    // 其它方法不支持; 暂不确定响应头就这样是否就ok
                    client.getOutputStream().write((
                            "HTTP/1.1 405 Method Not Allowed\r\n" +
                                    "\r\n" +
                                    // 暂放空
                                    " " +
                                    "\r\n"
                    ).getBytes());
                    response = null;
                    return;
                }

                // todo 当前使用base64方式来上传方式,将导致数据大小变成3倍,后续要优化
                while ((mark = isr.read(charBuf)) != -1) {
                    builder.append(charBuf, 0, mark);
                    if (mark < charBuf.length) {
                        break;
                    }
                }
                String body = builder.toString();
                body = body.substring(body.indexOf("\r\n\r\n") + 4).trim();
                if (body.isEmpty()) throw new Exception("post不能为空");
                JSONObject obj = new JSONObject(body);
                if (!obj.has("action")) throw new Exception("post的json.action缺失");

                switch (obj.getString("action")) {
                    case "send_text":
                        // 文字上屏
                        if (!obj.has("text")) throw new Exception("send_text 的 json.text 缺失");
                        send_text(obj.getString("text"));
                        break;
                    case "send_key":
                        // 发送按键事件
                        if (!obj.has("key")) throw new Exception("send_key 的 json.key 缺失");
                        send_key(obj.getString("key"));
                        break;
                    case "play_url":
                        // 播放远程视频
                        if (!obj.has("url")) throw new Exception("play_url 的 json.url 缺失");
                        play_url(obj.getString("url"));
                        break;
                    case "upload":
                        // 上传文件
                        if (!obj.has("name"))
                            throw new Exception("upload 的 json.name 缺失");
                        if (!obj.has("base64"))
                            throw new Exception("upload 的 json.base64 缺失");
                        String path = obj.getString("name");
                        boolean is_apk = path.toLowerCase().endsWith(".apk");

                        if (!is_apk) throw new Exception("目前只允许上传安卓应用文件(apk后缀)");
                        install_apk(obj.getString("base64"));
                        break;
                    default:
                        throw new Exception(obj.getString("action") + " action不支持");
                }
            } catch (Exception e) {
                response.put("code", 500);
                response.put("msg", e.getMessage());
            } finally {
                if (response != null)
                    client.getOutputStream().write((
                            "HTTP/1.1 200 OK\r\n"
                                    + "Content-Type: application/json;charset=utf-8\r\n"
                                    + "Access-Control-Allow-Origin: *\r\n"
                                    + "Access-Control-Allow-Headers: *\r\n"
                                    + "\r\n"
                                    + response.toString()
                                    + "\r\n"
                    ).getBytes());
            }
        } catch (Exception e) {
            toast("处理客户端请求失败:" + e.getMessage());
        }
    }

    /**
     * onStartInputView方法 输入视图正在显示并且编辑框输入已经开始的时候回调该方法，
     * onStartInputView方法总会在onStartInput方法之后被调用，普通的设置可以在onStartInput方法中进行，
     * 在onStartInputView方法中进行视图相关的设置，开发者应该保证onCreateInputView方法在该方法被调用之前调用。
     *
     * @param info       输入框的信息
     * @param restarting 是否重新启动
     */
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
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
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    String extension = MimeTypeMap.getFileExtensionFromUrl(url);
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    Intent mediaIntent = new Intent(Intent.ACTION_VIEW);
                    mediaIntent.setDataAndType(Uri.parse(url), mimeType);
                    me.startActivity(mediaIntent);
                } catch (Exception e) {
                    toast("播放视频出错:" + e.getMessage());
                }
            }
        });
    }

    // base64 的文件内容保存到文件
    private void base64_to_file(String base64, File file) throws Exception {
        if (base64 == null)
            throw new Exception("无法保存文件:内容为空");

        OutputStream out = new FileOutputStream(file);
        String sub = "base64,";
        base64 = base64.substring(base64.indexOf(sub) + sub.length());
        out.write(Base64.decode(base64, Base64.NO_WRAP));
        out.flush();
        out.close();
    }

    private void install_apk(String base64) throws Exception {
        File file = new File(apk_path);
        base64_to_file(
                base64,
                file
        );
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
}
