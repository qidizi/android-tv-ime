package qidizi.tv_ime;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView tv;
    private final MainActivity activity = this;
    private final Handler qr_code_handler = new Handler();
    private final Runnable qr_code_run = new Runnable() {
        @Override
        public void run() {
            // 定时刷新2维码
            String url = "http://" + get_lan_ipv4() + ":"
                    + SoftKeyboard.PORT + "/?r=" +
                    new SimpleDateFormat("yyyy.MM.dd-HH_mm_ss", Locale.CHINA)
                            .format(new Date());
            Bitmap bitmap = get_qr_code_bitmap(
                    url,
                    200, 200,
                    "UTF-8",
                    "H", "1",
                    Color.BLACK, Color.WHITE
            );

            if (null == bitmap) {
                tv.setText(new StringBuilder("生成二维码失败,请在手机浏览器中手动输入如下网址来控制电视\n" + url));
            } else {
                ImageSpan imgSpan = new ImageSpan(activity, bitmap);
                SpannableString spannableString = new SpannableString(" ");
                spannableString.setSpan(imgSpan, 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv.setText(spannableString);
                tv.append("\n微信扫码 控制电视\n" + url);
            }
            qr_code_handler.postDelayed(this, 2000);
        }
    };

    private String get_lan_ipv4() {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tv = new TextView(this);
        tv.setClickable(false);
        tv.setPadding(50, 50, 50, 50);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 界面不可见时,停止刷新2维码
        qr_code_handler.removeCallbacks(qr_code_run);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 界面可见时,开始定时刷新2维码
        qr_code_handler.post(qr_code_run);
    }

    /**
     * 生成简单二维码
     *
     * @param content                字符串内容
     * @param width                  二维码宽度
     * @param height                 二维码高度
     * @param character_set          编码方式（一般使用UTF-8）
     * @param error_correction_level 容错率 L：7% M：15% Q：25% H：35%
     * @param margin                 空白边距（二维码与边框的空白区域）
     * @param color_black            黑色色块
     * @param color_white            白色色块
     * @return BitMap
     */
    public static Bitmap get_qr_code_bitmap(String content, int width, int height,
                                            String character_set, String error_correction_level,
                                            String margin, int color_black, int color_white) {
        // 字符串内容判空
        if (TextUtils.isEmpty(content)) {
            return null;
        }
        // 宽和高>=0
        if (width < 0 || height < 0) {
            return null;
        }
        try {
            /* 1.设置二维码相关配置 */
            Hashtable<EncodeHintType, String> hints = new Hashtable<>();
            // 字符转码格式设置
            if (!TextUtils.isEmpty(character_set)) {
                hints.put(EncodeHintType.CHARACTER_SET, character_set);
            }
            // 容错率设置
            if (!TextUtils.isEmpty(error_correction_level)) {
                hints.put(EncodeHintType.ERROR_CORRECTION, error_correction_level);
            }
            // 空白边距设置
            if (!TextUtils.isEmpty(margin)) {
                hints.put(EncodeHintType.MARGIN, margin);
            }
            /* 2.将配置参数传入到QRCodeWriter的encode方法生成BitMatrix(位矩阵)对象 */
            BitMatrix bitMatrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            /* 3.创建像素数组,并根据BitMatrix(位矩阵)对象为数组元素赋颜色值 */
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    //bitMatrix.get(x,y)方法返回true是黑色色块，false是白色色块
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = color_black;//黑色色块像素设置
                    } else {
                        pixels[y * width + x] = color_white;// 白色色块像素设置
                    }
                }
            }
            /* 4.创建Bitmap对象,根据像素数组设置Bitmap每个像素点的颜色值,并返回Bitmap对象 */
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
