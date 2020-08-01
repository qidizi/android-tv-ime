package qidizi.tv_ime;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
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
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class MainActivity extends AppCompatActivity {
    SimpleExoPlayer simpleExoPlayer = null;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        open_url(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 唤醒httpd服务
        Intent intent = new Intent(this, SoftKeyboard.class);
        Log.d("qidizi_debug", "激活httpd" + (startService(intent) == null ? "失败" : "成功"));
        String url = getIntent().getStringExtra("url");

        if (null == url) {
            create_qr();
            return;
        }

        open_url(getIntent());
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 如果处于二维码界面,结束回收资源,若需要再次启动即可
        if (null == simpleExoPlayer) {
            Log.d("qidizi_debug", "切换到后台,结束 " + this.getClass().getName());
            finish();
        }
    }

    private void open_url(Intent intent) {
        String url = intent.getStringExtra("url");
        String seek_min = intent.getStringExtra("seek");
        int seek = 0;

        if (null != seek_min) {
            try {
                seek = Integer.parseInt(seek_min) * 60 * 1000;
            } catch (Exception ignore) {
            }
        }

        if (null == url) return;
        final MainActivity me = this;

        if (null == simpleExoPlayer) {
            simpleExoPlayer = new SimpleExoPlayer.Builder(this).build();
            PlayerView playerView = new PlayerView(this);
            playerView.setPlayer(simpleExoPlayer);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
            // 自适应分辨率,如4:3,16:9
            playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
            simpleExoPlayer.setVolume(1);
            simpleExoPlayer.addListener(new Player.EventListener() {
                @Override
                public void onPlayerError(@NonNull ExoPlaybackException error) {
                    SoftKeyboard.toast(error.getMessage(), me);
                }
            });
            simpleExoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
            setContentView(playerView);
        }

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this,
                        "Mozilla/5.0 (Linux; Android 8.0; Pixel 2 Build/OPD3.170816.012)" +
                                " AppleWebKit/537.36 (KHTML, like Gecko)" +
                                " Chrome/84.0.4147.89 Mobile Safari/537.36"
                ));
        MediaSource videoSource;
        Uri uri = Uri.parse(url);
        String extension;
        extension = uri.getQueryParameter("ext");

        if (null == extension) {
            SoftKeyboard.toast("请指定后缀ext参数", this);
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

                SoftKeyboard.toast("Unsupported type: " + type, this);
                return;
        }

        simpleExoPlayer.prepare(videoSource);
        // 自动播放
        simpleExoPlayer.setPlayWhenReady(true);
        if (seek > 0)
            simpleExoPlayer.seekTo(seek);
    }

    private void create_qr() {
        try {
            String ip = SoftKeyboard.get_lan_ipv4();
            BitMatrix result = new QRCodeWriter().encode(
                    String.format(
                            "%s/index.html?tv=%s&rnd=%s",
                            SoftKeyboard.get_client_url(),
                            ip,
                            System.currentTimeMillis()
                    ), BarcodeFormat.QR_CODE, 300, 300);
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
            textView.append("微信扫码 遥控电视\n电视IP " + ip);
            setContentView(textView);
        } catch (Exception e) {
            SoftKeyboard.toast("创建二维码失败:" + e.getMessage(), this);
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
                if (player_toggle_pause())
                    return true;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                // 快退
                if (player_seek(-1)) return true;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // 快进
                if (player_seek(1)) return true;
                break;
        }
        return false;
    }

    private boolean player_toggle_pause() {
        if (null == simpleExoPlayer) return false;
        simpleExoPlayer.setPlayWhenReady(!simpleExoPlayer.getPlayWhenReady());
        return true;
    }

    private boolean player_seek(int how) {
        if (null == simpleExoPlayer || !(simpleExoPlayer.getDuration() > 1000 * 60 * 3)) return false;

        simpleExoPlayer.seekTo(simpleExoPlayer.getCurrentPosition() + how * 1000 * 60);
        return true;
    }

    private void destroy_player() {
        if (simpleExoPlayer == null) return;
        simpleExoPlayer.release();
        simpleExoPlayer = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destroy_player();
    }
}
