package com.wqg.com.usbhost.myusblibrary;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.view.Surface;
import android.view.TextureView;

import java.io.IOException;

public class UsbUvcCamera {
    int width=1920;
    int height=1080;
    Surface surface;
    UsbUvcCamera.PreviewCallback cb;
    UsbHost usbHost;
    UsbUvc usbUvc;
    UsbHost.Device device;
    Rect rect=new Rect(0,0,width,height);
    int rotation;
    public UsbUvcCamera(Context context){
        usbHost=UsbHost.getInstance(context);
    }
    public void open(int cameraId) {
        device=usbHost.getDevice(cameraId);
        device.openDevice();
        usbUvc=new UsbUvc(device);
        usbUvc.setOnListener(onListener);
        usbUvc.init(7,
                1,
                9,
                333333,
                8,
                3027,
                1920,
                1080,
                8,
                "mjpeg");
    }
    UsbUvc.OnListener onListener=new UsbUvc.OnListener() {
        @Override
        public void onSurfaceTextureUpdated(Bitmap var1) {
            if (surface!=null){
                try {
                    if (surface!=null){
                        Canvas canvas=surface.lockCanvas(rect);
                        if (rotation!=0){
                            var1=adjustPhotoRotation(var1,rotation);
                        }
                        canvas.drawBitmap(var1, null, rect, null);
                        //canvas.rotate(rotation);
                        surface.unlockCanvasAndPost(canvas);
                    }
                }catch (IllegalArgumentException e){
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    };
    public void setDisplayOrientation(int var1){
        rotation=var1;
    }

    public void setPreviewTexture(SurfaceTexture var1) throws IOException{
        surface=new Surface(var1);
    }
    public int getNumberOfCameras() {
        int i=0;
        for (String key:usbHost.keySet()){
            UsbDevice usbDevice=usbHost.get(key).getUsbDevice();
            if (UVCTools.getVideoStreamingInterface(usbDevice)!=null)i++;
        }
        return i;
    }

    public void startPreview(){
        if (usbUvc!=null){
            usbUvc.isoStream();
        }
    }
    Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);
        float targetX, targetY;
        if (orientationDegree == 90) {
            targetX = bm.getHeight();
            targetY = 0;
        } else {
            targetX = bm.getHeight();
            targetY = bm.getWidth();
        }
        final float[] values = new float[9];
        m.getValues(values);
        float x1 = values[Matrix.MTRANS_X];
        float y1 = values[Matrix.MTRANS_Y];
        m.postTranslate(targetX - x1, targetY - y1);
        Bitmap bm1 = Bitmap.createBitmap(bm.getHeight(), bm.getWidth(), Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        Canvas canvas = new Canvas(bm1);
        canvas.drawBitmap(bm, m, paint);
        return bm1;
    }


    public void stopPreview() {

    }

    public void setPreviewCallback(UsbUvcCamera.PreviewCallback cb) {
      this.cb=cb;
    }
    /** @deprecated */
    @Deprecated
    public interface PreviewCallback {
        void onPreviewFrame(byte[] var1, UsbUvcCamera var2);
    }
}
