package com.topsky.gasdatapop.ui;

import android.content.Intent;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.blankj.utilcode.util.CollectionUtils;
import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.LogUtils;
import com.blankj.utilcode.util.SPUtils;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.permission.PermissionLists;
import com.hjq.window.EasyWindow;
import com.hjq.window.EasyWindowManager;
import com.hjq.window.OnWindowViewClickListener;
import com.hjq.window.draggable.MovingWindowDraggableRule;
import com.skydroid.rcsdk.PipelineManager;
import com.skydroid.rcsdk.RCSDKManager;
import com.skydroid.rcsdk.SDKManagerCallBack;
import com.skydroid.rcsdk.comm.CommListener;
import com.skydroid.rcsdk.common.error.SkyException;
import com.skydroid.rcsdk.common.pipeline.Pipeline;
import com.topsky.gasdatapop.R;
import com.topsky.gasdatapop.base.BaseActivity;
import com.topsky.gasdatapop.bean.GasInfo;
import com.topsky.gasdatapop.constant.DefaultConstant;
import com.topsky.gasdatapop.constant.SpConstant;
import com.topsky.gasdatapop.databinding.ActivityMainBinding;
import com.topsky.gasdatapop.mqtt.MQTTManager;
import com.topsky.gasdatapop.utils.HexUtils;
import com.topsky.gasdatapop.utils.ProtocolParser;

import java.util.List;

import androidx.annotation.Nullable;

