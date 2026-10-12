package com.topsky.gasdatapop.modbus;

import android.os.Handler;
import android.os.Looper;

import com.blankj.utilcode.util.LogUtils;
import com.topsky.gasdatapop.bean.RadiationData;
import com.topsky.gasdatapop.tcp.TcpClient;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

public class ModbusRtuManager {
    private static final String TAG = "ModbusRtuManager";

    private static final int SLAVE_ADDRESS = 0x07;
    private static final int FUNCTION_CODE = 0x04;
    private static final int REGISTER_COUNT = 3;
    private static final long POLL_INTERVAL_MS = 1000;

    private static final int ITEM_COUNT = 4;
    private static final int INDEX_DOSE_RATE = 0;
    private static final int INDEX_CUMULATIVE_DOSE = 1;
    private static final int INDEX_TEMPERATURE = 2;
    private static final int INDEX_BATTERY_VOLTAGE = 3;

    private static final int[] START_ADDRESSES = {
            0x0000,
            0x0007,
            0x000E,
            0x0015
    };

    private static final String[] ITEM_NAMES = {
            "实时剂量率",
            "累计剂量",
            "环境温度",
            "锂电池电压"
    };

    private final TcpClient tcpClient;
    private final Handler mainHandler;
    private final ByteArrayOutputStream receiveBuffer;
    private final RadiationData radiationData;

    private int nextSendIndex = 0;
    private int pendingItemIndex = -1;
    private boolean isPolling = false;
    private ModbusDataListener listener;
    private final Runnable pollRunnable;

    public interface ModbusDataListener {
        void onDataUpdated(RadiationData data);
    }

    public ModbusRtuManager(TcpClient tcpClient) {
        this.tcpClient = tcpClient;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.receiveBuffer = new ByteArrayOutputStream();
        this.radiationData = new RadiationData();
        this.pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;
                sendRequest(nextSendIndex);
                pendingItemIndex = nextSendIndex;
                nextSendIndex = (nextSendIndex + 1) % ITEM_COUNT;
                mainHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
    }

    public void setListener(ModbusDataListener listener) {
        this.listener = listener;
    }

    public void startPolling() {
        if (isPolling) return;
        isPolling = true;
        nextSendIndex = 0;
        pendingItemIndex = -1;
        synchronized (receiveBuffer) {
            receiveBuffer.reset();
        }
        mainHandler.post(pollRunnable);
        LogUtils.d(TAG, "Polling started");
    }

    public void stopPolling() {
        isPolling = false;
        mainHandler.removeCallbacks(pollRunnable);
        pendingItemIndex = -1;
        synchronized (receiveBuffer) {
            receiveBuffer.reset();
        }
        LogUtils.d(TAG, "Polling stopped");
    }

    public void release() {
        stopPolling();
        listener = null;
    }

    public void onRawDataReceived(byte[] data) {
        synchronized (receiveBuffer) {
            receiveBuffer.write(data, 0, data.length);
            tryParseFrames();
        }
    }

    private void sendRequest(int itemIndex) {
        byte[] request = buildRequestFrame(START_ADDRESSES[itemIndex]);
        LogUtils.d(TAG, "Send [" + ITEM_NAMES[itemIndex] + "] addr=0x"
                + String.format(Locale.US, "%04X", START_ADDRESSES[itemIndex])
                + " frame=" + bytesToHex(request));
        synchronized (receiveBuffer) {
            receiveBuffer.reset();
        }
        if (tcpClient != null) {
            tcpClient.send(request);
        }
    }

    private static byte[] buildRequestFrame(int startAddress) {
        byte[] frame = new byte[8];
        frame[0] = (byte) SLAVE_ADDRESS;
        frame[1] = (byte) FUNCTION_CODE;
        frame[2] = (byte) ((startAddress >> 8) & 0xFF);
        frame[3] = (byte) (startAddress & 0xFF);
        frame[4] = (byte) ((REGISTER_COUNT >> 8) & 0xFF);
        frame[5] = (byte) (REGISTER_COUNT & 0xFF);
        int crc = calculateCrc16(frame, 6);
        frame[6] = (byte) (crc & 0xFF);
        frame[7] = (byte) ((crc >> 8) & 0xFF);
        return frame;
    }

