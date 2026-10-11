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
  private AudioRecord recorder;
  private Thread musicThread;
  private int red=255,green=40,blue=150,brightness=100;

  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(0xff120b20);
    getWindow().setNavigationBarColor(0xff120b20);
    BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
    adapter=manager.getAdapter();
    LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(28,28,28,28);
    root.setBackgroundColor(0xff120b20);
    ScrollView scroll=new ScrollView(this);
    scroll.addView(root);
    setContentView(scroll);
    TextView title=label("KATSEYE ✦",30,0xffffc3e4);
    title.setGravity(Gravity.CENTER);
    root.addView(title);
    TextView subtitle=label("COCONUT LIGHTSTICK · beta 0.1",12,0xffbda9ca);
    subtitle.setGravity(Gravity.CENTER);
    root.addView(subtitle);
    status=label("Sin conectar",17,Color.WHITE);
    root.addView(status);
    battery=label("Batería: pendiente de lectura",14,0xffd2b9e2);
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
    root.addView(label("MODO CONCIERTO 🎵",20,0xffffc3e4));
    root.addView(label("Reacciona al volumen captado por el micrófono. No sincroniza canciones por título ni captura audio interno.",14,0xffd2b9e2));
    Button music=button("INICIAR MODO MÚSICA",root);
    music.setOnClickListener(v->{
      if(musicOn){stopMusic();music.setText("INICIAR MODO MÚSICA");}
      else if(startMusic()){music.setText("DETENER MODO MÚSICA");}
    });
    root.addView(label("Prototipo experimental. Protocolo Bluetooth sin verificar con el dispositivo real. No afiliado a KATSEYE.",11,0xffa593b2));
    requestBluetoothPermissions();
  }
  private TextView label(String value,int size,int color){
    TextView view=new TextView(this);
    view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setPadding(5,18,5,18);
    return view;
  }
  private Button button(String value,LinearLayout root){
    Button view=new Button(this);
    view.setText(value);view.setAllCaps(false);view.setTextColor(Color.WHITE);
    view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff8e3b9f));
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
      short[] samples=new short[2048];int index=0;long last=0;
      while(musicOn){
        int count=currentRecorder.read(samples,0,samples.length);
        if(count<=0)continue;
        double energy=0;
        for(int i=0;i<count;i++)energy+=samples[i]*(double)samples[i];
        double rms=Math.sqrt(energy/count)/32768.0;
        long now=System.currentTimeMillis();
        if(now-last<180)continue;
        last=now;
        int color=palette[(index++/5)%6];
        double amp=Math.min(1,Math.max(.08,rms*12));
        send((int)(Color.red(color)*amp),(int)(Color.green(color)*amp),(int)(Color.blue(color)*amp));
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
