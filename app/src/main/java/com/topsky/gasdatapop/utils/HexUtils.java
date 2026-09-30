package com.topsky.gasdatapop.utils;

public class HexUtils {
    // 字节数组转十六进制字符串 (辅助方法)
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = String.format("%02X ", b);
            hexString.append(hex);
        }
        return hexString.toString().trim();
    }
}
