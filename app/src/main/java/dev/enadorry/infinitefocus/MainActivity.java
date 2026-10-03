package dev.enadorry.infinitefocus;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final int bg = Color.rgb(16,25,27), ink = Color.rgb(224,237,231), accent = Color.rgb(180,228,207), muted = Color.rgb(145,166,160);
    private SharedPreferences preferences;
    private Button play;
    private TextView status, clock;
    private OrbitView orbit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            boolean active = PlaybackService.playing;
            play.setText(active ? "停止する" : "演奏をはじめる");
            status.setText(PlaybackService.message);
            long seconds = active ? Math.max(0, (SystemClock.elapsedRealtime() - PlaybackService.startedAt) / 1000) : 0;
            if (active && PlaybackService.deadline > 0) {
                seconds = Math.max(0, (PlaybackService.deadline - SystemClock.elapsedRealtime()) / 1000);
                clock.setText(String.format(Locale.JAPAN, "残り %02d:%02d", seconds / 60, seconds % 60));
            } else clock.setText(active ? String.format(Locale.JAPAN, "%02d:%02d  /  ∞", seconds / 60, seconds % 60) : "OFFLINE GENERATIVE MUSIC");
            orbit.active = active; orbit.invalidate();
            handler.postDelayed(this, 500);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("focus", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(bg); scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(28), dp(24), dp(28), dp(28));
        scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);
        TextView tag = text("ENADORRY  /  SOUND LAB", 11, muted); tag.setLetterSpacing(.18f); root.addView(tag);
        TextView title = text("Infinite Focus", 34, ink); title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); margin(root, title, 20);
        margin(root, text("終わりのない音。目の前のことに。", 14, muted), 8);
        orbit = new OrbitView(); root.addView(orbit, new LinearLayout.LayoutParams(-1, dp(190)));
        clock = text("OFFLINE GENERATIVE MUSIC", 12, accent); clock.setGravity(Gravity.CENTER); clock.setLetterSpacing(.12f); root.addView(clock);
        status = text("", 12, muted); status.setGravity(Gravity.CENTER); margin(root, status, 12);
        play = new Button(this); play.setAllCaps(false); play.setTextSize(16); play.setTextColor(bg); play.setBackground(shape(accent, 18));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58)); bp.topMargin = dp(24); root.addView(play, bp);
        play.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlaybackService.class).setAction(PlaybackService.playing ? PlaybackService.STOP : PlaybackService.PLAY);
            if (PlaybackService.playing) startService(intent);
            else {
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !preferences.getBoolean("askedNotifications", false)) {
                    preferences.edit().putBoolean("askedNotifications", true).apply();
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3);
                }
                startForegroundService(intent);
            }
            handler.postDelayed(() -> refreshUiOnce(), 150);
        });
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18),dp(10),dp(18),dp(14)); panel.setBackground(shape(Color.rgb(26,38,40), 20));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,-2); pp.topMargin = dp(26); root.addView(panel,pp);
        slider(panel, "テンポ", "bpm", 45, 100, 68, " BPM");
        slider(panel, "旋律の音数", "density", 0, 100, 35, "%");
        slider(panel, "BGM音量", "volume", 0, 100, 65, "%");
        toggle(panel, "控えめなドラム", "drums");
        toggle(panel, "雨のような環境音", "rain");
        margin(panel, text("水辺の音", 15, accent), 24);
        toggle(panel, "水のせせらぎ", "water");
        slider(panel, "水の音量", "waterVolume", 0, 100, 55, "%");
        toggle(panel, "鹿威し（ししおどし）", "bamboo");
        slider(panel, "鹿威しの音量", "bambooVolume", 0, 100, 60, "%");
        slider(panel, "鹿威しの間隔", "bambooInterval", 10, 60, 25, "秒");
        margin(panel, text("BGM音量を0にすると、環境音だけで聴けます。", 11, muted), 14);
        margin(root, text("終了タイマー", 13, muted), 24);
        LinearLayout timers = new LinearLayout(this); timers.setOrientation(LinearLayout.HORIZONTAL); margin(root, timers, 8);
        int[] values = {0,25,50}; String[] labels = {"無制限", "25分", "50分"}; Button[] buttons = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int minutes = values[i]; Button button = new Button(this); buttons[i] = button;
            button.setText(labels[i]); button.setTextSize(13); button.setAllCaps(false);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,dp(48),1); if (i > 0) tp.leftMargin = dp(8); timers.addView(button,tp);
            button.setOnClickListener(v -> {
                preferences.edit().putInt("timer", minutes).apply();
                if (PlaybackService.playing) startService(new Intent(this,PlaybackService.class).setAction(PlaybackService.UPDATE_TIMER));
                for (int j = 0; j < buttons.length; j++) timerStyle(buttons[j], values[j] == minutes);
            });
            timerStyle(button, preferences.getInt("timer",0) == minutes);
        }
        TextView footer = text("テンポ・音数・ドラムは次の小節で反映。\n停止後に再生すると、新しい曲になります。", 11, muted); footer.setLineSpacing(dp(4),1); margin(root, footer,20);
    }
    private void refreshUiOnce() { play.setText(PlaybackService.playing ? "停止する" : "演奏をはじめる"); status.setText(PlaybackService.message); }
    private void timerStyle(Button b, boolean selected) { b.setTextColor(selected ? bg : ink); b.setBackground(shape(selected ? accent : Color.rgb(31,45,46),12)); }
    private void slider(LinearLayout parent, String title, String key, int min, int max, int initial, String unit) {
        TextView label = text("",13,ink); margin(parent,label,14);
        SeekBar bar = new SeekBar(this); bar.setMax(max-min); bar.setProgress(preferences.getInt(key,initial)-min);
        bar.setProgressTintList(ColorStateList.valueOf(accent)); bar.setThumbTintList(ColorStateList.valueOf(accent));
        label.setText(getString(R.string.slider_label, title, bar.getProgress()+min, unit));
        parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(42)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
            public void onProgressChanged(SeekBar s,int progress,boolean user) {
                label.setText(getString(R.string.slider_label, title, progress+min, unit));
                if (user) { preferences.edit().putInt(key,progress+min).apply(); update(); }
            }
        });
    }
    private void toggle(LinearLayout parent, String label, String key) {
        Switch control = new Switch(this); control.setText(label); control.setTextSize(13); control.setTextColor(ink); control.setChecked(preferences.getBoolean(key,false));
        control.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent,muted}));
        margin(parent,control,14); control.setOnCheckedChangeListener((b,on) -> { preferences.edit().putBoolean(key,on).apply(); update(); });
    }
    private void update() { if (PlaybackService.playing) startService(new Intent(this,PlaybackService.class).setAction(PlaybackService.UPDATE)); }
    private TextView text(String value,int size,int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void margin(LinearLayout parent,View view,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); parent.addView(view,p); }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+.5f); }
    @Override public void onResume() { super.onResume(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private final class OrbitView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path = new Path(); boolean active;
        OrbitView() { super(MainActivity.this); setContentDescription("ゆっくり変化する音の輪"); }
        @Override protected void onDraw(Canvas c) {
            float x=getWidth()/2f,y=getHeight()/2f,r=dp(58); double t=active ? SystemClock.elapsedRealtime()/4500.0 : 0;
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(Color.rgb(47,70,67)); c.drawCircle(x,y,r+dp(19),p);
            p.setColor(accent); p.setStrokeWidth(dp(1.7f)); path.reset();
            for(int i=0;i<=160;i++) { double a=i*Math.PI*2/160; double rr=r+Math.sin(a*3+t)*dp(6)+Math.cos(a*5-t*.7)*dp(3); float px=x+(float)(Math.cos(a)*rr),py=y+(float)(Math.sin(a)*rr); if(i==0)path.moveTo(px,py);else path.lineTo(px,py); }
            path.close(); c.drawPath(path,p); p.setStyle(Paint.Style.FILL); p.setColor(ink); p.setTextSize(dp(34)); p.setTextAlign(Paint.Align.CENTER); c.drawText("∞",x,y+dp(11),p);
            if(active)postInvalidateDelayed(50);
        }
    }
}
