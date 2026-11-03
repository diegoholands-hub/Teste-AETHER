package br.com.aeris.drone;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reverse-engineered KY UFO network formats. 7099 devices and 8800 FLOW-WIFI
 * devices use DIFFERENT packet formats. No fictitious telemetry is generated.
 * See README for original hardware research and limitations.
 */
final class DroneProtocol {
    enum Profile { KY_7099, FLOW_8800 }
    private final AtomicInteger sequence = new AtomicInteger();
    static byte axis(float value) {
        return (byte)Math.max(1,Math.min(255,Math.round(128f + Math.max(-1f,Math.min(1f,value))*127f)));
    }
    static byte[] heartbeat() { return new byte[]{1,1}; }
    static byte[] stopTransmission() { return new byte[]{8,1}; }
    static byte[] camera(boolean front) { return new byte[]{6,(byte)(front?1:2)}; }
    byte[] flight(Profile profile,float roll,float pitch,float throttle,float yaw,int flags,boolean active) {
        int r=axis(roll)&255,p=axis(pitch)&255,t=active?(axis(throttle)&255):(profile==Profile.FLOW_8800?0:128),y=axis(yaw)&255;
        if(profile==Profile.KY_7099){
            byte[] out=new byte[21];
            out[0]=3;out[1]=0x66;out[2]=0x14;
            out[3]=(byte)r;out[4]=(byte)p;out[5]=(byte)t;out[6]=(byte)y;
            out[7]=(byte)flags;out[8]=0;
            out[19]=(byte)(r^p^t^y^flags);
            out[20]=(byte)0x99;
            return out;
        }
        byte[] out=new byte[88];
        out[0]=(byte)0xEF;out[1]=2;out[2]=0x58;out[3]=0;
        out[4]=2;out[5]=2;out[6]=0;out[7]=1;
        int seq=sequence.incrementAndGet()&65535;
        out[12]=(byte)seq;out[13]=(byte)(seq>>8);
        out[16]=0x14;out[17]=0;
        out[18]=0x66;out[19]=0x14;
        out[20]=(byte)r;out[21]=(byte)p;out[22]=(byte)t;out[23]=(byte)y;
        out[24]=(byte)flags;out[25]=2;
        out[36]=(byte)(r^p^t^y^flags^2);
        out[37]=(byte)0x99;
        return out;
    }
}
