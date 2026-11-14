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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NativeImageActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private JSONArray items;
    private int index = 0;
    private ImageView imageView;
    private ProgressBar progress;
    private TextView titleView, counterView;
    private float downX, downY;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        try { items = new JSONArray(getIntent().getStringExtra("IMAGE_ITEMS_JSON")); }
        catch (Exception e) { items = new JSONArray(); }
        index = Math.max(0, Math.min(getIntent().getIntExtra("IMAGE_START_INDEX", 0), items.length()-1));
        if (items.length() == 0) { finish(); return; }

        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        imageView = new ImageView(this); imageView.setBackgroundColor(Color.BLACK);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(imageView, new FrameLayout.LayoutParams(-1, -1));
        imageView.setOnTouchListener((v, event) -> handleSwipe(event));
        progress = new ProgressBar(this);
        root.addView(progress, new FrameLayout.LayoutParams(96, 96, Gravity.CENTER));

        TextView close = new TextView(this); close.setText("✕"); close.setTextColor(Color.WHITE);
        close.setTextSize(28); close.setGravity(Gravity.CENTER); close.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(64,64,Gravity.TOP|Gravity.END); cp.setMargins(16,16,16,16);
        root.addView(close,cp); close.setOnClickListener(v -> finish());

        titleView = new TextView(this); titleView.setTextColor(Color.WHITE); titleView.setTextSize(16);
        titleView.setPadding(20,14,20,14); titleView.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM); root.addView(titleView,tp);
        counterView = new TextView(this); counterView.setTextColor(Color.WHITE); counterView.setTextSize(14);
        counterView.setPadding(12,8,12,8); counterView.setBackgroundColor(0x99000000); counterView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL); np.topMargin=18; root.addView(counterView,np);

        TextView prev = navButton("‹"); FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(56,80,Gravity.CENTER_VERTICAL|Gravity.START); pp.leftMargin=8; root.addView(prev,pp); prev.setOnClickListener(v -> showAt(index-1));
        TextView next = navButton("›"); FrameLayout.LayoutParams np2 = new FrameLayout.LayoutParams(56,80,Gravity.CENTER_VERTICAL|Gravity.END); np2.rightMargin=8; root.addView(next,np2); next.setOnClickListener(v -> showAt(index+1));
        setContentView(root); showAt(index);
    }

    private TextView navButton(String text) { TextView t=new TextView(this); t.setText(text); t.setTextColor(Color.WHITE); t.setTextSize(42); t.setGravity(Gravity.CENTER); t.setBackgroundColor(0x55000000); return t; }

    private boolean handleSwipe(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_DOWN) { downX=e.getX(); downY=e.getY(); return true; }
        if (e.getAction()==MotionEvent.ACTION_UP) {
            float dx=e.getX()-downX, dy=e.getY()-downY;
            if (Math.abs(dx)>70 && Math.abs(dx)>Math.abs(dy)*1.2f) { showAt(index+(dx<0?1:-1)); return true; }
            imageView.performClick(); return true;
        }
        return true;
    }

    private void showAt(int newIndex) {
        if (newIndex<0) newIndex=items.length()-1;
        if (newIndex>=items.length()) newIndex=0;
        index=newIndex;
        try {
            JSONObject item=items.getJSONObject(index);
            String title=item.optString("title","Photo"); String source=item.optString("source",""); boolean local=item.optBoolean("local",false);
            titleView.setText(title); counterView.setText((index+1)+" / "+items.length());
            progress.setVisibility(View.VISIBLE); imageView.setImageDrawable(null);
            final String sourceCopy=source; final boolean localCopy=local;
            executor.execute(() -> {
                try {
                    Bitmap bitmap;
                    if (localCopy) bitmap=decodeLocal(sourceCopy);
                    else bitmap=decodeRemote(sourceCopy);
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { imageView.setImageBitmap(bitmap); progress.setVisibility(View.GONE); } });
                } catch (Exception ex) {
                    runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { progress.setVisibility(View.GONE); Toast.makeText(this,"Unable to load "+title,Toast.LENGTH_SHORT).show(); } });
                }
            });
        } catch (Exception ignored) { }
    }

    private Bitmap decodeLocal(String path) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeFile(path,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0) throw new IllegalStateException("Invalid local image");
        BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample(bounds.outWidth,bounds.outHeight);
        opts.inPreferredConfig=Bitmap.Config.RGB_565; Bitmap b=BitmapFactory.decodeFile(path,opts);
        if(b==null) throw new IllegalStateException("Image decode failed"); return b;
    }

    private Bitmap decodeRemote(String original) throws Exception {
        String url=original, cookies="";
        if(original.contains("drive.google.com")) { DriveVideoResolver.ResolvedVideo r=DriveVideoResolver.resolve(original); url=r.url; cookies=r.cookieHeader; }
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection(); c.setConnectTimeout(20000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
        if(cookies!=null&&!cookies.isEmpty()) c.setRequestProperty("Cookie",cookies);
        int status=c.getResponseCode(); String ct=c.getContentType();
        if(status<200||status>=300||(ct!=null&&ct.toLowerCase().contains("text/html"))) { c.disconnect(); throw new IllegalStateException("Image server returned non-image response"); }
        try(InputStream in=c.getInputStream()) {
            BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeStream(in,null,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0) throw new IllegalStateException("Invalid image");
        } finally { c.disconnect(); }
        HttpURLConnection c2=(HttpURLConnection)new URL(url).openConnection(); c2.setConnectTimeout(20000); c2.setReadTimeout(30000); c2.setInstanceFollowRedirects(true);
        if(cookies!=null&&!cookies.isEmpty()) c2.setRequestProperty("Cookie",cookies);
        try(InputStream in=c2.getInputStream()) { BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inSampleSize=sample(2400,2400); opts.inPreferredConfig=Bitmap.Config.RGB_565; Bitmap b=BitmapFactory.decodeStream(in,null,opts); if(b==null) throw new IllegalStateException("Decode failed"); return b; }
        finally { c2.disconnect(); }
    }

    private int sample(int w,int h) { int reqW=getResources().getDisplayMetrics().widthPixels, reqH=getResources().getDisplayMetrics().heightPixels; int n=1; while(w/(n*2)>=reqW && h/(n*2)>=reqH) n*=2; return n; }
    private void enableImmersiveMode() { getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if(f) enableImmersiveMode(); }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
