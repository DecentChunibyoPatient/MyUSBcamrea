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

public class UsbUvc {

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
    private  int imageWidth;
    private  int imageHeight;
    private  int activeUrbs;
    private  String videoformat;
    UsbIso usbIso;
    private int currentBrightness;
    IsochronousStream runningStream;
    private boolean stopKamera = false;
    boolean brightnessChanged;
    private boolean changeBrightness;
    int brightnessMin;
    int brightnessMax;
    UsbHost.Device device;
    public UsbUvc(UsbHost.Device device){
        this.usbDeviceConnection=device.getUsbDeviceConnection();
        this.usbDevice=device.getUsbDevice();
        this.device=device;
    }
    public void init(
           int camStreamingAltSetting,
           int camFormatIndex,
           int camFrameIndex,
           int camFrameInterval,
           int packetsPerRequest,
           int maxPacketSize,
           int imageWidth,
           int imageHeight,
           int activeUrbs,
           String videoformat){

        this.camStreamingAltSetting=camStreamingAltSetting;
        this.camFormatIndex=camFormatIndex;
        this.camFrameIndex=camFrameIndex;
        this.camFrameInterval=camFrameInterval;
        this.packetsPerRequest=packetsPerRequest;
        this.maxPacketSize=maxPacketSize;
        this.imageWidth=imageWidth;
        this.imageHeight=imageHeight;
        this.activeUrbs=activeUrbs;
        this.videoformat=videoformat;

    }

    public UsbDevice getUsbDevice() {
        return usbDevice;
    }

    public void setOnListener(OnListener onListener) {
        this.onListener = onListener;
    }

    OnListener onListener=new OnListener() {
        @Override
        public void onSurfaceTextureUpdated(Bitmap var1) {


        }
    };
    public interface OnListener{
        void onSurfaceTextureUpdated(Bitmap var1);
    }

