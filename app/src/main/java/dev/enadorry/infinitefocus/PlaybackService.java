package dev.enadorry.infinitefocus;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.*;
import android.os.*;

public final class PlaybackService extends Service {
    public static final String PLAY = "dev.enadorry.infinitefocus.PLAY";
    public static final String STOP = "dev.enadorry.infinitefocus.STOP";
    public static final String UPDATE_TIMER = "dev.enadorry.infinitefocus.UPDATE_TIMER";
    public static final String UPDATE = "dev.enadorry.infinitefocus.UPDATE";
    public static volatile boolean playing;
    public static volatile long startedAt, deadline;
    public static volatile String message = "再生すると、新しい曲が始まります";
    private static final int NOTIFICATION = 71;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile FocusSynth synth;
    private volatile AudioTrack track;
    private volatile boolean stopping, destroyed;
    private boolean restartRequested;
    private Thread worker;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private final Runnable renewWakeLock = new Runnable() {
        @Override public void run() {
            if (playing && !destroyed && !stopping) {
                wakeLock.acquire(600000L);
                main.postDelayed(this, 300000L);
            }
        }
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { stopPlayback("イヤホンが外れたため停止しました"); }
    };
    @Override public void onCreate() {
        super.onCreate();
        audioManager = getSystemService(AudioManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("music", "BGM再生", NotificationManager.IMPORTANCE_LOW));
        session = new MediaSession(this, "InfiniteFocus");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { startPlayback(); }
            @Override public void onPause() { stopPlayback("停止しました"); }
            @Override public void onStop() { stopPlayback("停止しました"); }
        }, main);
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Infinite Focus")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "端末内で自動作曲中").build());
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change -> {
                if (change < 0) stopPlayback("他の音声を優先して停止しました");
            }, main).build();
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "InfiniteFocus:Audio");
        wakeLock.setReferenceCounted(false);
    }
    private AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? STOP : intent.getAction();
        if (PLAY.equals(action)) startPlayback();
        else if (UPDATE.equals(action)) {
            if (synth != null) { synth.configure(settings()); }
        } else if (UPDATE_TIMER.equals(action)) {
            if (playing) updateTimer();
        } else stopPlayback("停止しました");
        return START_NOT_STICKY;
    }
    private FocusSynth.Settings settings() {
        SharedPreferences p = getSharedPreferences("focus", MODE_PRIVATE);
        return new FocusSynth.Settings(p.getInt("bpm", 68), p.getInt("density", 35),
            p.getBoolean("drums", false), p.getBoolean("rain", false), p.getInt("volume", 65) / 100.0,
            p.getBoolean("water", false), p.getBoolean("bamboo", false),
            p.getInt("waterVolume", 55) / 100.0, p.getInt("bambooVolume", 60) / 100.0, p.getInt("bambooInterval", 25));
    }
    private void updateTimer() {
        int minutes = getSharedPreferences("focus", MODE_PRIVATE).getInt("timer", 0);
        deadline = minutes == 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60000L;
    }
    private void startPlayback() {
        if (destroyed) return;
        if (worker != null) { if (stopping) restartRequested = true; return; }
        stopping = false;
        try {
            session.setActive(true);
            session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1)
                .setActions(PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP).build());
            Notification notification = notification();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIFICATION, notification);
            if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                message = "音声を再生できません。他の音声を停止して再試行してください";
                finishPlayback(); return;
            }
            synth = new FocusSynth(System.nanoTime());
            synth.configure(settings());
            startedAt = SystemClock.elapsedRealtime(); updateTimer();
            playing = true; message = "途切れず、少しずつ変わるBGMを演奏中";
            wakeLock.acquire(600000L);
            main.removeCallbacks(renewWakeLock);
            main.postDelayed(renewWakeLock, 300000L);
            worker = new Thread(this::stream, "FocusAudio"); worker.start();
        } catch (RuntimeException e) {
            message = "再生を開始できませんでした: " + e.getClass().getSimpleName();
            finishPlayback();
        }
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2, new Intent(this, PlaybackService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "music").setSmallIcon(dev.enadorry.infinitefocus.R.drawable.ic_focus)
            .setContentTitle("Infinite Focus").setContentText("オフラインで自動作曲中")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "停止", stop).build())
            .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0)).build();
    }
    private void stream() {
        AudioTrack local = null;
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
            int min = AudioTrack.getMinBufferSize(FocusSynth.RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("Unsupported audio format");
            local = new AudioTrack.Builder().setAudioAttributes(attributes())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(FocusSynth.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min * 2, 8192)).build();
            track = local;
            if (local.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("Audio init");
            local.play(); short[] pcm = new short[1024];
            while (!destroyed) {
                if (!stopping && deadline > 0 && SystemClock.elapsedRealtime() >= deadline) {
                    stopping = true; message = "タイマーが終了しました"; synth.fadeOut();
                }
                synth.render(pcm, pcm.length / 2);
                int offset = 0;
                while (offset < pcm.length && !destroyed) {
                    int n = local.write(pcm, offset, pcm.length - offset, AudioTrack.WRITE_BLOCKING);
                    if (n < 0) throw new IllegalStateException("Audio write " + n);
                    if (n == 0 && !destroyed) throw new IllegalStateException("Audio stalled");
                    offset += n;
                }
                if (synth.isQuiet()) break;
            }
        } catch (RuntimeException e) {
            if (!destroyed) message = "音声が停止しました: " + e.getClass().getSimpleName();
        } finally {
            if (local != null) { try { local.stop(); } catch (RuntimeException ignored) { } local.release(); }
            track = null;
            main.post(() -> {
                worker = null;
                if (destroyed) return;
                playing = false;
                if (restartRequested) { restartRequested = false; startPlayback(); }
                else finishPlayback();
            });
        }
    }
    private void stopPlayback(String reason) {
        restartRequested = false;
        message = reason; stopping = true;
        if (synth != null && worker != null) synth.fadeOut();
        else finishPlayback();
    }
    private void finishPlayback() {
        playing = false; deadline = 0; synth = null;
        main.removeCallbacks(renewWakeLock);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        audioManager.abandonAudioFocusRequest(focus);
        session.setPlaybackState(new PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0).build());
        session.setActive(false);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() {
        destroyed = true; playing = false; deadline = 0;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (audioManager != null && focus != null) audioManager.abandonAudioFocusRequest(focus);
        AudioTrack t = track; if (t != null) { try { t.pause(); } catch (RuntimeException ignored) { } }
        unregisterReceiver(noisy); session.release(); main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
