package com.topsky.gasdatapop.bean;

public class GasInfo {
    private int type;
    private float threshold;
    private String name;
    private String unit;
    private float value;
    private String enName;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public float getThreshold() {
        return threshold;
    }

    public void setThreshold(float threshold) {
        this.threshold = threshold;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = value;
    }

    public String getEnName() {
        return enName;
    }

    public void setEnName(String enName) {
        this.enName = enName;
    }

    public boolean isWarn() {
        if (type == 2) {
            //氧气
            return value < threshold;
        }
        return value > threshold;
    }
}