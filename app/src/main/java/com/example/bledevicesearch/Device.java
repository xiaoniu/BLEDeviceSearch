package com.example.bledevicesearch;

/**
 * Created by LAA niujiajun on 2017/12/5.
 */

public class Device {

    private String name;
    private String address;

    public Device(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }
}
