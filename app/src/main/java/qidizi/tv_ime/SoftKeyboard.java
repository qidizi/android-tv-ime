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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.MimeTypeMap;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

public class SoftKeyboard extends InputMethodService {
    public volatile boolean httpd_running = false;

    @Override
    public void onCreate() {
        // 必须调用super
        super.onCreate();
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

    private String get_lan_ipv4() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface
                    .getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface i_face = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = i_face
                        .getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    // for getting IPV4 format
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception ex) {
            toast("无法获取电视IP:" + ex.toString());
        }
        return null;
    }

    private void create_httpd() {
        if (httpd_running) return;
        httpd_running = true;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    ServerSocket httpd = new ServerSocket(
                            11111, 50, Inet4Address.getByName("0.0.0.0")
                    );

                    if (!httpd.isBound()) {
                        httpd_running = false;
                        return;
                    }
                    toast(String.format("电视IP %s", get_lan_ipv4()));
                    while (true) {
                        Socket client = null;
                        try {
                            client = httpd.accept();
                            StringBuilder builder = new StringBuilder();
                            InputStreamReader isr = new InputStreamReader(client.getInputStream());
                            char[] charBuf = new char[1024];
                            int mark;
                            while ((mark = isr.read(charBuf)) != -1) {
                                builder.append(charBuf, 0, mark);
                                if (mark < charBuf.length) {
                                    break;
                                }
                            }

                            String json = builder.toString();
                            toast(String.format("收到信息:\n[%s]", json));
                            json = json.substring(json.indexOf("\r\n\r\n") + 4);
                            toast(String.format("收到信息:\n[%s]", json));
                            JSONObject obj;
                            String msg = "";
                            int code = 500;

                            if (json.isEmpty()) {
                                msg = "post为空";
                            } else {
                                obj = new JSONObject(json);

                                if (!obj.has("action")) {
                                    msg = "post json 缺失 action 键";
                                } else {
                                    switch (obj.getString("action")) {
                                        case "commit_text":
                                            if (!obj.has("text")) {
                                                msg = "commit_text 需要 text键";
                                                break;
                                            }

                                            commit_text(obj.getString("text"));
                                            code = 200;
                                            msg = "处理完成";
                                            break;
                                        case "send_key":
                                            if (!obj.has("key")) {
                                                msg = "send_key 缺失 action 或 key 键";
                                                code = 500;
                                                break;
                                            }
                                            send_key_event(obj.getString("key"));
                                            code = 200;
                                            msg = "处理完成";
                                            break;
                                        case "play_media":
                                            if (!obj.has("url")) {
                                                msg = "play_media 缺失 url 键";
                                                code = 500;
                                                break;
                                            }
                                            play_media(obj.getString("url"));
                                            code = 200;
                                            msg = "处理完成";
                                            break;
                                        default:
                                            code = 500;
                                            msg = "action 无效";
                                    }
                                }
                            }

                            obj = new JSONObject();
                            obj.put("code", code);
                            obj.put("msg", msg);
                            obj.put("time",
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                            .format(new Date())
                            );
                            json = obj.toString();
                            client.getOutputStream()
                                    .write((
                                            "HTTP/1.1 200 OK\r\n"
                                                    + "Content-Type: application/json;charset=utf-8\r\n"
                                                    + "Access-Control-Allow-Origin: *\r\n"
                                                    + "Access-Control-Allow-Headers: *\r\n"
                                                    + "\r\n"
                                                    + json
                                                    + "\r\n"
                                    ).getBytes());
                        } catch (Exception e) {
                            toast("电视服务监听异常:" + e.getMessage());
                        } finally {
                            if (null != client) {
                                client.close();
                            }
                        }
                    }
                } catch (
                        Exception e) {
                    toast("电视服务启动异常:" + e.getMessage());
                }
            }
        };
        thread.start();

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
        create_httpd();
    }

    private void destroy_httpd() {
    }


    @Override
    public void onDestroy() {
        destroy_httpd();
        super.onDestroy();
    }

    private void send_key_event(String key) {
        // 向当前绑定到键盘的应用发送键盘事件
        InputConnection ic = getCurrentInputConnection();
        long down_time = SystemClock.uptimeMillis();
        long event_happen_time = SystemClock.uptimeMillis();
        // 可以组合，表示控制键是否按下了,meta_state是按bit运算，只有指定位为1才表示on
        // 这些常量是经过精心设计的，所有不会出现bit位冲突问题

        // 如

        // META_ALT_LEFT_ON bit 为 10000
        // META_ALT_ON bit 10
        // META_ALT_RIGHT_ON bit 100000
        // META_ALT_MASK bit 为110010

        // META_ALT_LEFT_ON | META_ALT_ON | META_ALT_RIGHT_ON == META_ALT_MASK
        // 如查询3个键状态时表示所有键都按下了，若只查询某个键即表示该键是按下状态

        // 如查询 右 alt是否按下，作 META_ALT_MASK & META_ALT_RIGHT_ON == META_ALT_RIGHT_ON
        // 如查询 左和右alt是否按下，作 META_ALT_MASK & META_ALT_RIGHT_ON ＆ META_ALT_LEFT_ON
        // == META_ALT_RIGHT_ON ＆ META_ALT_LEFT_ON

        // 其它以此类推
        // 来源：0 表示是来自虚拟设备（不是物理设备），其它值表示是物理设备如蓝牙耳机
        int device_id = 0;
        // 硬件层面处理码，如打印机对于1，不同字体对应不同打印字
        int scan_code = 0;
        int repeat = 0;
        // 表示长按时，定时自动发送按下事件序号，比如长按中共发送3次0、1、2，那么某些应用就可以选择只处理首次，其它忽视
        // 比如防止用户放开时手慢本意按一次却变长按发送n次按下事件而执行多次操作
//        if (KeyEvent.ACTION_DOWN != action) {
//        }
        // 控制键像alt、shift、ctrl是否按下了
        // if (meta_state < 0) meta_state = 0;
        // 击键来自虚拟键盘
        int flags = KeyEvent.FLAG_SOFT_KEYBOARD;
        // 表示击键来自键盘，还有屏幕触摸，游戏柄之类
        int source = InputDevice.SOURCE_KEYBOARD;
        int key_code = KeyEvent.keyCodeFromString(key);
        int meta_state = 0;
        KeyEvent ke = new KeyEvent(
                down_time,
                event_happen_time,
                KeyEvent.ACTION_DOWN,
                key_code,
                // 像按下后，第几次自动发送的按下事件，比如给某些长按下只当一次处理的程序使用，那么它就可以只使用0这个后面都忽视
                // 还有就是多个按键事件的事件数
                repeat,
                meta_state,
                device_id,
                scan_code,
                flags,
                source
        );
        ic.sendKeyEvent(ke);
        ke = new KeyEvent(
                down_time,
                event_happen_time,
                KeyEvent.ACTION_UP,
                key_code,
                // 像按下后，第几次自动发送的按下事件，比如给某些长按下只当一次处理的程序使用，那么它就可以只使用0这个后面都忽视
                // 还有就是多个按键事件的事件数
                repeat,
                meta_state,
                device_id,
                scan_code,
                flags,
                source
        );
        ic.sendKeyEvent(ke);
    }

    private boolean have_permission(String permission, String name) {
        // 检测是否拥有权限
        if (
                PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(
                        this, permission
                )
        ) {
            // 因为处于service中，无法获得activity，就不能使用
            // ActivityCompat.shouldShowRequestPermissionRationale 及 ActivityCompat.requestPermissions
            tip("请授予" + name + "权限后再试");
            // 后台服务启动startActivity限制
            // https://developer.android.com/guide/components/activities/background-starts
            return false;
        }

        // 获得授权
        return true;
    }

    void tip(String msg) {
        NotificationManager n_man = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);

        if (null == n_man) {
            toast(msg);
            return;
        }

        String channel_id = "qidizi";
        CharSequence channel_name = "qidizi";
        String channel_desc = "qidizi app";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // 这个版本需要创建通知的通道:高版本提供给用户可以关闭指定通道（分类）通知的能力，而不是全部应用的
            NotificationChannel channel = new NotificationChannel(
                    channel_id, channel_name, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(channel_desc);
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            // 不在应用图标上显示红点
            channel.setShowBadge(false);
            n_man.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channel_id)
                .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                .setContentTitle("提示")
                .setContentText(msg);
        // 点击通知唤起主界面
        Intent intent = new Intent(this, MainActivity.class);
        // 创建状态栏的消息
        TaskStackBuilder task_builder = TaskStackBuilder.create(this);
        task_builder.addParentStack(MainActivity.class);
        task_builder.addNextIntent(intent);
        PendingIntent pending_intent = task_builder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        //  关联内容点击唤起那个界面
        builder.setContentIntent(pending_intent);
        // 相同的id，未消失的旧通知将会被替换
        int notify_id = (int) System.currentTimeMillis() / 1000;
        n_man.notify(notify_id, builder.build());


    }

    void toast(final String msg) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(
                        me, msg,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void commit_text(final String text) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // 发送的是字符串，非android支持的KEYCODE
                InputConnection ic = me.getCurrentInputConnection();
                if (ic == null) return;
                ic.beginBatchEdit();// 提示输入框，只有收到end事件才代表本次输入结束
                // 指针移动到最后,1表示插入字符并移动到最后
                ic.commitText(text, 1);
                ic.endBatchEdit();
            }
        });
    }

    private void play_media(final String url) {
        final SoftKeyboard me = this;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    String extension = MimeTypeMap.getFileExtensionFromUrl(url);
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    Intent mediaIntent = new Intent(Intent.ACTION_VIEW);
                    mediaIntent.setDataAndType(Uri.parse(url), mimeType);
                    toast(mimeType);
                    startActivity(mediaIntent);
                } catch (Exception e) {
                    toast("播放视频出错:" + e.getMessage());
                }
            }
        });
    }
}
