package com.coconut.katseye;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  private final UUID serviceId=UUID.fromString("00010203-0405-0607-0809-0a0b0c0d1911");
  private final UUID writeId=UUID.fromString("00010203-0405-0607-0809-0a0b0c0d2b19");
  private final int[] palette={0xffff3399,0xffab66ff,0xff368bff,0xff32efca,0xffffbb44,0xffffffff,0xffff3333,0xff000000};
  private BluetoothAdapter adapter;
  private BluetoothLeScanner scanner;
  private BluetoothGatt gatt;
  private BluetoothGattCharacteristic writeChar;
  private TextView status,battery;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private boolean scanning=false;
  private volatile boolean musicOn=false;
  private volatile int songMode=0; // 0: general, 1: Gnarly, 2: Pinky Up, 3: Gabriela, 4: Touch, 5: That Way
  private TextView selectedSong;
  private AudioRecord recorder;
  private Thread musicThread;
  private int red=255,green=40,blue=150,brightness=100;

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(0xffd5b8ef);
    getWindow().setNavigationBarColor(0xffd5b8ef);
    BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
    adapter=manager.getAdapter();
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(28,28,28,28);
    root.setBackgroundColor(0xffd5b8ef);
    ScrollView scroll=new ScrollView(this);
    scroll.addView(root);
    setContentView(scroll);
    TextView title=label("KATSEYE ✦",30,0xff582d78);
    title.setGravity(Gravity.CENTER);
    root.addView(title);
    TextView subtitle=label("COCONUT LIGHTSTICK · beta 0.1",12,0xff56336c);
    subtitle.setGravity(Gravity.CENTER);
    root.addView(subtitle);
    status=label("Sin conectar",17,0xff422357);
    root.addView(status);
    battery=label("Batería: pendiente de lectura",14,0xff56336c);
    root.addView(battery);
    button("♡ CONECTAR LIGHTSTICK",root).setOnClickListener(v->connect());
    root.addView(label("COLORES",20,0xffffc3e4));
    LinearLayout colors=new LinearLayout(this);
    colors.setOrientation(LinearLayout.HORIZONTAL);
    root.addView(colors);
    for(int c:palette){
      Button colorButton=new Button(this);
      colorButton.setText("●");
      colorButton.setTextColor(c);
      colorButton.setTextSize(25);
      colors.addView(colorButton,new LinearLayout.LayoutParams(0,65,1));
      colorButton.setOnClickListener(v->{red=Color.red(c);green=Color.green(c);blue=Color.blue(c);sendColor();});
    }
    button("🎨 ELEGIR CUALQUIER COLOR",root).setOnClickListener(v->showColorPicker());
    root.addView(label("BRILLO",20,0xffffc3e4));
    SeekBar bar=new SeekBar(this);
    bar.setMax(100);
    bar.setProgress(100);
    root.addView(bar);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onStartTrackingTouch(SeekBar s){}
      public void onStopTrackingTouch(SeekBar s){sendColor();}
      public void onProgressChanged(SeekBar s,int progress,boolean fromUser){brightness=progress;}
    });
    button("APAGAR LUZ",root).setOnClickListener(v->send(0,0,0));
    button("PRUEBA DE COLORES",root).setOnClickListener(v->{
      for(int i=0;i<palette.length;i++){
        final int c=palette[i];
        handler.postDelayed(()->send(Color.red(c),Color.green(c),Color.blue(c)),i*650L);
      }
    });
    root.addView(label("MODOS KATSEYE ♡",20,0xffffc3e4));
    selectedSong=label("Seleccionado: modo general",14,0xff56336c);
    root.addView(selectedSong);
    String[] songs={"GENERAL","GNARLY 💚","PINKY UP 💗","GABRIELA ❤️","TOUCH 💜","THAT WAY 💜💗"};
    for(int i=0;i<songs.length;i++){
      final int mode=i;
      Button preset=button(songs[i],root);
      preset.setOnClickListener(v->{
        songMode=mode;
        selectedSong.setText("Seleccionado: "+songs[mode]);
        if(!musicOn)sendPresetPreview(mode);
        else say("Modo "+songs[mode]+" activo");
      });
    }
    root.addView(label("MODO CONCIERTO 🎵",20,0xffffc3e4));
    root.addView(label("Reacciona al volumen captado por el micrófono. No sincroniza canciones por título ni captura audio interno.",14,0xff56336c));
    Button music=button("INICIAR MODO MÚSICA",root);
    music.setOnClickListener(v->{
      if(musicOn){stopMusic();music.setText("INICIAR MODO MÚSICA");}
      else if(startMusic()){music.setText("DETENER MODO MÚSICA");}
    });
    root.addView(label("Prototipo experimental. Protocolo Bluetooth sin verificar con el dispositivo real. No afiliado a KATSEYE.",11,0xffa593b2));
    requestBluetoothPermissions();
  }
  private void showColorPicker(){
    LinearLayout layout=new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(30,12,30,12);
    TextView preview=label("● COLOR PERSONALIZADO",20,Color.rgb(red,green,blue));
    preview.setGravity(Gravity.CENTER);
    layout.addView(preview);
    final float[] hsv=new float[3];
    Color.RGBToHSV(red,green,blue,hsv);
    String[] names={"Tono (todos los colores)","Saturación (intensidad)","Luminosidad"};
    int[] maximum={360,100,100};
    for(int i=0;i<3;i++){
      final int index=i;
      TextView caption=label(names[i],14,Color.DKGRAY);
      layout.addView(caption);
      SeekBar slider=new SeekBar(this);
      slider.setMax(maximum[i]);
      slider.setProgress(Math.round(hsv[i]*(i==0?1:100)));
      layout.addView(slider);
      slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
        public void onStartTrackingTouch(SeekBar b){}
        public void onStopTrackingTouch(SeekBar b){}
        public void onProgressChanged(SeekBar b,int value,boolean fromUser){
          hsv[index]=index==0?value:value/100f;
          int c=Color.HSVToColor(hsv);
          preview.setTextColor(c);
        }
      });
    }
    new android.app.AlertDialog.Builder(this)
      .setTitle("Paleta de colores RGB")
      .setView(layout)
      .setNegativeButton("Cancelar",null)
      .setPositiveButton("Aplicar",(dialog,which)->{
        int c=Color.HSVToColor(hsv);
        red=Color.red(c);green=Color.green(c);blue=Color.blue(c);
        sendColor();
      }).show();
  }
  private TextView label(String value,int size,int color){
    TextView view=new TextView(this);
    view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(5,18,5,18);
    return view;
  }
  private Button button(String value,LinearLayout root){
    Button view=new Button(this);
    view.setText(value);view.setAllCaps(false);view.setTextColor(Color.WHITE);
    view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff9563be));
    root.addView(view);
    return view;
  }
  private void say(String value){runOnUiThread(()->status.setText(value));}
  private boolean bluetoothPermitted(){
    return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
      checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED);
  }
  private void requestBluetoothPermissions(){
    if(!bluetoothPermitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},100);
  }
  private void connect(){
    if(!bluetoothPermitted()){requestBluetoothPermissions();return;}
    if(adapter==null||!adapter.isEnabled()){say("Activa Bluetooth primero");return;}
    if(scanning)return;
    scanner=adapter.getBluetoothLeScanner();
    if(scanner==null){say("Bluetooth LE no disponible");return;}
    scanning=true;
    say("Buscando KATSEYE OLS...");
    scanner.startScan(scanCallback);
    handler.postDelayed(()->{
      if(scanning){scanning=false;scanner.stopScan(scanCallback);say("No encontrado. Intenta otra vez.");}
    },15000);
  }
  private final ScanCallback scanCallback=new ScanCallback(){
    @Override public void onScanResult(int callbackType,ScanResult result){
      BluetoothDevice device=result.getDevice();
      String name=device.getName();
      if(name!=null&&name.toUpperCase(Locale.ROOT).contains("KATSEYE")){
        scanning=false;
        scanner.stopScan(this);
        say("Conectando a "+name);
        if(gatt!=null){gatt.close();gatt=null;}
        gatt=device.connectGatt(MainActivity.this,false,gattCallback);
      }
    }
  };
  private final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){
    @Override public void onConnectionStateChange(BluetoothGatt current,int statusCode,int state){
      if(state==BluetoothProfile.STATE_CONNECTED){say("Conectado · descubriendo servicios");current.discoverServices();}
      else if(state==BluetoothProfile.STATE_DISCONNECTED){writeChar=null;say("Desconectado");}
    }
    @Override public void onServicesDiscovered(BluetoothGatt current,int statusCode){
      BluetoothGattService service=current.getService(serviceId);
      writeChar=service==null?null:service.getCharacteristic(writeId);
      if(writeChar==null){say("Conectado, pero no se encontró el canal de color");return;}
      say("Lightstick conectado · prueba los colores");
      BluetoothGattService batteryService=current.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"));
      if(batteryService!=null){
        BluetoothGattCharacteristic characteristic=batteryService.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"));
        if(characteristic!=null)current.readCharacteristic(characteristic);
      }
    }
    @Override public void onCharacteristicRead(BluetoothGatt current,BluetoothGattCharacteristic characteristic,int statusCode){
      if(statusCode==BluetoothGatt.GATT_SUCCESS&&characteristic.getUuid().toString().equals("00002a19-0000-1000-8000-00805f9b34fb")){
        byte[] bytes=characteristic.getValue();
        if(bytes!=null&&bytes.length>0)runOnUiThread(()->battery.setText("Batería: "+(bytes[0]&255)+"%"));
      }
    }
  };
  private void sendColor(){send(red*brightness/100,green*brightness/100,blue*brightness/100);}
  private void send(int r,int g,int b){
    runOnUiThread(()->{
      if(gatt==null||writeChar==null){say("Primero conecta el lightstick");return;}
      byte[] payload={(byte)1,(byte)255,0,(byte)r,(byte)g,(byte)b,0,0,0};
      payload[8]=(byte)((r+g+b)&255);
      writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
      writeChar.setValue(payload);
      if(!gatt.writeCharacteristic(writeChar))say("No se pudo enviar el color");
    });
  }
  private int presetColor(int mode,int beat){
    switch(mode){
      case 1: return 0xff00ff00;
      case 2: return 0xffff3ba7;
      case 3: return 0xffe51b32;
      case 4: return 0xffbd86f7;
      case 5: return beat%2==0?0xff923eeb:0xffff65c8;
      default: return palette[(beat/5)%6];
    }
  }
  private void sendPresetPreview(int mode){
    int c=presetColor(mode,0);
    send(Color.red(c)*brightness/100,Color.green(c)*brightness/100,Color.blue(c)*brightness/100);
  }
  private boolean startMusic(){
    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},101);
      say("Autoriza micrófono y vuelve a iniciar");
      return false;
    }
    if(writeChar==null){say("Primero conecta el lightstick");return false;}
    int minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
    if(minimum<=0){say("Micrófono no disponible");return false;}
    recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(minimum,4096));
    if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){
      recorder.release();recorder=null;say("No se pudo iniciar micrófono");return false;
    }
    try{recorder.startRecording();}catch(Exception ex){recorder.release();recorder=null;say("Error de micrófono");return false;}
    musicOn=true;
    final AudioRecord currentRecorder=recorder;
    musicThread=new Thread(()->{
      short[] samples=new short[2048];
      int beat=0;
      long last=0,lastBeat=0;
      double envelope=0.015;
      double previous=0;
      while(musicOn){
        int count=currentRecorder.read(samples,0,samples.length);
        if(count<=0)continue;
        double energy=0;
        for(int i=0;i<count;i++)energy+=samples[i]*(double)samples[i];
        double rms=Math.sqrt(energy/count)/32768.0;
        long now=System.currentTimeMillis();
        envelope=envelope*0.92+rms*0.08;
        if(now-last<110)continue;
        last=now;
        boolean hit=rms>Math.max(0.018,envelope*1.5)&&rms>previous*1.12&&now-lastBeat>170;
        if(hit){beat++;lastBeat=now;}
        previous=rms;
        int mode=songMode;
        int color=presetColor(mode,beat);
        double level=Math.min(1.0,Math.max(0.18,rms*13));
        if(hit)level=1.0;
        else if(mode!=0)level=Math.min(0.65,level);
        level*=brightness/100.0;
        send((int)(Color.red(color)*level),(int)(Color.green(color)*level),(int)(Color.blue(color)*level));
      }
    });
    musicThread.start();
    say("Modo concierto activo");
    return true;
  }
  private void stopMusic(){
    musicOn=false;
    AudioRecord current=recorder;
    recorder=null;
    if(current!=null){
      try{current.stop();}catch(Exception ignored){}
      if(musicThread!=null&&musicThread!=Thread.currentThread())try{musicThread.join(400);}catch(InterruptedException ignored){}
      current.release();
    }
  }
  @Override protected void onDestroy(){
    stopMusic();
    if(scanning&&scanner!=null)scanner.stopScan(scanCallback);
    if(gatt!=null){gatt.disconnect();gatt.close();}
    super.onDestroy();
  }
}