    public void isoStream() {
        if (usbDevice == null)return;
        try {
            openCam();
            runningStream = new IsochronousStream(usbDeviceConnection,camStreamingInterface,packetsPerRequest,activeUrbs,maxPacketSize);
            runningStream.setOnListener(new IsochronousStream.OnListener() {
                @Override
                public void onSurfaceTextureUpdated(byte[] var1) {
                    if (videoformat.equals("mjpeg") ) {
                        try {
                            processReceivedMJpegVideoFrameKamera(var1);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }else if (videoformat.equals("yuv")){
                        processReceivedVideoFrameYuv(var1);
                    }
                }
            });
            runningStream.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void isoStream(Analysis analysis) {
        if (usbDevice == null)return;
        try {
            openCameraDevice();
            initControls(device,analysis);
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
    private void submitActiveUrbs() throws IOException {
        for (int i = 0; i < activeUrbs; i++) {
            UsbIso.Request req = usbIso.getRequest();
            req.initialize(camStreamingEndpoint.getAddress());
            req.submit();
        }
    }
    private int stillImageFrame = 0;
    private int stillImageFrameBeenden = 0;
   /* class IsochronousStream extends Thread {

     
        public IsochronousStream() {
            setPriority(Thread.MAX_PRIORITY);
        }

        public void run() {
            try {
                ByteArrayOutputStream frameData = new ByteArrayOutputStream(0x20000);
                int skipFrames = 0;
                byte[] data = new byte[maxPacketSize];
                enableStreaming(true);
                submitActiveUrbs();
                while (true) {
                    UsbIso.Request req = usbIso.reapRequest(true);
                    for (int packetNo = 0; packetNo < req.getPacketCount(); packetNo++) {
                        int packetStatus = req.getPacketStatus(packetNo);
                        try {if (packetStatus != 0) {
                            skipFrames = 1;}
                            //    throw new IOException("Camera read error, packet status=" + packetStatus);
                        } catch (Exception e){
                        }
                        int packetLen = req.getPacketActualLength(packetNo);
                        if (packetLen == 0) {
                            continue;
                        }
                        if (packetLen > maxPacketSize) {
                            throw new Exception("packetLen > maxPacketSize");
                        }
                        req.getPacketData(packetNo, data, packetLen);
                        int headerLen = data[0] & 0xff;

                        try { if (headerLen < 2 || headerLen > packetLen) {
                            skipFrames = 1;
                        }
                        } catch (Exception e) {
                        }
                        int headerFlags = data[1] & 0xff;
                        int dataLen = packetLen - headerLen;
                        boolean error = (headerFlags & 0x40) != 0;
                        if (error && skipFrames == 0) {
                            skipFrames = 1;
                        }
                        if (dataLen > 0 && skipFrames == 0) {
                            frameData.write(data, headerLen, dataLen);
                        }
                        if ((headerFlags & 2) != 0) {
                            if (skipFrames > 0) {
                                frameData.reset();
                                skipFrames--;
                            }
                            else {
                                if (stillImageFrame > stillImageFrameBeenden ) {
                                    try {
                                        sendStillImageTrigger();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                stillImageFrameBeenden = stillImageFrame;
                                frameData.write(data, headerLen, dataLen);
                                if (videoformat.equals("mjpeg") ) {
                                    try {
                                        processReceivedMJpegVideoFrameKamera(frameData.toByteArray());
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }else if (videoformat.equals("yuv")){
                                    processReceivedVideoFrameYuv(frameData.toByteArray());
                                }
                                frameData.reset();
                            }
                        }
                    }
                    req.initialize(camStreamingEndpoint.getAddress());
                    req.submit();
                    if (stopKamera == true) {
                        break;
                    }
                    if (changeBrightness) changebright();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    */

    public void changebright () {
        final int timeout = 500;
        int len;
        byte[] brightnessParms = new byte[2];
        UVCTools.packIntBrightness(currentBrightness, brightnessParms);

        // PU_BRIGHTNESS_CONTROL(0x02), SET_CUR(0x01) [UVC1.5, p. 160, 158, 96]
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_SET, UsbHost.SET_CUR, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);
        if (len != brightnessParms.length) {

        }
        // PU_BRIGHTNESS_CONTROL(0x02), GET_CUR(0x81) [UVC1.5, p. 160, 158, 96]
        len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_GET, UsbHost.GET_CUR, UsbHost.PU_BRIGHTNESS_CONTROL << 8, 0x0200, brightnessParms, brightnessParms.length, timeout);
        if (len != brightnessParms.length) {
        } else {
            currentBrightness = unpackIntBrightness(brightnessParms);
        }
        changeBrightness = false;
        if (currentBrightness != 0) brightnessChanged = true;
    }
    private void processReceivedVideoFrameYuv(byte[] frameData){
        YuvImage yuvImage = new YuvImage(frameData, ImageFormat.YUY2, imageWidth, imageHeight, null);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, imageWidth, imageHeight), 100, os);
        byte[] jpegByteArray = os.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.length);
        onListener.onSurfaceTextureUpdated(bitmap);
    }


