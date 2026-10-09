package com.topsky.gasdatapop.utils;

import com.blankj.utilcode.util.LogUtils;
import com.topsky.gasdatapop.bean.GasInfo;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class ProtocolParser {
    private static final String TAG = "ProtocolParser";
    private static final int FRAME_START = 0x68;
    private static final int FRAME_END = 0x16;
    private static final int PREAMBLE = 0xFE;

    public static final int CTRL_ACK = 0x00;
    public static final int CTRL_REALTIME = 0x01;
    public static final int CTRL_HISTORY = 0x02;
    public static final int CTRL_ALARM = 0x03;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private OnFrameParsedListener listener;

    public interface OnFrameParsedListener {
        void onRealtimeData(int address, List<GasInfo> gasInfoList);

        void onAlarmData(int address, List<GasInfo> gasInfoList);

        void onAck(int address, boolean success);
    }

    public ProtocolParser(OnFrameParsedListener listener) {
        this.listener = listener;
    }

    public void feed(byte[] data) {
        buffer.write(data, 0, data.length);
        tryParse();
    }

    public void reset() {
        buffer.reset();
    }

    private void tryParse() {
        byte[] bytes = buffer.toByteArray();
        int pos = 0;

        while (pos < bytes.length) {
            while (pos < bytes.length && (bytes[pos] & 0xFF) == PREAMBLE) {
                pos++;
            }

            if (pos >= bytes.length) break;

            if ((bytes[pos] & 0xFF) != FRAME_START) {
                pos++;
                continue;
            }

            if (bytes.length - pos < 6) {
                break;
            }

            int address = bytes[pos + 1] & 0xFF;
            int length = bytes[pos + 2] & 0xFF;

            int frameLen = length + 5;

            if (bytes.length - pos < frameLen) {
                break;
            }

            if ((bytes[pos + frameLen - 1] & 0xFF) != FRAME_END) {
                LogUtils.w(TAG, "帧结束符错误,跳过 pos=" + pos);
                pos++;
                continue;
            }

            int cs = 0;
            for (int i = pos; i < pos + frameLen - 2; i++) {
                cs += bytes[i] & 0xFF;
            }
            cs = cs & 0xFF;

            int expectedCs = bytes[pos + frameLen - 2] & 0xFF;

            if (cs != expectedCs) {
                LogUtils.w(TAG, "校验和错误 expected=" + expectedCs + " actual=" + cs + ",跳过 pos=" + pos);
                pos++;
                continue;
            }

            int ctrlCode = bytes[pos + 3] & 0xFF;

            switch (ctrlCode) {
                case CTRL_ACK:
                    parseAck(address, bytes, pos, length);
                    break;
                case CTRL_REALTIME:
                    parseRealtimeData(address, bytes, pos, length);
                    break;
                case CTRL_HISTORY:
                    LogUtils.d(TAG, "历史数据帧(暂不处理)");
                    break;
                case CTRL_ALARM:
                    parseAlarmData(address, bytes, pos, length);
                    break;
                default:
                    LogUtils.w(TAG, "未知控制码: 0x" + String.format("%02X", ctrlCode));
                    break;
            }

            pos += frameLen;
        }

        if (pos > 0) {
            byte[] remaining = new byte[bytes.length - pos];
            System.arraycopy(bytes, pos, remaining, 0, remaining.length);
            buffer.reset();
            buffer.write(remaining, 0, remaining.length);
        }
    }

    private void parseAck(int address, byte[] bytes, int pos, int length) {
        if (length >= 2) {
            int d0 = bytes[pos + 4] & 0xFF;
            if (listener != null) {
                listener.onAck(address, d0 == 1);
            }
        }
    }

    private void parseRealtimeData(int address, byte[] bytes, int pos, int length) {
        int dataLen = length - 1;
        int dataStart = pos + 4;
        List<GasInfo> gasInfoList = parseDataGroups(bytes, dataStart, dataLen);
        if (listener != null) {
            listener.onRealtimeData(address, gasInfoList);
        }
    }

    private void parseAlarmData(int address, byte[] bytes, int pos, int length) {
        int dataLen = length - 1;
        int dataStart = pos + 4;
        List<GasInfo> gasInfoList = parseDataGroups(bytes, dataStart, dataLen);
        if (listener != null) {
            listener.onAlarmData(address, gasInfoList);
        }
    }

    private List<GasInfo> parseDataGroups(byte[] bytes, int start, int dataLen) {
        List<GasInfo> result = new ArrayList<>();
        int pos = start;
        int end = start + dataLen;

        while (pos < end) {
            if (pos >= bytes.length) break;

            int type = bytes[pos] & 0xFF;
            pos++;

            int valueLen = (type == 20) ? 4 : 2;
            if (pos + valueLen > bytes.length || pos + valueLen > end) {
                LogUtils.w(TAG, "数据域不完整 type=" + type);
                break;
            }

            int rawValue;
            if (valueLen == 4) {
                rawValue = ((bytes[pos] & 0xFF) << 24) | ((bytes[pos + 1] & 0xFF) << 16) |
                        ((bytes[pos + 2] & 0xFF) << 8) | (bytes[pos + 3] & 0xFF);
            } else {
                rawValue = ((bytes[pos] & 0xFF) << 8) | (bytes[pos + 1] & 0xFF);
            }
            pos += valueLen;

            GasInfo gasInfo = GasInfo.fromRawValue(type, rawValue);
            if (gasInfo != null) {
                result.add(gasInfo);
            }
        }

        return result;
    }

    public static byte[] buildReadRealtimeCmd(int address) {
        int cs = (FRAME_START + (address & 0xFF) + 0x01 + CTRL_REALTIME) & 0xFF;
        return new byte[]{
                (byte) PREAMBLE, (byte) PREAMBLE, (byte) FRAME_START,
                (byte) address, 0x01, (byte) CTRL_REALTIME, (byte) cs, (byte) FRAME_END
        };
    }

    public static byte[] buildReadAlarmCmd(int address) {
        int cs = (FRAME_START + (address & 0xFF) + 0x01 + CTRL_ALARM) & 0xFF;
        return new byte[]{
                (byte) PREAMBLE, (byte) PREAMBLE, (byte) FRAME_START,
                (byte) address, 0x01, (byte) CTRL_ALARM, (byte) cs, (byte) FRAME_END
        };
    }
}