public class MainActivity extends BaseActivity<ActivityMainBinding> implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private static final int CLICK_THRESHOLD = 6;
    private static final long CLICK_INTERVAL_MS = 2000L;
    private EasyWindow easyWindowGAS;
    private int clickCount = 0;
    private long lastClickTime = 0;

    @Override
    protected ActivityMainBinding initViewBinding(LayoutInflater inflater) {
        return ActivityMainBinding.inflate(inflater);
    }

    @Override
    protected void init() {
        // 添加标志保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPermission();
        //initView();
        //initMqtt();
        initTcpClient();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBootLaunch();
    }

    private void handleBootLaunch() {
        try {
            Intent intent = getIntent();
            if (intent != null && intent.getBooleanExtra("launched_from_boot", false)) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        moveTaskToBack(true);
                    }
                }, 500);
                LogUtils.i(TAG, "Launched from boot; moving to background.");
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "handleBootLaunch error: " + e.getMessage(), e);
        }
    }

    private void checkPermission() {
        XXPermissions.with(this)
                .permission(PermissionLists.getSystemAlertWindowPermission())
                .request((grantedList, deniedList) -> {
                    if (CollectionUtils.isNotEmpty(grantedList)) {
                        initPop();
                    } else {
                        Toast.makeText(MainActivity.this, R.string.set_app_float_permission_tip, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void initView() {
        String deviceCode = SPUtils.getInstance().getString(SpConstant.DEVICE_CODE, DefaultConstant.deviceCode);
        binding.etDeviceCode.setText(deviceCode);
        binding.tvGas.setOnClickListener(v -> handleGasTextClick());
        binding.btnSaveDeviceCode.setOnClickListener(this);
    }

    private void handleGasTextClick() {
        long now = System.currentTimeMillis();
        if (now - lastClickTime > CLICK_INTERVAL_MS) {
            clickCount = 0;
        }
        clickCount++;
        lastClickTime = now;
        if (clickCount >= CLICK_THRESHOLD) {
            clickCount = 0;
            int visibility = binding.cardDeviceCode.getVisibility();
            binding.cardDeviceCode.setVisibility(visibility == View.VISIBLE ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_save_device_code) {
            String code = binding.etDeviceCode.getText().toString().trim();
            if (code.isEmpty()) {
                Toast.makeText(this, R.string.ty_device_code, Toast.LENGTH_SHORT).show();
                return;
            }
            SPUtils.getInstance().put(SpConstant.DEVICE_CODE, code);
            MQTTManager.getInstance().updateDeviceCode(code);
            Toast.makeText(this, R.string.ty_save_success, Toast.LENGTH_SHORT).show();
            binding.cardDeviceCode.setVisibility(View.GONE);
            KeyboardUtils.hideSoftInput(binding.etDeviceCode);
        }
    }

    private void initPop() {
        // 传入 Activity 对象表示设置成局部的，不需要有悬浮窗权限
        // 传入 Application 对象表示设置成全局的，但需要有悬浮窗权限
        // noinspection unchecked
        easyWindowGAS = EasyWindow.with(this.getApplication())
                .setContentView(R.layout.pop_gas_data)
                .setWindowTag("gas")
                //.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
                // 设置成可拖拽的
                .setWindowDraggableRule(new MovingWindowDraggableRule())
                .setWindowLocationPercent(0.68f, 0.1f)
                .setOnClickListenerByView(R.id.iv_close, (OnWindowViewClickListener<ImageView>) (easyWindow, view) -> {
                    easyWindow.setVisibilityByView(R.id.iv_close, View.GONE);
                    easyWindow.setVisibilityByView(R.id.nested_scroll_view, View.GONE);
                }).setOnClickListenerByView(R.id.tvDeviceTitle, (OnWindowViewClickListener<TextView>) (easyWindow, view) -> {
                    easyWindow.setVisibilityByView(R.id.iv_close, View.VISIBLE);
                    easyWindow.setVisibilityByView(R.id.nested_scroll_view, View.VISIBLE);
                });
        easyWindowGAS.show();
    }

    //region 遥控器通信
    private Pipeline pipeline;
    private long lastDataReceivedTime;
    private boolean isDeviceOnline = false;
    private static final long DATA_CHECK_INTERVAL = 20_000L;
    private final Runnable dataCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - lastDataReceivedTime > DATA_CHECK_INTERVAL) {
                changeDeviceStatus(false);
                isDeviceOnline = false;
                hasFetchedAlarm = false;
            }
            postDelayed(this, DATA_CHECK_INTERVAL);
        }
    };

    private boolean hasFetchedAlarm = false;
    private final SparseArray<Float> thresholdMap = new SparseArray<>();
    private final ProtocolParser protocolParser = new ProtocolParser(new ProtocolParser.OnFrameParsedListener() {
        @Override
        public void onRealtimeData(int address, List<GasInfo> gasInfoList) {
            for (GasInfo gasInfo : gasInfoList) {
                Float threshold = thresholdMap.get(gasInfo.getType());
                if (threshold != null) {
                    gasInfo.setThreshold(threshold);
                }
            }
            updatePopupDeviceInfo(gasInfoList);
            //push2Cloud(gasInfoList);
            if (!hasFetchedAlarm) {
                hasFetchedAlarm = true;
                sendReadAlarmCmd();
            }
        }

        @Override
        public void onAlarmData(int address, List<GasInfo> gasInfoList) {
            LogUtils.d(TAG, "报警阈值: addr=" + address + " count=" + gasInfoList.size());
            for (GasInfo gasInfo : gasInfoList) {
                thresholdMap.put(gasInfo.getType(), gasInfo.getValue());
                LogUtils.d(TAG, "阈值: type=" + gasInfo.getType() + " " + gasInfo.getEnName() + "=" + gasInfo.getDisplayValue());
            }
        }

        @Override
        public void onAck(int address, boolean success) {
            LogUtils.d(TAG, "应答: addr=" + address + " success=" + success);
        }
    });

    private final byte[] cmdRealtime = ProtocolParser.buildReadRealtimeCmd(DefaultConstant.slaveAddress);
    private final Runnable cmdRunnable = new Runnable() {
        @Override
        public void run() {
            if (pipeline != null) {
                pipeline.writeData(cmdRealtime);
            }
            postDelayed(this, 1000);
        }
    };

    private void sendReadAlarmCmd() {
        if (pipeline != null) {
            byte[] cmd = ProtocolParser.buildReadAlarmCmd(DefaultConstant.slaveAddress);
            pipeline.writeData(cmd);
            LogUtils.d(TAG, "发送报警阈值请求:" + HexUtils.bytesToHex(cmd));
        }
    }

    private void initTcpClient() {
        removeCallbacks(cmdRunnable);
        postDelayed(cmdRunnable, 1000);
        RCSDKManager.INSTANCE.initSDK(this, new SDKManagerCallBack() {
            @Override
            public void onRcConnected() {
                LogUtils.d(TAG, "onRcConnected");
                createPipeline();
            }

            @Override
            public void onRcConnectFail(@Nullable SkyException e) {
                LogUtils.e(TAG, "onRcConnectFail:" + (e == null ? "" : e.getMessage()));
            }

            @Override
            public void onRcDisconnect() {
                LogUtils.d(TAG, "onRcDisconnect");
            }
        });
        RCSDKManager.INSTANCE.setMainThreadCallBack(true);
        RCSDKManager.INSTANCE.connectToRC();
    }

    private void createPipeline() {
        pipeline = PipelineManager.INSTANCE.createTCPPipeline("192.168.1.159", 8899, true, true);
        if (pipeline != null) {
            pipeline.setOnCommListener(new CommListener() {
                @Override
                public void onConnectSuccess() {
                    LogUtils.d(TAG, "onConnectSuccess");
                }

                @Override
                public void onConnectFail(SkyException e) {
                    LogUtils.e(TAG, "onConnectFail:" + (e == null ? "" : e.getMessage()));
                }

                @Override
                public void onDisconnect() {
                    LogUtils.d(TAG, "onDisconnect");
                    hasFetchedAlarm = false;
                    protocolParser.reset();
                }

                @Override
                public void onReadData(byte[] bytes) {
                    LogUtils.d(TAG, "接收到的数据:" + HexUtils.bytesToHex(bytes));
                    lastDataReceivedTime = System.currentTimeMillis();
                    if (!isDeviceOnline) {
                        isDeviceOnline = true;
                        changeDeviceStatus(true);
                        removeCallbacks(dataCheckRunnable);
                        postDelayed(dataCheckRunnable, DATA_CHECK_INTERVAL);
                    }
                    protocolParser.feed(bytes);
                }
            });
            PipelineManager.INSTANCE.connectPipeline(pipeline);
        }
    }
    //endregion

    //region 更新弹窗数据
    private void updatePopupDeviceInfo(List<GasInfo> gasBeanList) {
        if (gasBeanList == null || gasBeanList.isEmpty()) {
            return;
        }
        StringBuilder deviceInfo = new StringBuilder();
        for (GasInfo gasInfo : gasBeanList) {
            if (gasInfo == null) {
                continue;
            }
            // 检查是否超过预警值
            boolean isWarn = gasInfo.isWarn();

            // 构建设备信息文本，根据预警状态设置颜色
            String deviceText = "• " + gasInfo.getName() + " " + gasInfo.getDisplayValue() + " " + gasInfo.getUnit();

            if (isWarn) {
                // 预警数据显示红色
                deviceInfo.append("<font color='#FF0000'>").append(deviceText).append("</font><br>");
            } else {
                // 正常数据显示白色
                deviceInfo.append("<font color='#FFFFFF'>").append(deviceText).append("</font><br>");
            }
        }

        // 使用HTML格式设置文本
        String htmlText = deviceInfo.toString();
        if (easyWindowGAS != null) {
            easyWindowGAS.setTextByTextView(R.id.tvDeviceInfo, android.text.Html.fromHtml(htmlText, android.text.Html.FROM_HTML_MODE_LEGACY));
        }
    }

    private void changeDeviceStatus(boolean online) {
        if (easyWindowGAS != null && easyWindowGAS.isShowing() && !isFinishing() && !isDestroyed()) {
            easyWindowGAS.setTextByTextView(R.id.tvDeviceTitle, getString(online ? R.string.device_online : R.string.device_offline));
        }
    }
    //endregion

    /*//region MQTT 云平台
    private void initMqtt() {
        MQTTManager.getInstance().init(this);
        Random random = new Random();
        Runnable test = new Runnable() {
            @Override
            public void run() {
                List<GasInfo> gasBeanList = new ArrayList<>();
                GasInfo gasInfo = new GasInfo();
                gasInfo.setName("CO");
                gasInfo.setUnit("ppm");
                gasInfo.setValue(random.nextInt(100));
                gasInfo.setEnName("CO");
                gasBeanList.add(gasInfo);

                gasInfo = new GasInfo();
                gasInfo.setName("O2");
                gasInfo.setUnit("%");
                gasInfo.setValue(random.nextInt(30));
                gasInfo.setEnName("O2");
                gasBeanList.add(gasInfo);

                gasInfo = new GasInfo();
                gasInfo.setName("CH4");
                gasInfo.setUnit("ppm");
                gasInfo.setValue(random.nextInt(100) / 10.0f);
                gasInfo.setEnName("CH4");
                gasBeanList.add(gasInfo);

                push2Cloud(gasBeanList);
                updatePopupDeviceInfo(gasBeanList);
                postDelayed(this, 2000);
            }
        };
        post(test);
    }

    private void push2Cloud(List<GasInfo> gasBeanList) {
        MQTTManager.getInstance().push2Cloud(gasBeanList);
    }
    //endregion*/

    //region生命周期
    @Override
    protected void onDestroy() {
        removeCallbacks(dataCheckRunnable);
        removeCallbacks(cmdRunnable);
        RCSDKManager.INSTANCE.disconnectRC();
        //断开通讯管道
        if (pipeline != null) {
            PipelineManager.INSTANCE.disconnectPipeline(pipeline);
        }
        EasyWindowManager.cancelAllWindow();
        //MQTTManager.getInstance().release();
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    //endregion
}