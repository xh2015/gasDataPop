package com.topsky.gasdatapop.bean;

public class RadiationData {
    private float doseRate;
    private String doseRateUnit;
    private float cumulativeDose;
    private String cumulativeDoseUnit;
    private float temperature;
    private String temperatureUnit;
    private float batteryVoltage;

    public float getDoseRate() {
        return doseRate;
    }

    public void setDoseRate(float doseRate) {
        this.doseRate = doseRate;
    }

    public String getDoseRateUnit() {
        return doseRateUnit == null ? "" : doseRateUnit;
    }

    public void setDoseRateUnit(String doseRateUnit) {
        this.doseRateUnit = doseRateUnit;
    }

    public float getCumulativeDose() {
        return cumulativeDose;
    }

    public void setCumulativeDose(float cumulativeDose) {
        this.cumulativeDose = cumulativeDose;
    }

    public String getCumulativeDoseUnit() {
        return cumulativeDoseUnit == null ? "" : cumulativeDoseUnit;
    }

    public void setCumulativeDoseUnit(String cumulativeDoseUnit) {
        this.cumulativeDoseUnit = cumulativeDoseUnit;
    }

    public float getTemperature() {
        return temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public String getTemperatureUnit() {
        return temperatureUnit == null ? "" : temperatureUnit;
    }

    public void setTemperatureUnit(String temperatureUnit) {
        this.temperatureUnit = temperatureUnit;
    }

    public float getBatteryVoltage() {
        return batteryVoltage;
    }

    public void setBatteryVoltage(float batteryVoltage) {
        this.batteryVoltage = batteryVoltage;
    }
}