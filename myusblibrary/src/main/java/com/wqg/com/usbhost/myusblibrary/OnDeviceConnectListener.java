package com.wqg.com.usbhost.myusblibrary;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;

public interface OnDeviceConnectListener {
    public void onConnect(UsbDevice usbDevice);
    /**
     * called when USB device removed or its power off (this callback is called after device closing)
     * @param usbDevice
     */
    public void onDisconnect(UsbDevice usbDevice );
    public void openDevice(UsbDevice usbDevice, UsbDeviceConnection usbDeviceConnection );
    public void onPermission(int code,UsbDevice usbDevice);
    public void onErr(int code,UsbDevice usbDevice ,String m);
}
