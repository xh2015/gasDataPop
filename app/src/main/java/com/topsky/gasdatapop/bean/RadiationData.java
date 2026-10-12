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
        return Math.round(doseRate * 1000) / 1000.0f;
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
        return Math.round(cumulativeDose * 10000) / 10000.0f;
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
        return Math.round(temperature * 10) / 10.0f;
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
        return Math.round(batteryVoltage * 100) / 100.0f;
    }

    public void setBatteryVoltage(float batteryVoltage) {
        this.batteryVoltage = batteryVoltage;
    }
}