package br.com.aeris.drone;

import android.net.Network;
import android.os.SystemClock;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real UDP transport bound to the selected drone Wi-Fi network. */
final class DroneLink {
    interface Listener { void status(String message); void packet(byte[] bytes); void disconnected(String reason); }
    private final Network network;
    private final DroneProtocol.Profile profile;
    private final Listener listener;
    private final DroneProtocol format=new DroneProtocol();
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private final AtomicBoolean emergency=new AtomicBoolean(false);
    private DatagramSocket socket;
    private InetAddress address;
    private volatile long lastReply=0;
    private volatile float roll=0,pitch=0,throttle=0,yaw=0;
    private volatile boolean enabled=false;
    private volatile long actionUntil=0;
    private volatile int actionFlags=0;
    private volatile long lastFlight=0;
    private volatile long started=0;

    DroneLink(Network network,DroneProtocol.Profile profile,Listener listener) {
        this.network=network;this.profile=profile;this.listener=listener;
    }
    boolean isFresh() {return running.get() && SystemClock.elapsedRealtime()-lastReply<3500;}
    long responseAge() {return lastReply==0?-1:SystemClock.elapsedRealtime()-lastReply;}
    boolean isRunning() {return running.get();}
    boolean isEmergency() {return emergency.get();}
    void start() throws Exception {
        address=InetAddress.getByName(profile==DroneProtocol.Profile.KY_7099?"192.168.1.1":"192.168.169.1");
        socket=new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(0));
        network.bindSocket(socket);
        socket.setSoTimeout(600);
        running.set(true);
        started=SystemClock.elapsedRealtime();
        Thread rx=new Thread(()->{
            byte[] buf=new byte[512];
            while(running.get()){
                try {
                    DatagramPacket packet=new DatagramPacket(buf,buf.length);
                    socket.receive(packet);
                    if(packet.getLength()>0){
                        lastReply=SystemClock.elapsedRealtime();
                        byte[] data=new byte[packet.getLength()];
                        System.arraycopy(packet.getData(),packet.getOffset(),data,0,packet.getLength());
                        listener.packet(data);
                    }
                }catch(SocketTimeoutException ignored){}
                catch(Exception ex){if(running.get())listener.disconnected("Falha ao receber dados: "+ex.getMessage());break;}
            }
        },"aeris-drone-receiver");
        rx.setDaemon(true);rx.start();
        scheduler.scheduleAtFixedRate(this::tick,0,40,TimeUnit.MILLISECONDS);
        listener.status("Canal UDP real iniciado em "+address.getHostAddress());
    }
    synchronized void axes(float r,float p,float t,float y){
        if(!emergency.get()) {roll=r;pitch=p;throttle=t;yaw=y;}
    }
    void enable(boolean value){enabled=value&&!emergency.get();if(!enabled)neutral();}
    void neutral(){roll=0;pitch=0;throttle=0;yaw=0;}
    void takeoffOrLand() {
        if(profile!=DroneProtocol.Profile.FLOW_8800 || !enabled || !isFresh() || emergency.get())return;
        actionFlags=1; actionUntil=SystemClock.elapsedRealtime()+1000;
    }
    void camera(boolean front){send(DroneProtocol.camera(front),7099);}
    private synchronized void send(byte[] payload,int port) {
        if(!running.get()||socket==null)return;
        try{socket.send(new DatagramPacket(payload,payload.length,address,port));}
        catch(Exception ex){listener.status("UDP: "+ex.getMessage());}
    }
    private void tick() {
        if(!running.get())return;
        long now=SystemClock.elapsedRealtime();
        if(now/1000 != (now-40)/1000)send(DroneProtocol.heartbeat(),7099);
        if(emergency.get()) {
            if(now<actionUntil) {
                int bit=profile==DroneProtocol.Profile.FLOW_8800?2:2;
                send(format.flight(profile,0,0,0,0,bit,true),profile==DroneProtocol.Profile.FLOW_8800?8800:7099);
            } else {
                send(DroneProtocol.stopTransmission(),7099);
                running.set(false);
                if(socket!=null)socket.close();
                scheduler.shutdown();
                listener.status("PARADA DE EMERGÊNCIA ENVIADA. Desligue e reinicie o drone para novo voo.");
            }
            return;
        }
        if(now-started>5500 && !isFresh()) {
            enabled=false; neutral();
            listener.disconnected("Sem resposta do drone. Controles bloqueados.");
        }
        if(profile==DroneProtocol.Profile.FLOW_8800) {
            if(enabled || now-lastFlight>=2000) {
                int flag=enabled && now<actionUntil?actionFlags:0;
                if(now>=actionUntil)actionFlags=0;
                send(format.flight(profile,roll,pitch,throttle,yaw,flag,enabled),8800);
                lastFlight=now;
            }
        }else if(enabled) {
            send(format.flight(profile,roll,pitch,throttle,yaw,0,true),7099);
        }
    }
    void emergencyStop(){
        if(!running.get()||emergency.getAndSet(true))return;
        enabled=false;neutral();
        actionUntil=SystemClock.elapsedRealtime()+1200;
        // Immediately fire, in addition to the 40ms loop, to minimize delay.
        send(format.flight(profile,0,0,0,0,2,true),profile==DroneProtocol.Profile.FLOW_8800?8800:7099);
    }
    void close(){
        if(!running.getAndSet(false))return;
        enabled=false;neutral();
        send(DroneProtocol.stopTransmission(),7099);
        scheduler.shutdownNow();
        if(socket!=null)socket.close();
    }
}
