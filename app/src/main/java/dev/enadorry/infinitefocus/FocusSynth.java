package dev.enadorry.infinitefocus;

import java.util.Random;

/** Platform-independent, bounded-memory procedural stereo synthesizer. */
public final class FocusSynth {
    public static final int RATE = 48000;
    private static final int TABLE_SIZE = 8192;
    private static final double[] SIN = new double[TABLE_SIZE];
    static { for (int i = 0; i < TABLE_SIZE; i++) SIN[i] = Math.sin(i * 2 * Math.PI / TABLE_SIZE); }
    static double sin(double phase) {
        double p = (phase - Math.floor(phase)) * TABLE_SIZE;
        int i = (int)p;
        return SIN[i] + (SIN[(i + 1) & (TABLE_SIZE - 1)] - SIN[i]) * (p - i);
    }
    public static final class Settings {
        public final int bpm, density;
        public final boolean drums, rain, water, bamboo;
        public final double volume, waterVolume, bambooVolume;
        public final int bambooInterval;
        public Settings(int bpm, int density, boolean drums, boolean rain, double volume) {
            this(bpm, density, drums, rain, volume, false, false, .55, .60, 25);
        }
        public Settings(int bpm, int density, boolean drums, boolean rain, double volume,
                        boolean water, boolean bamboo, double waterVolume, double bambooVolume, int bambooInterval) {
            this.bpm = Math.max(45, Math.min(100, bpm));
            this.density = Math.max(0, Math.min(100, density));
            this.drums = drums; this.rain = rain;
            this.volume = Math.max(0, Math.min(1, volume));
            this.water = water; this.bamboo = bamboo;
            this.waterVolume = Math.max(0, Math.min(1, waterVolume));
            this.bambooVolume = Math.max(0, Math.min(1, bambooVolume));
            this.bambooInterval = Math.max(10, Math.min(60, bambooInterval));
        }
    }
    private volatile Settings requested = new Settings(68, 35, false, false, .65);
    private Settings musical = requested;
    private final Random random;
    private final NatureSynth nature;
    private final Voice[] voices = new Voice[64];
    private final double[] delayL = new double[16800], delayR = new double[21840];
    private int dl, dr, step, bar = -1, progression;
    private double stepRemaining, gain, musicGain, rainLow, rainLevel;
    private volatile boolean fading;
    private long frames;
    private int[] motif = {0, 2, 1, 3, 2, 1, 0, 2};
    private static final int[][][] CHORDS = {
        {{48,55,59,62}, {45,52,55,59}, {41,48,52,55}, {43,50,53,57}},
        {{48,55,59,64}, {43,50,57,59}, {45,52,55,60}, {41,48,55,57}},
        {{45,52,55,59}, {41,48,52,57}, {48,55,59,62}, {43,50,53,59}}
    };
    public FocusSynth(long seed) {
        random = new Random(seed);
        nature = new NatureSynth(seed ^ 0x4E41545552454CL);
        for (int i = 0; i < voices.length; i++) voices[i] = new Voice();
    }
    public void configure(Settings settings) { requested = settings; }
    public void fadeOut() { fading = true; }
    public boolean isQuiet() { return fading && gain < .0001; }
    public long generatedFrames() { return frames; }
    public int currentBar() { return bar + 1; }
    private static double hz(int midi) { return 440 * Math.pow(2, (midi - 69) / 12.0); }
    private void note(int type, int midi, double amp, double seconds, double pan) {
        Voice chosen = voices[0];
        for (Voice v : voices) {
            if (!v.active) { chosen = v; break; }
            if (v.age / v.duration > chosen.age / chosen.duration) chosen = v;
        }
        chosen.start(type, hz(midi), amp, seconds, pan);
    }
    private void schedule() {
        if (step == 0) {
            bar++;
            musical = requested;
            if (bar % 16 == 0) {
                progression = random.nextInt(CHORDS.length);
                for (int i = 0; i < motif.length; i++) motif[i] = random.nextInt(4);
            }
            int[] c = CHORDS[progression][(bar / 2) % 4];
            if (bar % 2 == 0) {
                double seconds = 60.0 / musical.bpm * 9;
                for (int i = 0; i < c.length; i++) note(0, c[i], .085, seconds, .15 + i * .23);
            }
            note(2, c[0] - 12, .115, 60.0 / musical.bpm * 3.5, .5);
        }
        int[] c = CHORDS[progression][(bar / 2) % 4];
        if (step % 2 == 0 && random.nextDouble() < .10 + musical.density * .006) {
            int index = motif[((bar % 2) * 4 + step / 4) % motif.length];
            note(1, c[index] + 12, .065 + random.nextDouble() * .03, 2.8, .25 + random.nextDouble() * .5);
        }
        if (musical.drums) {
            if (step == 0 || step == 8) note(3, 36, .12, .26, .5);
            if (step == 4 || step == 12) note(4, 60, .032, .17, .52);
            if (step % 2 == 0 && random.nextDouble() < .65) note(4, 90, .012, .045, .65);
        }
        step = (step + 1) % 16;
        stepRemaining += RATE * 60.0 / musical.bpm / 4;
    }
    /** Fill interleaved PCM16 stereo. No Android APIs or per-buffer allocations. */
    public void render(short[] output, int frameCount) {
        if (frameCount < 0 || frameCount * 2 > output.length) throw new IllegalArgumentException("buffer");
        Settings latest = requested;
        double target = fading ? 0 : 1;
        for (int f = 0; f < frameCount; f++) {
            if (stepRemaining <= 0) schedule();
            stepRemaining--;
            double l = 0, r = 0;
            for (Voice v : voices) {
                if (!v.active) continue;
                double n = v.sample(random);
                l += n * v.left; r += n * v.right;
            }
            rainLow += .08 * ((random.nextDouble() * 2 - 1) - rainLow);
            rainLevel += ((latest.rain ? .026 : 0) - rainLevel) * .00008;
            double echoL = delayL[dl], echoR = delayR[dr];
            delayL[dl] = l + echoR * .25; delayR[dr] = r + echoL * .25;
            dl = (dl + 1) % delayL.length; dr = (dr + 1) % delayR.length;
            l += echoL * .22; r += echoR * .22;
            musicGain += (latest.volume - musicGain) * .00045;
            l *= musicGain; r *= musicGain;
            nature.sample(latest.water, latest.bamboo, latest.waterVolume, latest.bambooVolume, latest.bambooInterval);
            l += rainLow * rainLevel + nature.left;
            r += rainLow * rainLevel + nature.right;
            gain += (target - gain) * .00045;
            output[f * 2] = (short)(32767 * gain * l / (1 + Math.abs(l)));
            output[f * 2 + 1] = (short)(32767 * gain * r / (1 + Math.abs(r)));
            frames++;
        }
    }
    private static final class Voice {
        boolean active;
        int type;
        double phase, phase2, age, duration, amp, left, right, inc;
        void start(int type, double frequency, double amp, double duration, double pan) {
            this.type = type; this.amp = amp; this.duration = duration;
            age = 0; phase = 0; phase2 = .12; inc = frequency / RATE;
            left = Math.sqrt(1 - pan); right = Math.sqrt(pan); active = true;
        }
        double sample(Random random) {
            if (age >= duration) { active = false; return 0; }
            double envelope, value;
            if (type == 0) {
                envelope = Math.min(1, age / 1.2) * Math.min(1, (duration - age) / 1.6);
                value = .7 * sin(phase) + .2 * sin(phase2) + .1 * sin(phase * 2);
                phase2 += inc * 1.003;
            } else if (type == 1) {
                envelope = Math.min(1, age / .008) * Math.exp(-age * 1.8) * Math.min(1, (duration - age) / .1);
                value = sin(phase + .08 * sin(phase * 2) * Math.exp(-age * 2));
            } else if (type == 2) {
                envelope = Math.min(1, age / .035) * Math.min(1, (duration - age) / .4);
                value = sin(phase) * .9 + sin(phase * 2) * .1;
            } else if (type == 3) {
                envelope = Math.min(1, age / .003) * Math.exp(-age * 20);
                value = sin(phase); phase += (35 + 100 * Math.exp(-age * 35)) / RATE;
            } else {
                envelope = Math.min(1, age / .002) * Math.exp(-age * 45) * Math.min(1, (duration - age) / .01);
                value = random.nextDouble() * 2 - 1;
            }
            if (type != 3) phase += inc;
            // Keep oscillator phases bounded during arbitrarily long sessions.
            if (phase >= 1) phase -= Math.floor(phase);
            if (phase2 >= 1) phase2 -= Math.floor(phase2);
            age += 1.0 / RATE;
            return value * envelope * amp;
        }
    }
}
