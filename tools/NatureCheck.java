import dev.enadorry.infinitefocus.FocusSynth;
import dev.enadorry.infinitefocus.NatureSynth;
import java.io.*;
import java.util.Arrays;

/** Audible-output checks: isolated sources, channel controls, timing and stop. */
public class NatureCheck {
    private static FocusSynth.Settings settings(boolean water, boolean bamboo, double w, double b) {
        return new FocusSynth.Settings(68,35,false,false,0,water,bamboo,w,b,10);
    }
    public static void main(String[] args)throws Exception {
        short[] x=new short[1024], y=new short[1024];
        FocusSynth silent=new FocusSynth(10), water=new FocusSynth(10), bamboo=new FocusSynth(10);
        silent.configure(settings(false,false,1,1)); water.configure(settings(true,false,.8,0)); bamboo.configure(settings(false,true,0,.8));
        long we=0,be=0; boolean stereo=false; int peak=0;
        for(int i=0;i<5625;i++) { // 60 seconds with BGM muted.
            silent.render(y,512); for(short v:y)if(v!=0)throw new AssertionError("all sources off must be silent");
            water.render(x,512);for(int j=0;j<x.length;j++){we+=(long)x[j]*x[j];peak=Math.max(peak,Math.abs((int)x[j]));}for(int j=0;j<x.length;j+=2)stereo|=x[j]!=x[j+1];
            bamboo.render(x,512);for(short v:x){be+=(long)v*v;peak=Math.max(peak,Math.abs((int)v));}
        }
        if(we==0||be==0||!stereo||peak>=32767)throw new AssertionError("isolated nature PCM");
        water.configure(settings(true,true,0,0));for(int i=0;i<600;i++)water.render(x,512);
        for(short v:x)if(v!=0)throw new AssertionError("zero-volume nature controls");
        FocusSynth a=new FocusSynth(77),b=new FocusSynth(77);
        b.configure(new FocusSynth.Settings(68,35,false,false,.65,true,true,0,0,10));
        for(int i=0;i<1000;i++){a.render(x,512);b.render(y,512);if(!Arrays.equals(x,y))throw new AssertionError("nature RNG must not change the music");}
        NatureSynth n=new NatureSynth(1);for(int i=0;i<FocusSynth.RATE*60;i++)n.sample(false,true,0,.8,10);
        if(n.knockCount()<6||n.knockCount()>7)throw new AssertionError("bamboo interval");
        int count=n.knockCount();for(int i=0;i<FocusSynth.RATE*15;i++)n.sample(false,false,0,.8,10);
        if(n.knockCount()!=count)throw new AssertionError("disabled bamboo still triggered");
        NatureSynth muted=new NatureSynth(9);
        for(int i=0;i<FocusSynth.RATE*5;i++)muted.sample(false,true,0,0,10);
        for(int i=0;i<FocusSynth.RATE;i++){muted.sample(false,true,0,1,10);if(muted.left!=0||muted.right!=0)throw new AssertionError("unmuting replayed an expired knock");}
        bamboo.fadeOut();for(int i=0;i<200;i++)bamboo.render(x,512);
        for(short v:x)if(v!=0)throw new AssertionError("stop must fade nature to silence");
        System.out.printf("PASS: independent water/bamboo output; stereo water; zero-volume/off/stop controls; music unchanged by muted nature; 60s bamboo timing (%d knocks); peak=%d.%n",count,peak);
        if(args.length>0)preview(args[0]);
    }
    private static void le(DataOutputStream o,int v,int bytes)throws IOException{for(int i=0;i<bytes;i++)o.writeByte(v>>>(i*8));}
    private static void preview(String path)throws Exception {
        int frames=FocusSynth.RATE*20, bytes=frames*4;short[] pcm=new short[1024];
        FocusSynth engine=new FocusSynth(20261003);engine.configure(new FocusSynth.Settings(68,35,false,false,0,true,true,.8,.7,10));
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)))){
            out.writeBytes("RIFF");le(out,36+bytes,4);out.writeBytes("WAVEfmt ");le(out,16,4);le(out,1,2);le(out,2,2);le(out,FocusSynth.RATE,4);le(out,FocusSynth.RATE*4,4);le(out,4,2);le(out,16,2);out.writeBytes("data");le(out,bytes,4);
            for(int f=0;f<frames;f+=512){if(f>=frames-FocusSynth.RATE)engine.fadeOut();int len=Math.min(512,frames-f);engine.render(pcm,len);for(int i=0;i<len*2;i++)le(out,pcm[i],2);}
        }
    }
}
