package in.gov.bhilwara.wildlife;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** Full-screen local photo viewer with visible back navigation and swipe support. */
public class NativeImageActivity extends Activity {
    private JSONArray items;
    private int index = 0;
    private ImageView imageView;
    private TextView titleView;
    private TextView countView;
    private float downX;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        try {
            items = new JSONArray(getIntent().getStringExtra("IMAGE_ITEMS_JSON"));
            index = Math.max(0, Math.min(getIntent().getIntExtra("IMAGE_START_INDEX", 0), items.length() - 1));
        } catch (Exception e) {
            Toast.makeText(this, "Photo list is unavailable.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (items.length() == 0) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        imageView = new ImageView(this);
        imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(imageView, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout topBar = new FrameLayout(this);
        topBar.setBackgroundColor(0xCC082C20);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(62), Gravity.TOP);
        root.addView(topBar, topParams);

        TextView back = new TextView(this);
        back.setText("‹  Back");
        back.setTextColor(Color.WHITE);
        back.setTextSize(18);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(16), 0, dp(16), 0);
        topBar.addView(back, new FrameLayout.LayoutParams(dp(112), -1, Gravity.START | Gravity.CENTER_VERTICAL));
        back.setOnClickListener(v -> finish());

        countView = new TextView(this);
        countView.setTextColor(Color.WHITE);
        countView.setTextSize(14);
        countView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams countParams = new FrameLayout.LayoutParams(dp(80), -1, Gravity.END | Gravity.CENTER_VERTICAL);
        topBar.addView(countView, countParams);

        titleView = new TextView(this);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(2);
        titleView.setPadding(dp(18), dp(12), dp(18), dp(12));
        titleView.setBackgroundColor(0xCC082C20);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(titleView, titleParams);

        TextView previous = navButton("‹", Gravity.START | Gravity.CENTER_VERTICAL);
        TextView next = navButton("›", Gravity.END | Gravity.CENTER_VERTICAL);
        root.addView(previous, new FrameLayout.LayoutParams(dp(58), dp(76), Gravity.START | Gravity.CENTER_VERTICAL));
        root.addView(next, new FrameLayout.LayoutParams(dp(58), dp(76), Gravity.END | Gravity.CENTER_VERTICAL));
        previous.setOnClickListener(v -> showImage(index - 1));
        next.setOnClickListener(v -> showImage(index + 1));

        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) { downX = event.getX(); return true; }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX;
                if (Math.abs(dx) > dp(55)) showImage(index + (dx < 0 ? 1 : -1));
                return true;
            }
            return true;
        });

        setContentView(root);
        showImage(index);
    }

    private TextView navButton(String label, int gravity) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(42);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(0x55000000);
        return button;
    }

    private void showImage(int requested) {
        if (requested < 0 || requested >= items.length()) return;
        index = requested;
        try {
            JSONObject item = items.getJSONObject(index);
            String title = item.optString("title", "Photo");
            String path = item.optString("source", "");
            File file = new File(path);
            if (!file.isFile() || !file.canRead()) throw new Exception("Image file not accessible: " + file.getName());
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("Unsupported image format");
            int targetW = getResources().getDisplayMetrics().widthPixels;
            int targetH = getResources().getDisplayMetrics().heightPixels;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds, targetW, targetH);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap == null) throw new Exception("Could not decode image");
            imageView.setImageBitmap(bitmap);
            titleView.setText(title);
            countView.setText((index + 1) + " / " + items.length());
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int sampleSize(BitmapFactory.Options bounds, int width, int height) {
        int sample = 1;
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2;
        return Math.max(1, sample);
    }

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() { finish(); }
}
