package com.example.bledevicesearch;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by AST on 2017/12/5.
 */

public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.ViewHolder> {

    private List<Device> mDeviceList;

    public DevicesAdapter(List<Device> list) {
        mDeviceList = list;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView deviceNameTxt;
        private TextView deviceAddressTxt;

        public ViewHolder(View itemView) {
            super(itemView);
            this.deviceNameTxt = itemView.findViewById(R.id.tv_device_name);
            this.deviceAddressTxt = itemView.findViewById(R.id.tv_device_address);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        ViewHolder viewHolder = new ViewHolder(view);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.deviceNameTxt.setText(mDeviceList.get(position).getName());
        holder.deviceAddressTxt.setText(mDeviceList.get(position).getAddress());
    }

    @Override
    public int getItemCount() {
        return mDeviceList.size();
    }

    public void clear() {
        mDeviceList.clear();
    }

    public void addDevice(Device device) {
        if(!includeDevice(device)) {
            mDeviceList.add(device);
            notifyItemInserted(mDeviceList.size()-1);
        }
    }

    private boolean includeDevice(Device device) {
        for (Device d : mDeviceList) {
            if (device.getAddress().equals(d.getAddress())) {
                return true;
            }
        }
        return false;
    }
}
