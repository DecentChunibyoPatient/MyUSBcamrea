package com.wqg.com.usbhost.myusblibrary;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class UsbUvc2 {

    UsbDeviceConnection usbDeviceConnection;
    UsbDevice usbDevice;
    private UsbInterface camControlInterface;
    private UsbInterface camStreamingInterface;
    private UsbEndpoint camStreamingEndpoint;
    private boolean bulkMode;
    private  int camStreamingAltSetting;
    private  int camFormatIndex;
    private  int camFrameIndex;
    private  int camFrameInterval;
    private  int packetsPerRequest;
    private  int maxPacketSize;
    private  int activeUrbs;
    IsochronousStream runningStream;
    UsbHost.Device device;
    int brightnessMin;
    int brightnessMax;
    public UsbUvc2(UsbHost.Device device){
        this.device=device;
        this.usbDeviceConnection=device.getUsbDeviceConnection();
        this.usbDevice=device.getUsbDevice();

    }
    public void init(
           int camFormatIndex,
           int camFrameIndex,
           int camFrameInterval,
           int packetsPerRequest,
           int maxPacketSize,
           int activeUrbs) throws Exception {
        this.camFormatIndex=camFormatIndex;
        this.camFrameIndex=camFrameIndex;
        this.camFrameInterval=camFrameInterval;
        this.packetsPerRequest=packetsPerRequest;
        this.maxPacketSize=maxPacketSize;
        this.activeUrbs=activeUrbs;
        openCameraDevice();
        initStreamingParms();
        initBrightnessParms();
    }
    public void init(Analysis analysis,
            int packetsPerRequest,
            int maxPacketSize,
            int activeUrbs) throws Exception {
        this.packetsPerRequest=packetsPerRequest;
        this.maxPacketSize=maxPacketSize;
        this.activeUrbs=activeUrbs;
        openCameraDevice();
        initControls(device,analysis);
    }

    public IsochronousStream getRunningStream() {
        return runningStream;
    }

    public void isoStream() {
        if (usbDevice == null)return;
        try {
            runningStream = new IsochronousStream(usbDeviceConnection,camStreamingInterface,packetsPerRequest,activeUrbs,maxPacketSize);
            runningStream.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void initControls(UsbHost.Device device,Analysis analysis){
        for (Analysis.Controls.Control control:analysis.controls){
           UsbHost.Device.IofnData iofnData =device.controlTransfer3(control.getBytes(),5000);
            System.out.println("len:"+iofnData.len+" bytes:"+printHexString(iofnData.getData()));
        }
    }
    private static void packUsbInt(int i, byte[] buf, int pos) {
        UVCTools.packInt(i, buf, pos, false);
    }
    public static   String printHexString( byte[] b) {
        String a = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            if (i%4!=0){
                a = a+" "+hex;
            }else {
                a = a+"  "+hex;
            }
        }

        return a;
    }
    private void initStreamingParms() throws Exception {
        final int timeout = 5000;
        int usedStreamingParmsLen;
        int len;
        byte[] streamingParms = new byte[26];
        // The e-com module produces errors with 48 bytes (UVC 1.5) instead of 26 bytes (UVC 1.1) streaming parameters! We could use the USB version info to determine the size of the streaming parameters.
        streamingParms[0] = (byte) 0x00;                // (0x01: dwFrameInterval) //D0: dwFrameInterval //D1: wKeyFrameRate // D2: wPFrameRate // D3: wCompQuality // D4: wCompWindowSize
        streamingParms[2] = (byte) camFormatIndex;                // bFormatIndex
        streamingParms[3] = (byte) camFrameIndex;                 // bFrameIndex
        packUsbInt(camFrameInterval, streamingParms, 4);         // dwFrameInterval
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_SET, UsbHost.SET_CUR, UsbHost.VS_PROBE_CONTROL << 8, camStreamingInterface.getId(), streamingParms, streamingParms.length, timeout);
        if (len != streamingParms.length) {
            throw new Exception("Camera initialization failed. Streaming parms probe set failed, len=" + len + ".");
        }
        // for (int i = 0; i < streamingParms.length; i++) streamingParms[i] = 99;          // temp test
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.VS_PROBE_CONTROL << 8, camStreamingInterface.getId(), streamingParms, streamingParms.length, timeout);
        if (len != streamingParms.length) {
            throw new Exception("Camera initialization failed. Streaming parms probe get failed.");
        }
        usedStreamingParmsLen = len;
        // log("Streaming parms length: " + usedStreamingParmsLen);
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_SET, UsbHost.SET_CUR, UsbHost.VS_COMMIT_CONTROL << 8, camStreamingInterface.getId(), streamingParms, usedStreamingParmsLen, timeout);
        if (len != streamingParms.length) {
            throw new Exception("Camera initialization failed. Streaming parms commit set failed.");
        }
        // for (int i = 0; i < streamingParms.length; i++) streamingParms[i] = 99;          // temp test
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.VS_COMMIT_CONTROL << 8, camStreamingInterface.getId(), streamingParms, usedStreamingParmsLen, timeout);
        if (len != streamingParms.length) {
            throw new Exception("Camera initialization failed. Streaming parms commit get failed.");
        }
    }

    private void initBrightnessParms() throws Exception {
        final int timeout = 5000;
        int len;
        byte[] brightnessParms = new byte[2];
        // PU_BRIGHTNESS_CONTROL(0x02), GET_MIN(0x82) [UVC1.5, p. 160, 158, 96]
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_MIN, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);
        if (len != brightnessParms.length) {
            throw new Exception("Camera PU_BRIGHTNESS_CONTROL GET_MIN failed. len= " + len + ".");
        }
        brightnessMin = unpackIntBrightness(brightnessParms);
        // PU_BRIGHTNESS_CONTROL(0x02), GET_MAX(0x83) [UVC1.5, p. 160, 158, 96]
        usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_MAX, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);
        brightnessMax = unpackIntBrightness(brightnessParms);
        // PU_BRIGHTNESS_CONTROL(0x02), GET_RES(0x84) [UVC1.5, p. 160, 158, 96]
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_RES, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);
        // PU_BRIGHTNESS_CONTROL(0x02), GET_CUR(0x81) [UVC1.5, p. 160, 158, 96]
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);

    }
    private static int unpackIntBrightness(byte[] buf) {
        return (((buf[1] ) << 8) | (buf[0] & 0xFF));

    }
    private int getVideoStreamErrorCode() throws Exception {
        byte buf[] = new byte[1];
        buf[0] = 99;
        int len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.VS_STREAM_ERROR_CODE_CONTROL << 8, camStreamingInterface.getId(), buf, 1, 1000);
        if (len == 0) {
            return 0;
        }                   // ? (Logitech C310 returns len=0)
        if (len != 1) {
            throw new Exception("VS_STREAM_ERROR_CODE_CONTROL failed, len=" + len + ".");
        }
        return buf[0];
    }

    private int getVideoControlErrorCode() throws Exception {
        byte buf[] = new byte[1];
        buf[0] = 99;
        int len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.VC_REQUEST_ERROR_CODE_CONTROL << 8, 0, buf, 1, 1000);
        if (len != 1) {
            throw new Exception("VC_REQUEST_ERROR_CODE_CONTROL failed, len=" + len + ".");
        }
        return buf[0];
    }
    private void openCameraDevice() throws Exception {
        // (For transfer buffer sizes > 196608 the kernel file drivers/usb/core/devio.c must be patched.)
        camControlInterface = UVCTools.getVideoControlInterface(usbDevice);
        camStreamingInterface = UVCTools.getVideoStreamingInterface(usbDevice);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            camStreamingAltSetting=camStreamingInterface.getAlternateSetting();
        }
        if (camStreamingInterface.getEndpointCount() < 1) {
            throw new Exception("Streaming interface has no endpoint.");
        }
        camStreamingEndpoint = camStreamingInterface.getEndpoint(0);
        bulkMode = camStreamingEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK;
        if (usbDeviceConnection == null) {
            throw new Exception("Unable to open camera device connection.");
        }
        if (!usbDeviceConnection.claimInterface(camControlInterface, true)) {
            throw new Exception("Unable to claim camera control interface.");
        }
        if (!usbDeviceConnection.claimInterface(camStreamingInterface, true)) {
            throw new Exception("Unable to claim camera streaming interface.");
        }
    }
    public static Bitmap processReceivedMJpegVideoFrameKamera(byte[] mjpegFrameData) throws Exception {

        byte[] jpegFrameData = convertMjpegFrameToJpegKamera(mjpegFrameData);
        System.out.println(mjpegFrameData.length+" "+jpegFrameData.length);
       return BitmapFactory.decodeByteArray(mjpegFrameData, 0, mjpegFrameData.length);
    }
    // see USB video class standard, USB_Video_Payload_MJPEG_1.5.pdf
    public static byte[] convertMjpegFrameToJpegKamera(byte[] frameData) throws Exception {
        int frameLen = frameData.length;
        while (frameLen > 0 && frameData[frameLen - 1] == 0) {
            frameLen--;
        }
        boolean hasHuffmanTable = UVCTools.findJpegSegment(frameData, frameLen, 0xC4) != -1;
        if (hasHuffmanTable) {
            if (frameData.length == frameLen) {
                return frameData;
            }
            return Arrays.copyOf(frameData, frameLen);
        } else {
            int segmentDaPos = UVCTools.findJpegSegment(frameData, frameLen, 0xDA);

            if (segmentDaPos == -1) {
              return null;
            }
                byte[] a = new byte[frameLen + UVCTools.mjpgHuffmanTable.length];
                System.arraycopy(frameData, 0, a, 0, segmentDaPos);
                System.arraycopy(UVCTools.mjpgHuffmanTable, 0, a, segmentDaPos, UVCTools.mjpgHuffmanTable.length);
                System.arraycopy(frameData, segmentDaPos, a, segmentDaPos + UVCTools.mjpgHuffmanTable.length, frameLen - segmentDaPos);
                return a;
        }
    }
    public static Bitmap processReceivedVideoFrameYuv(byte[] frameData,int imageWidth,int imageHeight){
        YuvImage yuvImage = new YuvImage(frameData, ImageFormat.YUY2, imageWidth, imageHeight, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageWidth, imageHeight), 100, os);
        byte[] jpegByteArray = os.toByteArray();
        return BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
    }
}
