package com.topsky.gasdatapop.ui;

import android.content.Intent;
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
import com.topsky.gasdatapop.R;
import com.topsky.gasdatapop.base.BaseActivity;
import com.topsky.gasdatapop.bean.RadiationData;
import com.topsky.gasdatapop.constant.DefaultConstant;
import com.topsky.gasdatapop.constant.SpConstant;
import com.topsky.gasdatapop.databinding.ActivityMainBinding;
import com.topsky.gasdatapop.modbus.ModbusRtuManager;
import com.topsky.gasdatapop.tcp.TcpClient;
import com.topsky.gasdatapop.tcp.TcpListener;
import com.topsky.gasdatapop.utils.HexUtils;

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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        checkPermission();
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
        initTcpClient();
    }

    //region 通信
    private boolean isDeviceOnline = false;
    private TcpClient tcpClient;
    private ModbusRtuManager modbusRtuManager;
    private static final String TCP_HOST = "192.168.144.7";
    private static final int TCP_PORT = 8234;

    private void initTcpClient() {
        tcpClient = new TcpClient(TCP_HOST, TCP_PORT);
        modbusRtuManager = new ModbusRtuManager(tcpClient);
        modbusRtuManager.setListener(this::updatePopupDeviceInfo);

        tcpClient.setListener(new TcpListener() {
            @Override
            public void onConnected() {
                modbusRtuManager.startPolling();
            }

            @Override
            public void onReConnect(String msg) {
            }

            @Override
            public void onDisconnected() {
                isDeviceOnline = false;
                modbusRtuManager.stopPolling();
                changeDeviceStatus(false);
            }

            @Override
            public void onReceived(byte[] data) {
                LogUtils.d(TAG, "Received: " + HexUtils.bytesToHex(data));
                if (!isDeviceOnline) {
                    isDeviceOnline = true;
                    changeDeviceStatus(true);
                }
                modbusRtuManager.onRawDataReceived(data);
            }

            @Override
            public void onError(Exception e) {
                isDeviceOnline = false;
                changeDeviceStatus(false);
            }
        });
        tcpClient.connect();
    }

    private void updatePopupDeviceInfo(RadiationData data) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (data == null) {
            return;
        }
        StringBuilder deviceInfo = new StringBuilder();

        String deviceText = "• 实时剂量率 " + data.getDoseRate() + " " + data.getDoseRateUnit();
        deviceInfo.append("<font color='#FFFFFF'>").append(deviceText).append("</font><br>");

        deviceText = "• 累计剂量 " + data.getCumulativeDose() + " " + data.getCumulativeDoseUnit();
        deviceInfo.append("<font color='#FFFFFF'>").append(deviceText).append("</font><br>");

        deviceText = "• 环境温度 " + data.getTemperature() + " " + data.getTemperatureUnit();
        deviceInfo.append("<font color='#FFFFFF'>").append(deviceText).append("</font><br>");

        deviceText = "• 锂电池电压 " + data.getBatteryVoltage() + " V";
        deviceInfo.append("<font color='#FFFFFF'>").append(deviceText).append("</font><br>");

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

    //region 生命周期
    @Override
    protected void onDestroy() {
        EasyWindowManager.cancelAllWindow();
        if (modbusRtuManager != null) {
            modbusRtuManager.release();
        }
        if (tcpClient != null) {
            tcpClient.release();
        }
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    //endregion
}