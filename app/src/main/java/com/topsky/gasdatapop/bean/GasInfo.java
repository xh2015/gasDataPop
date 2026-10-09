package com.topsky.gasdatapop.bean;

public class GasInfo {
    private int type;
    private Float threshold;
    private String name;
    private String unit;
    private float value;
    private String enName;
    private int decimalPlaces;

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setThreshold(Float threshold) {
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
        if (threshold == null) {
            return false;
        }
        if (type == 2) {
            return value < threshold;
        }
        return value > threshold;
    }

    public String getDisplayValue() {
        if (decimalPlaces <= 0) {
            return String.valueOf(Math.round(value));
        }
        String format = "%." + decimalPlaces + "f";
        return String.format(format, value);
    }

    public static GasInfo fromRawValue(int type, int rawValue) {
        GasInfo info = new GasInfo();
        info.setType(type);
        switch (type) {
            case 1:
                info.setName("甲烷");
                info.setEnName("CH4");
                info.setUnit("%");
                info.setValue(rawValue * 0.01f);
                info.decimalPlaces = 2;
                break;
            case 2:
                info.setName("氧气");
                info.setEnName("O2");
                info.setUnit("%");
                info.setValue(rawValue * 0.1f);
                info.decimalPlaces = 1;
                break;
            case 3:
                info.setName("一氧化碳");
                info.setEnName("CO");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 4:
                info.setName("硫化氢");
                info.setEnName("H2S");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 5:
                info.setName("二氧化碳");
                info.setEnName("CO2");
                info.setUnit("%");
                info.setValue(rawValue * 0.01f);
                info.decimalPlaces = 2;
                break;
            case 6:
                info.setName("压力");
                info.setEnName("Pressure");
                info.setUnit("Pa");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 7:
                info.setName("温度");
                info.setEnName("Temp");
                info.setUnit("℃");
                info.setValue((rawValue - 400) * 0.1f);
                info.decimalPlaces = 1;
                break;
            case 8:
                info.setName("湿度");
                info.setEnName("RH");
                info.setUnit("%RH");
                info.setValue(rawValue * 0.1f);
                info.decimalPlaces = 1;
                break;
            case 9:
                info.setName("氢气");
                info.setEnName("H2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 10:
                info.setName("乙烯");
                info.setEnName("C2H4");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 13:
                info.setName("二氧化硫");
                info.setEnName("SO2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 14:
                info.setName("二氧化氮");
                info.setEnName("NO2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 15:
                info.setName("一氧化氮");
                info.setEnName("NO");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 16:
                info.setName("氨气");
                info.setEnName("NH3");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 17:
                info.setName("氯气");
                info.setEnName("Cl2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 18:
                info.setName("VOC");
                info.setEnName("VOC");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 19:
                info.setName("风速");
                info.setEnName("WindSpd");
                info.setUnit("m/s");
                info.setValue(rawValue * 0.1f);
                info.decimalPlaces = 1;
                break;
            case 20:
                info.setName("X/Y射线");
                info.setEnName("Radiation");
                info.setUnit("uSv/h");
                info.setValue(rawValue * 0.01f);
                info.decimalPlaces = 2;
                break;
            case 21:
                info.setName("噪声");
                info.setEnName("Noise");
                info.setUnit("dB");
                info.setValue(rawValue * 0.1f);
                info.decimalPlaces = 1;
                break;
            case 22:
                info.setName("风向");
                info.setEnName("WindDir");
                info.setUnit("°");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 23:
                info.setName("辐射热");
                info.setEnName("RadHeat");
                info.setUnit("℃");
                info.setValue(rawValue * 0.01f);
                info.decimalPlaces = 2;
                break;
            case 24:
                info.setName("PM2.5");
                info.setEnName("PM2.5");
                info.setUnit("ug/m³");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 25:
                info.setName("PM10");
                info.setEnName("PM10");
                info.setUnit("ug/m³");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 26:
                info.setName("臭氧");
                info.setEnName("O3");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 27:
                info.setName("磷化氢");
                info.setEnName("PH3");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 28:
                info.setName("氢气");
                info.setEnName("H2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 29:
                info.setName("HCL");
                info.setEnName("HCL");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 30:
                info.setName("HCN");
                info.setEnName("HCN");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 31:
                info.setName("氮气");
                info.setEnName("N2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 32:
                info.setName("乙烷");
                info.setEnName("C2H6");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 33:
                info.setName("SF6");
                info.setEnName("SF6");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 34:
                info.setName("乙炔");
                info.setEnName("C2H2");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 35:
                info.setName("丙烷");
                info.setEnName("C3H8");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 36:
                info.setName("乙醛");
                info.setEnName("ETO");
                info.setUnit("ppm");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
            case 129:
                info.setName("电压");
                info.setEnName("Voltage");
                info.setUnit("V");
                info.setValue(rawValue * 0.01f);
                info.decimalPlaces = 2;
                break;
            default:
                info.setName("未知");
                info.setEnName("UNK" + type);
                info.setUnit("");
                info.setValue(rawValue);
                info.decimalPlaces = 0;
                break;
        }
        return info;
    }
}