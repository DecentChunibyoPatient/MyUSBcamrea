package com.wqg.com.usbhost.myusblibrary;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Build;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;

public class UsbHost extends HashMap<String, UsbHost.Device>{
    // USB codes:
// Request types (bmRequestType):
    public static final int RT_STANDARD_INTERFACE_SET = 0x01;
    public static final int RT_CLASS_INTERFACE_SET = 0x21;
    public static final int RT_CLASS_INTERFACE_GET = 0xA1;
    // Video interface subclass codes:
    public static final int SC_VIDEOCONTROL = 0x01;
    public static final int SC_VIDEOSTREAMING = 0x02;
    // Standard request codes:
    public static final int SET_INTERFACE = 0x0b;
    // Video class-specific request codes:
    public static final int SET_CUR = 0x01;
    public static final int GET_CUR = 0x81;
    public static final int GET_MIN = 0x82;
    public static final int GET_MAX = 0x83;
    public static final int GET_RES = 0x84;
    // VideoControl interface control selectors (CS):
    public static final int VC_REQUEST_ERROR_CODE_CONTROL = 0x02;
    // VideoStreaming interface control selectors (CS):
    public static final int VS_PROBE_CONTROL = 0x01;
    public static final int VS_COMMIT_CONTROL = 0x02;
    public static final int PU_BRIGHTNESS_CONTROL = 0x02;
    public static final int VS_STILL_PROBE_CONTROL = 0x03;
    public static final int VS_STILL_COMMIT_CONTROL = 0x04;
    public static final int VS_STREAM_ERROR_CODE_CONTROL = 0x06;
    public static final int VS_STILL_IMAGE_TRIGGER_CONTROL = 0x05;

    ArrayList<String>key;
    UsbManager manager;
    static public UsbHost usbHost;
    public static Context context;
    static public Context getContext(){
        return context;
    }
    public static UsbHost getInstance(Context context) {
        if (usbHost == null) {
            usbHost = new UsbHost(context);
        }
        return usbHost;
    }
    public static UsbHost getInstance() {
        return usbHost;
    }
    public UsbHost(Context context) {
        this.context=context;
        key=new ArrayList<>();
        initReceiver(context);
        // 获取USB设备
         manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        //获取到设备列表
        HashMap<String, UsbDevice>deviceList = manager.getDeviceList();
        for (String key:deviceList.keySet()){
            put(manager,key);
        }
        usbHost=this;

    }
    public void remove(UsbManager usbManager,String key){
        this.key.remove(key);
        remove(key);
    }
    public void put(UsbManager usbManager,String key){
        this.key.add(key);
        Device device=new Device(usbManager,key);
        put(key,device);
    }
    final static public int ERR_NOT_INTERFACE=-1;
    final static public int ERR_NOT_PERMISSION=-2;
    final static public int PERMISSION_REFUSAL=0;
    final static public int PERMISSION_ALLOW=-1;
   static public OnDeviceConnectListener onDeviceConnectListener;

    public static void setOnDeviceConnectListener(OnDeviceConnectListener onDeviceConnectListener) {
        UsbHost.onDeviceConnectListener = onDeviceConnectListener;
    }