    private void tryParseFrames() {
        byte[] buf = receiveBuffer.toByteArray();
        int pos = 0;

        while (pos < buf.length) {
            if (buf[pos] != (byte) SLAVE_ADDRESS) {
                pos++;
                continue;
            }
            if (pos + 2 >= buf.length) break;
            if (buf[pos + 1] != (byte) FUNCTION_CODE) {
                pos++;
                continue;
            }
            int byteCount = buf[pos + 2] & 0xFF;
            int frameLen = 1 + 1 + 1 + byteCount + 2;
            if (pos + frameLen > buf.length) break;

            byte[] frame = new byte[frameLen];
            System.arraycopy(buf, pos, frame, 0, frameLen);

            int calculatedCrc = calculateCrc16(frame, frameLen - 2);
            int receivedCrcLow = frame[frameLen - 2] & 0xFF;
            int receivedCrcHigh = frame[frameLen - 1] & 0xFF;
            int receivedCrc = (receivedCrcHigh << 8) | receivedCrcLow;

            if (calculatedCrc != receivedCrc) {
                LogUtils.w(TAG, "CRC mismatch at pos=" + pos + ", skip byte");
                pos++;
                continue;
            }

            parseResponseFrame(frame);
            pos += frameLen;
        }

        if (pos > 0) {
            byte[] remaining = new byte[buf.length - pos];
            System.arraycopy(buf, pos, remaining, 0, remaining.length);
            receiveBuffer.reset();
            receiveBuffer.write(remaining, 0, remaining.length);
        }
    }

    private void parseResponseFrame(byte[] frame) {
        int byteCount = frame[2] & 0xFF;
        if (byteCount != 6) {
            LogUtils.w(TAG, "Unexpected byte count: " + byteCount);
            return;
        }

        float value = parseFloat32Dcba(frame, 3);
        int unitCode = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);

        int itemIndex = pendingItemIndex;
        if (itemIndex < 0) {
            LogUtils.w(TAG, "No pending request, discard response");
            return;
        }

        LogUtils.d(TAG, "Parsed [" + ITEM_NAMES[itemIndex] + "] value=" + value + " unitCode=" + unitCode);
        updateRadiationData(itemIndex, value, unitCode);
        pendingItemIndex = -1;
        notifyDataUpdated();
    }

    private void updateRadiationData(int itemIndex, float value, int unitCode) {
        switch (itemIndex) {
            case INDEX_DOSE_RATE:
                radiationData.setDoseRate(value);
                radiationData.setDoseRateUnit(mapDoseRateUnit(unitCode));
                break;
            case INDEX_CUMULATIVE_DOSE:
                radiationData.setCumulativeDose(value);
                radiationData.setCumulativeDoseUnit(mapCumulativeDoseUnit(unitCode));
                break;
            case INDEX_TEMPERATURE:
                radiationData.setTemperature(value);
                radiationData.setTemperatureUnit(mapTemperatureUnit(unitCode));
                break;
            case INDEX_BATTERY_VOLTAGE:
                radiationData.setBatteryVoltage(value);
                break;
        }
    }

    private static String mapDoseRateUnit(int code) {
        switch (code) {
            case 4: return "μSv/h";
            case 5: return "CPM";
            case 6: return "CPS";
            default: return "μSv/h";
        }
    }

    private static String mapCumulativeDoseUnit(int code) {
        switch (code) {
            case 4: return "μSv";
            case 5: return "CPM";
            case 6: return "CPS";
            case 7: return "Bq/L";
            default: return "μSv";
        }
    }

    private static String mapTemperatureUnit(int code) {
        switch (code) {
            case 1: return "℃";
            case 2: return "℉";
            default: return "℃";
        }
    }

    private static float parseFloat32Dcba(byte[] data, int offset) {
        int bits = ((data[offset + 3] & 0xFF) << 24)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 1] & 0xFF) << 8)
                | (data[offset] & 0xFF);
        return Float.intBitsToFloat(bits);
    }

    static int calculateCrc16(byte[] data, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (data[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc;
    }

    private void notifyDataUpdated() {
        if (listener != null) {
            listener.onDataUpdated(radiationData);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format(Locale.US, "%02X ", b));
        }
        return sb.toString().trim();
    }
}