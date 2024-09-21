package com.example.gpsmocklocation;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class UsbDeviceAdapter extends ArrayAdapter<UsbDevice> {
    public UsbDeviceAdapter(Context context, List<UsbDevice> usbDevices) {
        super(context, android.R.layout.simple_spinner_item, usbDevices);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView text = (TextView) view.findViewById(android.R.id.text1);
        text.setText(getDeviceDisplayName(getItem(position)));
        return view;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        View view = super.getDropDownView(position, convertView, parent);
        TextView text = (TextView) view.findViewById(android.R.id.text1);
        text.setText(getDeviceDisplayName(getItem(position)));
        return view;
    }

    private String getDeviceDisplayName(UsbDevice device) {
        return String.format("%s - %04X:%04X", device.getDeviceName(), device.getVendorId(), device.getProductId());
    }
}