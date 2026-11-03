package br.com.aeris.drone;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.wifi.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.videolan.libvlc.*;
import org.videolan.libvlc.util.VLCVideoLayout;

public final class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private WifiManager wifi;
    private ConnectivityManager cm;
    private Network currentNetwork;
    private ConnectivityManager.NetworkCallback connectionCallback;
    private DroneLink drone;
    private DroneProtocol.Profile profile=DroneProtocol.Profile.KY_7099;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status,networkLabel,telemetry,videoStatus;
    private LinearLayout found;
    private VLCVideoLayout videoLayout;
    private LibVLC vlc;
    private MediaPlayer player;
    private TextToSpeech voice;
    private StickView left,right;
    private Button enableButton,assistantButton,launchButton;
    private boolean controls=false,assistant=false,front=true;
    private float roll,pitch,throttle,yaw;
    private int bg=Color.rgb(7,16,28),panel=Color.rgb(18,34,50),cyan=Color.rgb(82,224,224);
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        voice=new TextToSpeech(this,this);
        makeUi();requestAccess();
    }
    @Override public void onInit(int s){if(s==TextToSpeech.SUCCESS)voice.setLanguage(new Locale("pt","BR"));}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int r){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;
    }
    private TextView text(String s,int size,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(4),dp(7),dp(4),dp(7));return v;
    }
    private Button btn(String name,int color,Runnable click){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setTextSize(13);b.setBackground(shape(color,12));b.setOnClickListener(v->{tone();click.run();});
        return b;
    }
    private void add(LinearLayout parent,View child,int height){
        parent.addView(child,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));
    }
    private LinearLayout panel(LinearLayout root){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(10),dp(12),dp(11));box.setBackground(shape(panel,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(7));root.addView(box,lp);
        return box;
    }
    private void makeUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(15),dp(18),dp(15),dp(24));root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        TextView title=text("AERIS  /  PILOT",26,cyan);title.setTypeface(null,Typeface.BOLD);add(root,title,-2);
        add(root,text("Controle Wi-Fi real • KY UFO / FLOW-WIFI",12,Color.LTGRAY),-2);
        LinearLayout net=panel(root);
        add(net,text("01  CONECTAR À AERONAVE",16,cyan),-2);
        networkLabel=text("Nenhuma rede selecionada",13,Color.WHITE);add(net,networkLabel,-2);
        Button scan=btn("Procurar redes Wi-Fi",Color.rgb(32,91,120),this::scan);
        add(net,scan,45);
        found=new LinearLayout(this);found.setOrientation(LinearLayout.VERTICAL);add(net,found,-2);
        add(net,text("Perfis de protocolo — escolha corretamente:",12,Color.LTGRAY),-2);
        String[] names={"KY UFO 7099 + câmera RTSP (192.168.1.1)","FLOW-WIFI 8800 (192.168.169.1) — vídeo incompatível"};
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);spinner.setAdapter(adapter);add(net,spinner,50);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> a){}
            public void onItemSelected(android.widget.AdapterView<?> a,View v,int pos,long id){profile=pos==0?DroneProtocol.Profile.KY_7099:DroneProtocol.Profile.FLOW_8800;}
        });
        LinearLayout cam=panel(root);
        add(cam,text("02  CÂMERA AO VIVO",16,cyan),-2);
        videoLayout=new VLCVideoLayout(this);
        videoLayout.setBackgroundColor(Color.BLACK);
        add(cam,videoLayout,210);
        videoStatus=text("Sem vídeo. O app não exibe imagens simuladas.",12,Color.LTGRAY);add(cam,videoStatus,-2);
        Button camera=btn("Alternar câmera frontal/inferior",Color.rgb(38,76,99),()->{
            if(drone!=null && drone.isRunning()){front=!front;drone.camera(front);msg(front?"Comando: câmera frontal":"Comando: câmera inferior");}
        });add(cam,camera,44);
        LinearLayout command=panel(root);
        add(command,text("03  CONTROLE MANUAL",16,cyan),-2);
        status=text("Conecte ao Wi-Fi do drone para começar.",13,Color.WHITE);add(command,status,-2);
        telemetry=text("Aguardando resposta UDP real",12,Color.LTGRAY);add(command,telemetry,-2);
        enableButton=btn("Habilitar controles",Color.rgb(22,113,97),this::toggleControl);
        add(command,enableButton,47);
        LinearLayout sticks=new LinearLayout(this);sticks.setOrientation(LinearLayout.HORIZONTAL);
        left=new StickView(this,"ALTITUDE / GIRO");right=new StickView(this,"DIREÇÃO / AVANÇO");
        sticks.addView(left,new LinearLayout.LayoutParams(0,dp(190),1));sticks.addView(right,new LinearLayout.LayoutParams(0,dp(190),1));
        add(command,sticks,-2);
        left.setListener((x,y)->{yaw=x;throttle=y;sendAxes();});
        right.setListener((x,y)->{roll=x;pitch=y;sendAxes();});
        Button hover=btn("Centralizar comandos / hover",Color.rgb(35,71,94),()->{resetSticks();say("Vou centralizar os controles.");});add(command,hover,46);
        launchButton=btn("Decolar / pousar (somente FLOW-WIFI)",Color.rgb(38,99,111),this::launch);add(command,launchButton,48);
        LinearLayout assisted=panel(root);
        add(assisted,text("04  PILOTO ASSISTIDO",16,cyan),-2);
        add(assisted,text("Modo experimental baseado em regras e telemetria de conexão; NÃO é IA visual treinada nem evita obstáculos. Mantém comandos neutros e narra decisões.",12,Color.LTGRAY),-2);
        assistantButton=btn("Ativar piloto assistido",Color.rgb(39,83,115),this::toggleAssistant);add(assisted,assistantButton,50);
        Button emergency=btn("■  PARADA DE EMERGÊNCIA",Color.rgb(177,34,45),this::emergency);add(root,emergency,60);
        add(root,text("ATENÇÃO: parada pode desligar os motores imediatamente e derrubar o drone. Primeiro teste sem hélices. Nunca use o piloto automático próximo de pessoas.",11,Color.rgb(245,172,157)),-2);
        ui.postDelayed(this::refreshLink,1100);
    }
    private void tone(){
        try{android.media.ToneGenerator t=new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION,55);
            t.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,90);ui.postDelayed(t::release,150);}catch(Exception ignored){}
    }
    private void msg(String m){ui.post(()->{status.setText(m);});}
    private void say(String m){ui.post(()->{
        status.setText(m);
        if(voice!=null)voice.speak(m,TextToSpeech.QUEUE_FLUSH,null,"aeris-"+SystemClock.uptimeMillis());
    });}
    private void requestAccess(){
        ArrayList<String> perms=new ArrayList<>();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)perms.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if(!perms.isEmpty())requestPermissions(perms.toArray(new String[0]),8);
    }
    @Override public void onRequestPermissionsResult(int req,String[] names,int[] results){super.onRequestPermissionsResult(req,names,results);if(req==8&&results.length>0)scan();}
    private void scan(){
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestAccess();return;}
        found.removeAllViews();add(found,text("Buscando redes próximas...",12,Color.LTGRAY),-2);
        try{wifi.startScan();}catch(Exception ignored){}
        ui.postDelayed(this::showNetworks,1600);
    }
    private void showNetworks(){
        found.removeAllViews();
        try{
            List<ScanResult> results=wifi.getScanResults();
            TreeSet<String> ssids=new TreeSet<>();
            for(ScanResult r:results) if(r.SSID!=null&&!r.SSID.trim().isEmpty())ssids.add(r.SSID);
            if(ssids.isEmpty()){add(found,text("Nenhuma rede listada. Ative a localização e o Wi-Fi do celular.",12,Color.LTGRAY),-2);return;}
            for(String ssid:ssids){
                Button b=btn("◉  "+ssid,Color.rgb(26,52,68),()->chooseNetwork(ssid));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(45));lp.setMargins(0,dp(3),0,dp(2));found.addView(b,lp);
            }
        }catch(SecurityException ex){add(found,text("Autorize a localização para procurar redes.",13,Color.WHITE),-2);requestAccess();}
    }
    private void chooseNetwork(String ssid){
        EditText password=new EditText(this);password.setSingleLine(true);password.setHint("Deixe em branco para rede aberta");
        password.setInputType(129);
        new AlertDialog.Builder(this).setTitle("Conectar: "+ssid).setMessage("O Android exibirá uma confirmação do sistema. Mantenha a conexão mesmo sem internet.").setView(password)
            .setNegativeButton("Cancelar",null).setPositiveButton("Conectar",(d,w)->connect(ssid,password.getText().toString())).show();
    }
    private void connect(String ssid,String password){
        disconnect();
        networkLabel.setText("Solicitando conexão à rede: "+ssid);
        try{
            WifiNetworkSpecifier.Builder builder=new WifiNetworkSpecifier.Builder().setSsid(ssid);
            if(!password.isEmpty())builder.setWpa2Passphrase(password);
            NetworkRequest request=new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(builder.build()).build();
            connectionCallback=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network n){
                    currentNetwork=n;cm.bindProcessToNetwork(n);
                    ui.post(()->{
                        networkLabel.setText("Wi-Fi conectado: "+ssid);
                        msg("Conexão estabelecida. Iniciando canal UDP...");
                        startLink();
                    });
                }
                @Override public void onLost(Network n){ui.post(()->{disableControl();stopVideo();if(drone!=null)drone.close();drone=null;networkLabel.setText("Sinal Wi-Fi perdido.");msg("Conexão perdida — comandos interrompidos.");});}
                @Override public void onUnavailable(){ui.post(()->networkLabel.setText("Android não autorizou a conexão. Tente novamente."));}
            };
            cm.requestNetwork(request,connectionCallback,25000);
        }catch(Exception ex){msg("Falha ao conectar: "+ex.getMessage());}
    }
    private void startLink(){
        try{
            drone=new DroneLink(currentNetwork,profile,new DroneLink.Listener(){
                @Override public void status(String s){msg(s);}
                @Override public void packet(byte[] data){ui.post(()->telemetry.setText("Resposta REAL do drone: "+data.length+" bytes • sinal UDP recebido"));}
                @Override public void disconnected(String reason){ui.post(()->{disableControl();telemetry.setText(reason);});}
            });
            drone.start();
            startVideo();
        }catch(Exception e){msg("Não foi possível abrir UDP: "+e.getMessage());}
    }
    private void startVideo(){
        stopVideo();
        if(profile!=DroneProtocol.Profile.KY_7099){
            videoStatus.setText("Este protocolo usa vídeo fragmentado proprietário. Não há imagem implementada; nada simulado.");return;
        }
        try{
            ArrayList<String> options=new ArrayList<>();options.add("--network-caching=220");options.add("--rtsp-tcp");
            vlc=new LibVLC(this,options);player=new MediaPlayer(vlc);
            player.attachViews(videoLayout,null,false,false);
            player.setEventListener(event->{
                if(event.type==MediaPlayer.Event.Playing)ui.post(()->videoStatus.setText("Vídeo RTSP recebido da câmera"));
                else if(event.type==MediaPlayer.Event.EncounteredError)ui.post(()->videoStatus.setText("A câmera RTSP não respondeu neste modelo."));
            });
            Media media=new Media(vlc,Uri.parse("rtsp://192.168.1.1:7070/webcam"));
            media.setHWDecoderEnabled(true,false);media.addOption(":network-caching=220");
            player.setMedia(media);media.release();player.play();
            videoStatus.setText("Conectando ao fluxo RTSP REAL do drone...");
        }catch(Exception ex){videoStatus.setText("Falha ao abrir câmera: "+ex.getMessage());}
    }
    private void stopVideo(){
        try{if(player!=null){player.stop();player.detachViews();player.release();player=null;}}catch(Exception ignored){}
        try{if(vlc!=null){vlc.release();vlc=null;}}catch(Exception ignored){}
    }
    private void sendAxes(){if(drone!=null&&controls&&!assistant)drone.axes(roll,pitch,throttle,yaw);}
    private void resetSticks(){if(left!=null)left.reset();if(right!=null)right.reset();roll=pitch=throttle=yaw=0;if(drone!=null)drone.neutral();}
    private void toggleControl(){
        if(controls){disableControl();say("Controles manuais desativados.");return;}
        if(drone==null||!drone.isFresh()||drone.isEmergency()){msg("Sem resposta recente do drone. Não vou habilitar os motores.");return;}
        controls=true;drone.enable(true);enableButton.setText("Desativar controles");
        say("Comunicação confirmada. Controles manuais habilitados.");
    }
    private void disableControl(){controls=false;assistant=false;resetSticks();if(drone!=null)drone.enable(false);
        if(enableButton!=null)enableButton.setText("Habilitar controles");if(assistantButton!=null)assistantButton.setText("Ativar piloto assistido");}
    private void launch(){
        if(drone==null||profile!=DroneProtocol.Profile.FLOW_8800||!controls||!drone.isFresh()){msg("Decolagem automática disponível apenas no protocolo FLOW-WIFI confirmado.");return;}
        new AlertDialog.Builder(this).setTitle("Confirmar ação no drone").setMessage("O mesmo comando alterna DECOLAR/POUSAR. Verifique o estado físico da aeronave, remova pessoas da área e confirme.")
            .setNegativeButton("Cancelar",null).setPositiveButton("Enviar comando real",(d,w)->{
                say("Vou enviar o comando de decolagem ou pouso.");
                ui.postDelayed(()->{if(drone!=null&&controls&&drone.isFresh())drone.takeoffOrLand();},1700);
            }).show();
    }
    private void toggleAssistant(){
        if(assistant){assistant=false;assistantButton.setText("Ativar piloto assistido");resetSticks();say("Piloto assistido desligado. Controles centralizados.");return;}
        if(!controls||drone==null||!drone.isFresh()){msg("É preciso conectar e habilitar controles antes.");return;}
        assistant=true;resetSticks();assistantButton.setText("Desativar piloto assistido");
        say("Vou centralizar todos os eixos e monitorar a resposta do drone. Não tenho sensores de obstáculos ou GPS.");
        // Intentionally NO autonomous blind movement. Voice reports decisions based on real packets.
    }
    private void refreshLink(){
        if(drone!=null && drone.isRunning()){
            if(drone.isFresh())telemetry.setText("Conexão UDP REAL • último retorno há "+drone.responseAge()+" ms");
            else if(!drone.isEmergency())telemetry.setText("Sem resposta recente do drone — não habilite voo.");
            if(assistant){if(!drone.isFresh()){assistant=false;resetSticks();say("Perdi a confirmação de comunicação. Parei o piloto assistido.");}}
        }
        ui.postDelayed(this::refreshLink,1200);
    }
    private void emergency(){
        assistant=false;resetSticks();
        if(drone!=null)drone.emergencyStop();
        controls=false;
        enableButton.setText("Emergência: reinicie o drone");assistantButton.setText("Piloto assistido desligado");
        say("Emergência! Estou enviando o comando para desligar os motores.");
    }
    private void disconnect(){
        disableControl();stopVideo();
        if(drone!=null){drone.close();drone=null;}
        if(connectionCallback!=null){try{cm.unregisterNetworkCallback(connectionCallback);}catch(Exception ignored){}connectionCallback=null;}
        cm.bindProcessToNetwork(null);currentNetwork=null;
    }
    @Override protected void onPause(){super.onPause();if(controls)disableControl();}
    @Override protected void onDestroy(){disconnect();if(voice!=null){voice.stop();voice.shutdown();}super.onDestroy();}
}