    boolean exit=false;
    // see USB video class standard, USB_Video_Payload_MJPEG_1.5.pdf
    private byte[] convertMjpegFrameToJpegKamera(byte[] frameData) throws Exception {
        int frameLen = frameData.length;
        while (frameLen > 0 && frameData[frameLen - 1] == 0) {
            frameLen--;
        }
        //  if (frameLen < 100 || (frameData[0] & 0xff) != 0xff || (frameData[1] & 0xff) != 0xD8 || (frameData[frameLen - 2] & 0xff) != 0xff || (frameData[frameLen - 1] & 0xff) != 0xd9) {
        //        throw new Exception("Invalid MJPEG frame structure, length=" + frameData.length);
        //  }
        boolean hasHuffmanTable = UVCTools.findJpegSegment(frameData, frameLen, 0xC4) != -1;
        exit = false;
        if (hasHuffmanTable) {
            if (frameData.length == frameLen) {
                return frameData;
            }
            return Arrays.copyOf(frameData, frameLen);
        } else {
            int segmentDaPos = UVCTools.findJpegSegment(frameData, frameLen, 0xDA);

            try {if (segmentDaPos == -1) {
                exit = true;
            }
            } catch (Exception e) {

            }
            //          throw new Exception("Segment 0xDA not found in MJPEG frame data.");
            if (exit ==false) {
                byte[] a = new byte[frameLen + UVCTools.mjpgHuffmanTable.length];
                System.arraycopy(frameData, 0, a, 0, segmentDaPos);
                System.arraycopy(UVCTools.mjpgHuffmanTable, 0, a, segmentDaPos, UVCTools.mjpgHuffmanTable.length);
                System.arraycopy(frameData, segmentDaPos, a, segmentDaPos + UVCTools.mjpgHuffmanTable.length, frameLen - segmentDaPos);
                return a;
            } else
                return null;
        }
    }
    public void processReceivedMJpegVideoFrameKamera(byte[] mjpegFrameData) throws Exception {
        byte[] jpegFrameData = convertMjpegFrameToJpegKamera(mjpegFrameData);
        if (exit == false) {
            final Bitmap bitmap = BitmapFactory.decodeByteArray(jpegFrameData, 0, jpegFrameData.length);
            onListener.onSurfaceTextureUpdated(bitmap);
        }
    }
    private void sendStillImageTrigger() throws Exception {
        byte buf[] = new byte[1];
        buf[0] = 1;
        int len = usbDeviceConnection.controlTransfer(UsbHost.RT_CLASS_INTERFACE_SET, UsbHost.SET_CUR, UsbHost.VS_STILL_IMAGE_TRIGGER_CONTROL << 8, camStreamingInterface.getId(), buf, 1, 1000);
        if (len != 1) {
            throw new Exception("VS_STILL_IMAGE_TRIGGER_CONTROL failed, len=" + len + ".");
        }
    }

    private void openCam() throws Exception {
        openCameraDevice();
        initCamera();

    }
    private void initCamera() throws Exception {
        try {
            getVideoControlErrorCode();
        }                // to reset previous error states
        catch (Exception e) {
        }   // ignore error, some cameras do not support the request
       // enableStreaming(false);
        try {
            getVideoStreamErrorCode();
        }                // to reset previous error states
        catch (Exception e) {
        }   // ignore error, some cameras do not support the request
        initStreamingParms();
        initBrightnessParms();
    }
    private static void packUsbInt(int i, byte[] buf, int pos) {
        UVCTools.packInt(i, buf, pos, false);
    }
    public   String printHexString( byte[] b) {
        String a = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }

            a = a+" "+hex;
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
        System.out.println("streamingParms:"+printHexString(streamingParms));
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
        currentBrightness = unpackIntBrightness(brightnessParms);
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
    private void enableStreaming(boolean enabled) throws Exception {
        enableStreaming_usbFs(enabled);
    }
    private void enableStreaming_usbFs(boolean enabled) throws Exception {
        if (enabled && bulkMode) {
            // clearHalt(camStreamingEndpoint.getAddress());
        }
        int altSetting = enabled ? camStreamingAltSetting : 0;
        // For bulk endpoints, altSetting is always 0.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            usbIso.setInterface(camStreamingInterface.getId(), camStreamingInterface.getAlternateSetting());
        }
        if (!enabled) {
            usbIso.flushRequests();
            if (bulkMode) {
                // clearHalt(camStreamingEndpoint.getAddress());
            }
        }
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
   /* public String getProductName(){
        byte[] rawDescs = usbDeviceConnection.getRawDescriptors();
        String manufacturer = "", product = "";
        try		{
            byte[] buffer = new byte[255];
            int idxMan = rawDescs[14];
            int idxPrd = rawDescs[15];
            int rdo = usbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_IN
                            | UsbConstants.USB_TYPE_STANDARD, STD_USB_REQUEST_GET_DESCRIPTOR,
                    (LIBUSB_DT_STRING << 8) | idxMan, 0, buffer, 0xFF, 0);
            manufacturer = new String(buffer, 2, rdo - 2, "UTF-16LE");
            rdo = mUsbDeviceConnection.controlTransfer(UsbConstants.USB_DIR_IN
                            | UsbConstants.USB_TYPE_STANDARD, STD_USB_REQUEST_GET_DESCRIPTOR,
                    (LIBUSB_DT_STRING << 8) | idxPrd, 0, buffer, 0xFF, 0);
            product = new String(buffer, 2, rdo - 2, "UTF-16LE");
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return product.trim()+manufacturer.trim();
    }
*/

}
