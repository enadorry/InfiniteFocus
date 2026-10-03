package dev.enadorry.infinitefocus;

import java.util.Random;

/** Synthetic stream and bamboo fountain; no recordings or network access. */
public final class NatureSynth {
    private final Random random;
    private final Drop[] drops = new Drop[12];
    private double lowL, lowR, midL, midR, swellPhase;
    private double waterGain, bambooGain, dropCountdown;
    private boolean bambooEnabled;
    private double knockCountdown, knockAge = 1, knockPitch, knockDecay;
    private int knockCount;
    public double left, right;

    public NatureSynth(long seed) {
        random = new Random(seed);
        for (int i = 0; i < drops.length; i++) drops[i] = new Drop();
    }
    public int knockCount() { return knockCount; }
    public void sample(boolean water, boolean bamboo, double waterVolume, double bambooVolume, int intervalSeconds) {
        waterGain += ((water ? waterVolume : 0) - waterGain) * .00015;
        bambooGain += ((bamboo ? bambooVolume : 0) - bambooGain) * .00015;
        left = 0; right = 0;
        if (waterGain > .000001) {
            double nl = random.nextDouble() * 2 - 1, nr = random.nextDouble() * 2 - 1;
            lowL += .009 * (nl - lowL); lowR += .011 * (nr - lowR);
            midL += .16 * (nl - midL); midR += .14 * (nr - midR);
            swellPhase += 1.0 / (FocusSynth.RATE * 9.0);
            if (swellPhase >= 1) swellPhase -= 1;
            double swell = .86 + .14 * FocusSynth.sin(swellPhase);
            left = waterGain * swell * (.40 * lowL + .13 * midL + .024 * (nl - midL));
            right = waterGain * swell * (.40 * lowR + .13 * midR + .024 * (nr - midR));
            if (--dropCountdown <= 0) {
                for (Drop d : drops) if (d.envelope < .00001) {
                    d.phase = 0; d.frequency = 650 + random.nextDouble() * 1450;
                    d.envelope = .025 + random.nextDouble() * .04;
                    d.age = 0; d.pan = .1 + random.nextDouble() * .8; break;
                }
                dropCountdown = FocusSynth.RATE * (.045 + random.nextDouble() * .18);
            }
            for (Drop d : drops) {
                if (d.envelope < .00001) continue;
                d.phase += d.frequency / FocusSynth.RATE;
                if (d.phase >= 1) d.phase -= 1;
                d.frequency *= .99991; d.envelope *= .99945;
                double sound = FocusSynth.sin(d.phase) * d.envelope * Math.min(1, d.age / 160.0) * waterGain;
                d.age++; left += sound * (1 - d.pan); right += sound * d.pan;
            }
        }
        if (bamboo && !bambooEnabled) knockCountdown = FocusSynth.RATE * 3.0;
        bambooEnabled = bamboo;
        if (bamboo && --knockCountdown <= 0) {
            knockAge = 0; knockDecay = 1; knockPitch = .90 + random.nextDouble() * .18; knockCount++;
            // Slight timing variations rather than a metronomic repeating sample.
            knockCountdown = FocusSynth.RATE * intervalSeconds * (.92 + random.nextDouble() * .16);
        }
        if (bambooGain > .000001) {
            double pour = 0;
            if (bamboo && knockCountdown < FocusSynth.RATE * .85) {
                double progress = 1 - knockCountdown / (FocusSynth.RATE * .85);
                pour = .05 * Math.sin(progress * Math.PI) * (random.nextDouble() * 2 - 1);
            }
            double knock = 0;
            if (knockAge < .6) {
                double attack = Math.min(1, knockAge / .0015);
                knock = attack * knockDecay * (.24 * FocusSynth.sin(knockAge * 610 * knockPitch)
                    + .14 * FocusSynth.sin(knockAge * 1470 * knockPitch)
                    + .07 * FocusSynth.sin(knockAge * 2490 * knockPitch));
                // Short contact noise adds the hollow 'kon' attack.
                if (knockAge < .008) knock += attack * .13 * (1 - knockAge / .008) * (random.nextDouble() * 2 - 1);
                knockAge += 1.0 / FocusSynth.RATE; knockDecay *= .99960;
            }
            left += bambooGain * (pour * .65 + knock * .68);
            right += bambooGain * (pour * .75 + knock * .78);
        } else if (knockAge < .6) {
            // Muted hits still expire, so raising the volume never replays an old hit.
            knockAge += 1.0 / FocusSynth.RATE;
            knockDecay *= .99960;
        }
    }
    private static final class Drop { double phase, frequency, envelope, pan; int age; }
}
