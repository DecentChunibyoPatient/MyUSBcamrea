package com.wqg.myusbcamrea;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ImageView;

import com.wqg.com.usbhost.myusblibrary.Analysis;
import com.wqg.com.usbhost.myusblibrary.FileUtils;
import com.wqg.com.usbhost.myusblibrary.IsochronousStream;
import com.wqg.com.usbhost.myusblibrary.OnDeviceConnectListener;
import com.wqg.com.usbhost.myusblibrary.UsbHost;
import com.wqg.com.usbhost.myusblibrary.UsbUvc2;

public class MainActivity extends AppCompatActivity {
    UsbUvc2 usbUvc;
    ImageView imageView;
    UsbHost usbHost;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(imageView = new ImageView(getApplicationContext()));
        usbHost = UsbHost.getInstance(getApplicationContext());

        UsbHost.setOnDeviceConnectListener(new OnDeviceConnectListener() {
            @Override
            public void onConnect(UsbDevice usbDevice) {
                System.out.println("onConnect " + usbDevice.getDeviceName());
                start(new UsbHost.Device(usbHost.getManager(),usbDevice.getDeviceName()));
            }

            @Override
            public void onDisconnect(UsbDevice usbDevice) {
                System.out.println("onDisconnect " + usbDevice.getDeviceName());
            }

            @Override
            public void openDevice(UsbDevice usbDevice, UsbDeviceConnection usbDeviceConnection) {
                System.out.println("openDevice");
            }

            @Override
            public void onPermission(int code, UsbDevice usbDevice) {
                System.out.println("onPermission");
                switch (code) {
                    case UsbHost.PERMISSION_ALLOW: {
                        System.out.println("PERMISSION_ALLOW");
                    }
                    break;
                    case UsbHost.PERMISSION_REFUSAL: {
                        System.out.println("PERMISSION_REFUSAL");
                        UsbHost.requestPermission(usbDevice);
                    }
                    break;
                }
            }

            @Override
            public void onErr(int code, UsbDevice usbDevice, String m) {
                switch (code) {
                    case UsbHost.ERR_NOT_INTERFACE: {
                        System.out.println("ERR_NOT_INTERFACE");
                    }
                    break;
                    case UsbHost.ERR_NOT_PERMISSION: {
                        System.out.println("ERR_NOT_PERMISSION");
                        UsbHost.requestPermission(usbDevice);
                    }
                    break;
                }

            }

        });
        if (usbHost.getDeviceCount() > 0) {
            System.out.println("getInterfaceCount:"+usbHost.getDevice(0).getUsbDevice().getInterfaceCount());
            System.out.println(usbHost.getDevice(0).getUsbDevice());
            start(usbHost.getDevice(0));
        }

    }

    public void start(UsbHost.Device device) {
        device.openDevice();
        usbUvc = new UsbUvc2(device);
        try {
            usbUvc.init(new Analysis(new String(FileUtils.readStringFromAssets(getApplicationContext(), FileUtils.getfilesFromAssets(getApplicationContext(), "")[0]))),
                    84,
                    1024 * 1,
                    8);
        } catch (Exception e) {
            e.printStackTrace();
        }
        usbUvc.isoStream();
        usbUvc.getRunningStream().setOnListener(new IsochronousStream.OnListener() {
            @Override
            public void onSurfaceTextureUpdated(byte[] var1) {
                System.out.println(var1.length);
                System.out.println("var1:"+UsbUvc2.printHexString(var1));
              //  final Bitmap bitmap = BitmapFactory.decodeByteArray(var1, 0, var1.length);
              /*  final Bitmap bitmap =UsbUvc2.processReceivedVideoFrameYuv(var1,640,480);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        imageView.setImageBitmap(bitmap);
                    }
                });*/
            }
        });
    }
}