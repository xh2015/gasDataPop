package com.topsky.gasdatapop.mqtt;

import android.content.Context;

import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.SPUtils;
import com.topsky.gasdatapop.DataPopApp;
import com.topsky.gasdatapop.bean.GasInfo;
import com.topsky.gasdatapop.constant.DefaultConstant;
import com.topsky.gasdatapop.constant.SpConstant;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import info.mqtt.android.service.MqttAndroidClient;

public class MQTTManager {
    private static final String TAG = "MQTTManager";
    private Context context;
    private MqttAndroidClient mqttClient;
    private boolean isConnected = false;
    private boolean isInitialized = false;
    private boolean mqttEnable = true;
    private boolean isReconnecting = false;
    private String deviceCode;

    // 线程池
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // MQTT配置参数
    private static final String broker = "tcp://47.114.107.238:1883";
    private static final String username = "admin";
    private static final String password = "public";

    private boolean isShutdown = false;

    private void executeSafely(Runnable task) {
        if (isShutdown || executorService.isShutdown()) {
            return;
        }
        try {
            executorService.execute(task);
        } catch (Exception e) {
            LogUtils.e(TAG, "提交任务失败", e);
        }
    }

    // 私有构造方法
    private MQTTManager() {
        // 初始化设备号，从SP中获取，获取不到使用默认值
        deviceCode = SPUtils.getInstance().getString(SpConstant.DEVICE_CODE, DefaultConstant.deviceCode);
        // 初始化MQTT启用状态，从SP中获取
        mqttEnable = SPUtils.getInstance().getBoolean(SpConstant.MQTT_ENABLE, true);
    }

    // 静态内部类单例模式
    private static class MQTTManagerHolder {
        private static final MQTTManager INSTANCE = new MQTTManager();
    }

    // 获取单例实例
    public static MQTTManager getInstance() {
        return MQTTManagerHolder.INSTANCE;
    }

    // 更新设备号
    public void updateDeviceCode(String newDeviceCode) {
        deviceCode = newDeviceCode;
    }

    // 更新MQTT启用状态
    public void updateMqttEnable(boolean enable) {
        mqttEnable = enable;
        if (mqttEnable) {
            init(DataPopApp.context);
        }
    }

    // 获取客户端ID
    private String clientId;

    private String getClientId() {
        if (clientId == null) {
            clientId = getClientIdFromSp();
        }
        LogUtils.d(TAG, "客户端ID: " + clientId);
        return clientId;
    }

    private String getClientIdFromSp() {
        String clientId = SPUtils.getInstance().getString(SpConstant.CLIENT_ID, null);
        if (clientId == null) {
            clientId = "android_" + UUID.randomUUID().toString();
            SPUtils.getInstance().put(SpConstant.CLIENT_ID, clientId);
        }
        return clientId;
    }

    // 获取动态topic
    private String getTopic() {
        return "/xinjuSafety/iot/lttest/" + deviceCode + "/receive";
    }

    // 初始化MQTT客户端
    public void init(Context ctx) {
        if (isInitialized) {
            LogUtils.d(TAG, "MQTT已经初始化，跳过重复初始化");
            return;
        }

        if (!mqttEnable) {
            LogUtils.d(TAG, "MQTT未启用，跳过初始化");
            return;
        }

        isShutdown = false;
        context = ctx.getApplicationContext();
        executeSafely(() -> {
            LogUtils.d(TAG, "初始化MQTT");
            try {
                mqttClient = new MqttAndroidClient(context, broker, getClientId());
                mqttClient.setCallback(new MqttCallback() {
                    @Override
                    public void connectionLost(Throwable cause) {
                        LogUtils.e(TAG, "连接断开", cause);
                        isConnected = false;
                        // 自动重连
                        executeSafely(() -> autoReconnect());
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        LogUtils.d(TAG, "收到消息: " + new String(message.getPayload()));
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        //LogUtils.d(TAG, "消息发送成功");
                    }
                });
                connect();
                isInitialized = true;
            } catch (Exception e) {
                LogUtils.e(TAG, "初始化失败", e);
            }
        });
    }

