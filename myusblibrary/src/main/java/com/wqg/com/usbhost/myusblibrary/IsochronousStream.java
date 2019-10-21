package com.wqg.com.usbhost.myusblibrary;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Build;

import com.sun.jna.LastErrorException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public  class IsochronousStream extends Thread {

    UsbIso usbIso;
    UsbEndpoint camStreamingEndpoint;
    byte[] data;
    public IsochronousStream(UsbDeviceConnection usbDeviceConnection, UsbInterface camStreamingInterface, int packetsPerRequest, int activeUrbs, int maxPacketSize) throws IOException {
        setPriority(Thread.MAX_PRIORITY);
        camStreamingEndpoint=camStreamingInterface.getEndpoint(0);
        data = new byte[maxPacketSize];
        usbIso = new UsbIso(usbDeviceConnection.getFileDescriptor(), packetsPerRequest, maxPacketSize);
        usbIso.preallocateRequests(activeUrbs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            usbIso.setInterface(camStreamingInterface.getId(), camStreamingInterface.getAlternateSetting());
        }
        for (int i = 0; i < activeUrbs; i++) {
            UsbIso.Request req = usbIso.getRequest();
            req.initialize(camStreamingEndpoint.getAddress());
            req.submit();
        }
    }

    public void run() {
        run1();
    }

    public void run1(){
        try {
            ByteArrayOutputStream frameData = new ByteArrayOutputStream(0x20000);
            boolean error=false;
            while (true) {
                UsbIso.Request req = usbIso.reapRequest(true);
                for (int packetNo = 0; packetNo < req.getPacketCount(); packetNo++) {
                    if (req.getPacketStatus2(packetNo)!= 0){
                        continue;
                    }
                    int packetLen = req.getPacketActualLength2(packetNo);
                    if (packetLen == 0) {
                        continue;
                    }
                    req.getPacketData2(packetNo, data, packetLen);
                    int headerLen = data[0] & 0xff;
                    int headerFlags = data[1] & 0xff;
                    int dataLen = packetLen - headerLen;
                    if (!error)
                        error = (headerFlags & 0x40) != 0;
                    if (dataLen > 0 ) {
                        frameData.write(data, headerLen, dataLen);
                    }
                    if ((headerFlags & 2) != 0) {
                        if (!error){
                            onListener.onSurfaceTextureUpdated(frameData.toByteArray());
                        }
                        frameData.reset();
                        error=false;
                    }
                }
                req.initialize2(camStreamingEndpoint.getAddress());
                req.submit2();
            }
        } catch (LastErrorException lastErrorException){
            if (lastErrorException.getErrorCode()==19){
                System.out.println("No such device");
            }else {
                lastErrorException.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            usbIso.dispose();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void setOnListener(OnListener onListener) {
        this.onListener = onListener;
    }

    public interface OnListener{
        void onSurfaceTextureUpdated(byte[] var1);
    }
    OnListener onListener=new OnListener() {
        @Override
        public void onSurfaceTextureUpdated(byte[] var1) {
        }
    };
}