    void initReceiver(Context context){
        final IntentFilter filter = new IntentFilter(ACTION_DEVICE_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        context.registerReceiver(mUsbReceiver, filter);
    }
    public void unregisterReceiver(Context context){
        context.unregisterReceiver(mUsbReceiver);
    }
    public void Destruction(){
        onDeviceConnectListener=null;
        unregisterReceiver(context);
    }
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            switch (action){
                case UsbManager.ACTION_USB_DEVICE_ATTACHED:{
                    if (!containsValue(usbDevice.getDeviceName())){
                        put(manager,usbDevice.getDeviceName());
                    }else {
                        remove(manager,usbDevice.getDeviceName());
                        put(manager,usbDevice.getDeviceName());
                    }
                    if (onDeviceConnectListener!=null)
                        onDeviceConnectListener.onConnect(usbDevice);
                }break;
                case UsbManager.ACTION_USB_DEVICE_DETACHED:{
                    if (containsValue(usbDevice.getDeviceName())){
                        remove(manager,usbDevice.getDeviceName());
                    }
                    if (onDeviceConnectListener!=null)
                        onDeviceConnectListener.onDisconnect(usbDevice);

                }break;
                case ACTION_DEVICE_PERMISSION:{
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            //权限通过，继续操作
                            onDeviceConnectListener.onPermission(PERMISSION_ALLOW,usbDevice);
                        }
                    } else {
                        //用户不允许USB访问设备
                        onDeviceConnectListener.onPermission(PERMISSION_REFUSAL,usbDevice);
                    }
                }break;
            }
        }
    };

    public UsbManager getManager() {
        return manager;
    }
    public int getDeviceCount(){
        return key.size();
    }
    public Device getDevice(int index){
        return get(key.get(index));
    }
    static public class Device extends Thread {
        UsbManager manager;
        String key;
        UsbDeviceConnection usbDeviceConnection;
        public Device(UsbManager manager, String key) {
            this.manager = manager;
            this.key = key;
        }

        public int controlTransfer(byte[]connect,int timeout){
            int l=(connect[6]&0xff)+((connect[7]&0xff)<<8);
            byte[]data=new byte[l];
            if (connect.length!=data.length+8){
                byte[]bytes=new byte[data.length+8];
                System.arraycopy(connect,0,bytes,0,connect.length);
                connect=bytes;
            }
            if (connect.length>8){
                System.arraycopy(connect,8,data,0,connect.length-8);
            }
            System.out.println( UsbUvc2.printHexString(connect));
            int len = usbDeviceConnection.controlTransfer(connect[0], connect[1],  connect[2]+ (connect[3]<<8), connect[4]+(connect[5]<<8), data, data.length, timeout);

            if (connect.length>8)
                System.arraycopy(data,0,connect,8,data.length);
            System.out.println( UsbUvc2.printHexString(connect));
            return len;
        }
        public byte[] controlTransfer2(int[] len,byte[]connect,int timeout){
            int l=(connect[6]&0xff)+((connect[7]&0xff)<<8);
            byte[]data=new byte[l];
            if (connect.length!=data.length+8){
                byte[]bytes=new byte[data.length+8];
                System.arraycopy(connect,0,bytes,0,connect.length);
                connect=bytes;
            }
            if (connect.length>8){
                System.arraycopy(connect,8,data,0,connect.length-8);
            }
            len[0] = usbDeviceConnection.controlTransfer(connect[0], connect[1],  connect[2]+ (connect[3]<<8), connect[4]+(connect[5]<<8), data, data.length, timeout);
            System.out.println("data:"+new String(data));
            if (connect.length>8)
                System.arraycopy(data,0,connect,8,data.length);
            return connect;
        }
        public IofnData controlTransfer3(byte[]connect,int timeout){
            IofnData iofnData=new IofnData();
            byte[]data=new byte[(connect[6]&0xff)+((connect[7]&0xff)<<8)];
            System.arraycopy(connect,8,data,0,connect.length-8);
            int len = usbDeviceConnection.controlTransfer(connect[0], connect[1],  connect[2]+ (connect[3]<<8), connect[4]+(connect[5]<<8), data, data.length, timeout);
            iofnData.data=data;
            iofnData.head=connect;
            iofnData.len=len;
            return iofnData;
        }
        class IofnData{
            byte[]head;
            byte[]data;
            int len;

            public byte[] getData() {
                byte[]bytes=new byte[data.length+8];
                System.arraycopy(head,0,bytes,0,8);
                System.arraycopy(data,0,bytes,8,data.length);
                return bytes;
            }
        }
        public UsbDeviceConnection getUsbDeviceConnection() {
            return usbDeviceConnection;
        }
        public UsbDevice getUsbDevice() {
            return manager.getDeviceList().get(key);
        }
        public UsbInterface getInterface(int index) {
            return getUsbDevice().getInterface(index);
        }
        @Override
        public void run() {
            openDevice();
        }
        public boolean hasPermission() {
            return manager.hasPermission(getUsbDevice());
        }

        public boolean openDevice() {
            if (hasPermission()) {
                // 打开设备，获取 UsbDeviceConnection 对象，连接设备，用于后面的通讯
                usbDeviceConnection = manager.openDevice(getUsbDevice());
                if (getUsbDevice().getInterfaceCount() > 0) {
                    if (onDeviceConnectListener!=null)
                        onDeviceConnectListener.openDevice(getUsbDevice(),usbDeviceConnection);
                } else {
                    if (onDeviceConnectListener!=null)
                        onDeviceConnectListener.onErr(ERR_NOT_INTERFACE,getUsbDevice(),"没有设备接口");
                }
                return true;
            } else {
                if (onDeviceConnectListener!=null)
                    onDeviceConnectListener.onErr(ERR_NOT_PERMISSION,getUsbDevice(),"没有权限");
            }
            return false;
        }
    }
    public static final String ACTION_DEVICE_PERMISSION =
            "ACTION_DEVICE_PERMISSION";
    static public void requestPermission(UsbDevice usbDevice){
        //申请权限
        Intent intent = new Intent(ACTION_DEVICE_PERMISSION);
        PendingIntent mPermissionIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
        usbHost.getManager().requestPermission(usbDevice, mPermissionIntent);
    }

    static public String getType(int i){
        switch (i){
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:return "CONTROL";
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:return "ISOC";
            case UsbConstants.USB_ENDPOINT_XFER_BULK:return "BULK";
            case UsbConstants.USB_ENDPOINT_XFER_INT:return "INT";
            default:return "default";
        }
    }
    static public String getDirection(int i){
        switch (i){
            case UsbConstants.USB_DIR_OUT:return "DIR_OUT";
            case UsbConstants.USB_DIR_IN:return "DIR_IN";
            default:return "default";
        }
    }
    static public String getInterfaceClass(int i){
        switch (i){
            case UsbConstants.USB_CLASS_PER_INTERFACE:return "USB_CLASS_PER_INTERFACE";
            case UsbConstants.USB_CLASS_AUDIO:return "USB_CLASS_AUDIO";
            case UsbConstants.USB_CLASS_COMM:return "USB_CLASS_COMM";
            case UsbConstants.USB_CLASS_HID:return "USB_CLASS_HID";
            case UsbConstants.USB_CLASS_PHYSICA:return "USB_CLASS_PHYSICA";
            case UsbConstants.USB_CLASS_STILL_IMAGE:return "USB_CLASS_STILL_IMAGE";
            case UsbConstants.USB_CLASS_PRINTER:return "USB_CLASS_PRINTER";
            case UsbConstants.USB_CLASS_HUB:return "USB_CLASS_HUB";
            case UsbConstants.USB_CLASS_CDC_DATA:return "USB_CLASS_CDC_DATA";
            case UsbConstants.USB_CLASS_CSCID:return "USB_CLASS_CSCID";
            case UsbConstants.USB_CLASS_CONTENT_SEC:return "USB_CLASS_CONTENT_SEC";
            case UsbConstants.USB_CLASS_VIDEO:return "USB_CLASS_VIDEO";
            case UsbConstants.USB_CLASS_WIRELESS_CONTROLLER:return "USB_CLASS_WIRELESS_CONTROLLER";
            case UsbConstants.USB_CLASS_APP_SPEC:return "USB_CLASS_APP_SPEC";
            case UsbConstants.USB_CLASS_VENDOR_SPEC:return "USB_CLASS_VENDOR_SPEC";
            default:return "default";
        }
    }


}
