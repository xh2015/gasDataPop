package com.topsky.gasdatapop.base;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import com.blankj.utilcode.util.KeyboardUtils;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<VB extends ViewBinding> extends AppCompatActivity implements HandlerAction {
    protected VB binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = initViewBinding(LayoutInflater.from(this));
        setContentView(binding.getRoot());
        initBar();
        init();
    }

    protected abstract VB initViewBinding(LayoutInflater inflater);

    protected void initBar() {
        ImmersionBar.with(this)
                .hideBar(BarHide.FLAG_HIDE_STATUS_BAR)
                .statusBarDarkFont(darkFontBar())
                .init();
    }

    protected boolean darkFontBar() {
        return true;
    }

    protected abstract void init();

    public void start(Class clazz) {
        Intent intent = new Intent(this, clazz);
        this.startActivity(intent);
    }

    protected void starAndFinish(Class clazz) {
        Intent intent = new Intent(this, clazz);
        this.startActivity(intent);
        this.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeCallbacks();
    }

    @Override
    public void finish() {
        super.finish();
        // 隐藏软键，避免内存泄漏
        KeyboardUtils.hideSoftInput(this);
    }

    /**
     * 如果当前的 Activity（singleTop 启动模式） 被复用时会回调
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 设置为当前的 Intent，避免 Activity 被杀死后重启 Intent 还是最原先的那个
        setIntent(intent);
    }
}
