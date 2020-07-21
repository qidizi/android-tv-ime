package qidizi.tv_ime;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class MainActivity extends AppCompatActivity {
    WebView webView = null;
    SimpleExoPlayer simpleExoPlayer = null;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        open_url(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 唤醒键盘服务
        Intent intent = new Intent(this, SoftKeyboard.class);
        startService(intent);
        String url = getIntent().getStringExtra("url");

        if (null == url) {
            create_qr();
            return;
        }

        open_url(getIntent());
    }

    private void play_url(String url) {
        destroy_webview();

        if (null == simpleExoPlayer) {
            simpleExoPlayer = new SimpleExoPlayer.Builder(this).build();
            PlayerView playerView = new PlayerView(this);
            playerView.setPlayer(simpleExoPlayer);
            simpleExoPlayer.setVolume(1);
            simpleExoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerError(@NonNull ExoPlaybackException error) {
                    toast("视频加载失败:\n" + error.getMessage());
                }
            });
            simpleExoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
            setContentView(playerView);
        }

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, getResources().getString(R.string.app_name)));
        MediaSource videoSource;
        Uri uri = Uri.parse(url);
        String extension;
        extension = uri.getQueryParameter("ext");

        if (null == extension) {
            toast("请指定后缀ext参数");
            return;
        }

        @C.ContentType int type = Util.inferContentType(uri, extension);
        switch (type) {
            case C.TYPE_DASH:
                videoSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_SS:
                videoSource = new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_HLS:
                videoSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            case C.TYPE_OTHER:
                videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
                break;
            default:
                if ("rtmp".equals(extension)) {
                    videoSource = new ProgressiveMediaSource.Factory(new RtmpDataSourceFactory()).createMediaSource(uri);
                    break;
                }

                toast("Unsupported type: " + type);
                return;
        }

        simpleExoPlayer.prepare(videoSource);
        // 自动播放
        simpleExoPlayer.setPlayWhenReady(true);
    }

    private void create_qr() {
        try {
            BitMatrix result = new QRCodeWriter().encode(
                    String.format("http://%s:%s/",
                            SoftKeyboard.get_lan_ipv4(),
                            SoftKeyboard.PORT), BarcodeFormat.QR_CODE, 300, 300);
            Bitmap bitMap = Bitmap.createBitmap(result.getWidth(), result.getHeight(), Bitmap.Config.ARGB_8888);

            for (int y = 0; y < result.getHeight(); y++) {
                for (int x = 0; x < result.getWidth(); x++) {
                    if (result.get(x, y)) {
                        bitMap.setPixel(x, y, Color.BLACK);
                    }
                }
            }

            TextView textView = new TextView(this);
            textView.setBackgroundColor(Color.WHITE);
            textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            textView.setTextColor(Color.BLUE);
            textView.setTextSize(30);
            textView.setGravity(Gravity.CENTER);
            ImageSpan imageSpan = new ImageSpan(this, bitMap, ImageSpan.ALIGN_CENTER);
            SpannableString spannableString = new SpannableString(" \n");
            spannableString.setSpan(imageSpan, 0, 1, SpannableString.SPAN_INCLUSIVE_INCLUSIVE);
            textView.append(spannableString);
            textView.append("微信扫码 遥控电视");
            setContentView(textView);
        } catch (Exception e) {
            toast("创建二维码失败:" + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 界面不可见时,停止刷新2维码
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 界面可见时,开始定时刷新2维码
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                // 正在播放就暂停
                player_toggle_pause();
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 快退
                player_seek(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 快进
                player_seek(1);
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void player_toggle_pause() {
        if (null == simpleExoPlayer) return;
        simpleExoPlayer.setPlayWhenReady(!simpleExoPlayer.getPlayWhenReady());
    }

    private void player_seek(int how) {
        if (null == simpleExoPlayer || !(simpleExoPlayer.getDuration() > 1000 * 60 * 3)) return;

        simpleExoPlayer.seekTo(simpleExoPlayer.getCurrentPosition() + how * 1000 * 60);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface"})
    private void create_webview(String url) {
        // 防止重复创建，以内存换html启动时间
        destroy_player();
        webView = new WebView(this);
        WebSettings webSettings = webView.getSettings();
        // 允许file协议的文件通过js的xhr能力访问其它协议的文件，不受跨域策略限制；注意非js访问方式无效；
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        // 关闭内容缓存 类似于 cache-control为no-cache属性时使用
        webSettings.setAppCacheEnabled(false);
        webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE); // 无缓存
        // 启用自带数据库；使用内部实现的sqlite
        webSettings.setDatabaseEnabled(false);
        // 关闭这个，可以使用数据库，因为使用file协议它“可能“不安全，它的origin是file://而已，
        // 虽测试发现不同app实现的webview的storage是独立的，即使都使用file协议，
        // 目前了解到的信息是只有同一个webview，这个数据才会共享，不同的webview就无法使用了。
        // 为了防止后续webview的实现发生改变可以共享了，换成自己实现的数据来保存
        // indexDb好像兼容性不太好，目前还是暂使用这个方案;使用内部实现的sqlite
        webSettings.setDomStorageEnabled(false);
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " tv_ime");

        if (Build.VERSION.SDK_INT >= 26)
            // 检查网址是否安全
            webSettings.setSafeBrowsingEnabled(false);

        if (Build.VERSION.SDK_INT >= 21)
            // http+https是否允许
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // web允许执行js
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        // 可能需要更小的字体来显示键盘文字，值范围1～72
        webSettings.setMinimumFontSize(1);
        webSettings.setMinimumLogicalFontSize(1);
        // 支持 <meta name="viewport" content="width=device-width,
        webSettings.setUseWideViewPort(true);

        // 区域适应内容
        //webView.setLayoutMode(ViewGroup.LayoutParams.WRAP_CONTENT);
        // 把指定java方法暴露给js
        // webView.addJavascriptInterface(this, "JAVA");
        // 一般键盘会把输入的app界面上推，如果透明时就会看到桌面背景
        // webView.setBackgroundColor(Color.TRANSPARENT);
        WebView.setWebContentsDebuggingEnabled(true);//允许调试web

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("qidizi_console", String.format("%s \t%s:%d",
                        consoleMessage.message(),
                        consoleMessage.sourceId(),
                        consoleMessage.lineNumber()
                ));
                return true;
            }
        });

        // 目前没有找到请求超时时间设置方法
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 页面加载完成注入
                webView.evaluateJavascript(String.format(
                        "+function(){"
                                + "let js = document.createElement('script');"
                                + "js.src = '%s/injection.js?r=' + +new Date;"
                                + "document.documentElement.appendChild(js);"
                                + "}();",
                        SoftKeyboard.get_webview_url()
                ), null);//注入自定义方法——获取webview高度的方法
                super.onPageFinished(view, url);
            }

        });
        // webview内部不允许通过触摸或是物理键盘切换焦点
        webView.setFocusable(true);
        webView.loadUrl(url);
        setContentView(webView);
    }

    private void open_url(Intent intent) {
        String url = intent.getStringExtra("url");
        if (null == url) return;

        if (url.contains("__web__")) {
            create_webview(url);
            return;
        }

        play_url(url);
    }

    private void toast(String str) {
        Toast.makeText(this, str, Toast.LENGTH_LONG).show();
    }

    private void destroy_player() {
        if (simpleExoPlayer == null) return;
        simpleExoPlayer.release();
        simpleExoPlayer = null;
    }


    private void destroy_webview() {
        if (webView == null) return;

        // 这样处理防止有内存问题
        // 因为本方法运行在其它线程，不能直接调用webview的方法，否则将引起
        // java.lang.Throwable: A WebView method was called on thread 'JavaBridge'.
        // All WebView methods must be called on the same thread
        // 所以，把待执行方法放入它的队列
        // 如切换到其它输入法再切回来，webview被destroy但是JsInterface并没有重置
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("about:blank");
                webView.clearHistory();
                //((ViewGroup) webView.getParent()).removeView(webView);
                webView.destroy();
                webView = null;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroy_webview();
        destroy_player();
    }
}
