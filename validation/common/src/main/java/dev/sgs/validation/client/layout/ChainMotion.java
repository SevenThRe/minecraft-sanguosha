package dev.sgs.validation.client.layout;

/** Finite animation; reconnect uses a settled frame, never a looping effect. */
public final class ChainMotion {
    public static final long WRAP_MILLIS=480, RELEASE_MILLIS=360;
    private ChainMotion() {}
    public record Frame(float reveal,float clasp,float flash,float split,float drop,float opacity) {}
    private static float clamp(float value){return Math.max(0,Math.min(1,value));}
    public static Frame at(long elapsed,boolean releasing){
        if(releasing){
            float t=clamp(elapsed/(float)RELEASE_MILLIS);
            return new Frame(1,1,0,t,t*t*9,1-t);
        }
        float t=clamp(elapsed/(float)WRAP_MILLIS),r=clamp(t/.65f);
        float reveal=1-(1-r)*(1-r)*(1-r),clasp=clamp((t-.22f)/.28f);
        // One brief glint after closure, continuously rising and falling rather than popping to full brightness.
        float flash=clamp(1-Math.abs((t-.62f)/.30f));
        return new Frame(reveal,clasp,flash,0,0,1);
    }
}
