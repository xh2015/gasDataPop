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

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private OnFrameParsedListener listener;

    public interface OnFrameParsedListener {
        void onRealtimeData(int address, List<GasInfo> gasInfoList);
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

            if (bytes.length - pos < 5) {
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
            if (length == 1 && (bytes[pos + 3] & 0xFF) == 0x01) {
                LogUtils.d(TAG, "请求帧回显,跳过");
            } else if (length > 0) {
                parseRealtimeData(address, bytes, pos, length);
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

    private void parseRealtimeData(int address, byte[] bytes, int pos, int length) {
        int dataStart = pos + 3;
        List<GasInfo> gasInfoList = parseDataGroups(bytes, dataStart, length);
        if (listener != null) {
            listener.onRealtimeData(address, gasInfoList);
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
        int cs = (FRAME_START + (address & 0xFF) + 0x01 + 0x01) & 0xFF;
        return new byte[]{
                (byte) PREAMBLE, (byte) PREAMBLE, (byte) FRAME_START,
                (byte) address, 0x01, 0x01, (byte) cs, (byte) FRAME_END
        };
    }
}