    // 自动重连
    private void autoReconnect() {
        if (!mqttEnable) return;

        if (isReconnecting) {
            LogUtils.d(TAG, "重连已在进行中,跳过本次请求");
            return;
        }

        isReconnecting = true;
        int retryCount = 0;
        final int maxRetries = 15;
        final long baseDelay = 5000L; // 基础延迟5秒

        try {
            while (retryCount < maxRetries && !isConnected) {
                retryCount++;
                long currentDelay = Math.min(baseDelay * (1L << (retryCount - 1)), 300000L); // 指数增长,最大5分钟
                LogUtils.d(TAG, "尝试重连 (" + retryCount + "/" + maxRetries + "), 等待 " + (currentDelay / 1000) + "秒...");

                try {
                    connect();
                    if (isConnected) {
                        LogUtils.d(TAG, "重连成功");
                        break;
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "重连失败", e);
                }

                if (retryCount < maxRetries && !isConnected) {
                    try {
                        Thread.sleep(currentDelay);
                    } catch (InterruptedException e) {
                        LogUtils.e(TAG, "重连延迟失败", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (!isConnected) {
                LogUtils.e(TAG, "重连失败,已达到最大重试次数");
            }
        } finally {
            isReconnecting = false;
        }
    }

    // 连接MQTT服务器
    public void connect() {
        executeSafely(() -> {
            try {
                if (mqttEnable && !isConnected && mqttClient != null) {
                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setUserName(username);
                    options.setPassword(password.toCharArray());
                    options.setKeepAliveInterval(60);
                    options.setAutomaticReconnect(true); // 启用自动重连

                    mqttClient.connect(options, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            LogUtils.d(TAG, "连接成功");
                            //mqttClient.subscribe(getTopic(), 0);
                            isConnected = true;
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            LogUtils.e(TAG, "连接失败", exception);
                            //mqttClient.unsubscribe(getTopic());
                            isConnected = false;
                            // 连接失败时触发自动重连
                            executeSafely(() -> autoReconnect());
                        }
                    });
                }
            } catch (Exception e) {
                LogUtils.e(TAG, "连接异常", e);
                isConnected = false;
                // 异常时触发自动重连
                executeSafely(() -> autoReconnect());
            }
        });
    }

    // 发布消息
    public void publishMessage(String message) {
        executeSafely(() -> {
            try {
                if (mqttEnable && isConnected && mqttClient != null) {
                    LogUtils.d(TAG, "发布消息: " + message);
                    MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                    mqttMessage.setQos(1);
                    IMqttDeliveryToken token = mqttClient.publish(getTopic(), mqttMessage);
                    token.setActionCallback(new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            LogUtils.d(TAG, "消息发布成功");
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            LogUtils.e(TAG, "消息发布失败", exception);
                        }
                    });
                } else if (!mqttEnable) {
                    LogUtils.d(TAG, "MQTT未启用，无法发布消息");
                } else {
                    LogUtils.d(TAG, "MQTT未连接，无法发布消息");
                    connect();
                }
            } catch (Exception e) {
                LogUtils.d(TAG, "发布消息异常", e);
            }
        });
    }

    // 发布设备数据
    public void publishDeviceData(String deviceCode, List<GasInfo> gasBeanList) {
        try {
            JSONArray dataArr = new JSONArray();
            for (GasInfo gasInfo : gasBeanList) {
                if (gasInfo == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                item.put(gasInfo.getEnName(), String.valueOf(gasInfo.getValue()));
                item.put("unit", gasInfo.getUnit());
                dataArr.put(item);
            }
            JSONObject root = new JSONObject();
            root.put("deviceCode", deviceCode);
            root.put("data", dataArr);
            publishMessage(root.toString());
        } catch (JSONException e) {
            LogUtils.e(TAG, "构建JSON失败", e);
        }
    }

    // 发布汇总数据
    public void push2Cloud(List<GasInfo> gasBeanList) {
        if (gasBeanList == null || gasBeanList.isEmpty()) {
            return;
        }
        if (!(mqttEnable && isConnected && mqttClient != null)) {
            return;
        }

        publishDeviceData(deviceCode, gasBeanList);
    }

    // 释放MQTT资源
    public void release() {
        try {
            executeSafely(() -> {
                try {
                    if (isConnected && mqttClient != null) {
                        IMqttToken token = mqttClient.disconnect();
                        token.setActionCallback(new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                LogUtils.d(TAG, "断开连接成功");
                                isConnected = false;
                                // 断开连接成功后释放资源
                                cleanupResources();
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                LogUtils.e(TAG, "断开连接失败", exception);
                                // 即使断开连接失败也要释放资源
                                cleanupResources();
                            }
                        });
                    } else {
                        // 未连接或未初始化时直接释放资源
                        cleanupResources();
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "释放资源异常", e);
                    // 异常时也要释放资源
                    cleanupResources();
                }
            });
        } catch (Exception e) {

        }
    }

    // 清理资源
    private void cleanupResources() {
        isShutdown = true;
        try {
            if (mqttClient != null) {
                mqttClient.close();
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "关闭mqttClient异常", e);
        }
        //关闭mqtt
        //MQTTManager.getInstance().release();
        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            LogUtils.e(TAG, "关闭线程池异常", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        isInitialized = false;
        isConnected = false;
        LogUtils.d(TAG, "资源释放。。。");
    }
}