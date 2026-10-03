import dev.enadorry.infinitefocus.FocusSynth;
import java.io.*;
import java.util.Arrays;

public class EngineCheck {
    public static void main(String[] args) throws Exception {
        FocusSynth a = new FocusSynth(42), b = new FocusSynth(42), c = new FocusSynth(43);
        short[] x = new short[1024], y = new short[1024], z = new short[1024]; boolean changed = false;
        long energy = 0; int peak = 0; long start = System.nanoTime();
        for (int block=0;block<56250;block++) { // Ten minutes, including configuration changes.
            if (block == 12000) { FocusSynth.Settings s = new FocusSynth.Settings(90,80,true,true,.9); a.configure(s); b.configure(s); }
            a.render(x,512); b.render(y,512);
            if (!Arrays.equals(x,y)) throw new AssertionError("seed is not deterministic");
            if (block < 1000) { c.render(z,512); changed |= !Arrays.equals(x,z); }
            for(short v:x) { peak=Math.max(peak,Math.abs((int)v)); energy+=(long)v*v; }
        }
        if(!changed || energy==0 || peak>=32767 || a.generatedFrames()!=28800000L) throw new AssertionError("PCM invariants");
        a.fadeOut(); for(int i=0;i<150;i++)a.render(x,512);
        if(!a.isQuiet())throw new AssertionError("fade out failed");
        System.out.printf("PASS: 10-minute deterministic render; peak=%d, RMS=%.1f; two engines in %.2fs; fade passed.%n",peak,Math.sqrt(energy/(56250.0*1024)),(System.nanoTime()-start)/1e9);
        if(args.length>0) preview(args[0]);
    }
    private static void le(DataOutputStream o,int v,int bytes)throws IOException { for(int i=0;i<bytes;i++)o.writeByte(v>>>(i*8)); }
    private static void preview(String path)throws Exception {
        int seconds=45, frames=seconds*FocusSynth.RATE, bytes=frames*4;
        FocusSynth engine=new FocusSynth(20261003); short[] pcm=new short[1024];
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))) {
            out.writeBytes("RIFF");le(out,36+bytes,4);out.writeBytes("WAVEfmt ");le(out,16,4);le(out,1,2);le(out,2,2);le(out,FocusSynth.RATE,4);le(out,FocusSynth.RATE*4,4);le(out,4,2);le(out,16,2);out.writeBytes("data");le(out,bytes,4);
            for(int frame=0;frame<frames;frame+=512) { if(frame>=frames-FocusSynth.RATE)engine.fadeOut();int n=Math.min(512,frames-frame);engine.render(pcm,n);for(int j=0;j<n*2;j++)le(out,pcm[j],2); }
        }
        System.out.println("Preview: " + path);
    }
}
