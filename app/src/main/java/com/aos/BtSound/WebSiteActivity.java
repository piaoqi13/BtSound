package com.aos.BtSound;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import com.umeng.analytics.MobclickAgent;

/**
 * Created by CollinWang on 2016-05-07.
 */
public class WebSiteActivity extends Activity implements View.OnClickListener {
    private final String mPageName = "WebSiteActivity";
    private WebView mWvWebSite = null;

    private boolean isLoadImageAuto = true;
    private boolean isJavaScriptEnabled = true;
    private boolean isJavaScriptCanOpenWindowAuto = false;
    private boolean isRememberPassword = true;
    private boolean isSaveFormData = true;
    private boolean isLoadPageInOverviewMode = true;
    private boolean isUseWideViewPort = true;
    private boolean isLightTouch = false;
    private int minimumFontSize = 8;
    private int minimumLogicalFontSize = 8;
    private int defaultFontSize = 16;
    private int defaultFixedFontSize = 13;

    private ImageView mIvBack = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.web_site);
        super.onCreate(savedInstanceState);
        initView();
        initData();
        initListener();
    }

    public void initView() {
        mWvWebSite = (WebView) findViewById(R.id.wv_website);
        mIvBack = (ImageView) findViewById(R.id.iv_back);
        WebSettings webSettings = mWvWebSite.getSettings();
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSettings.setUserAgentString(null);
        webSettings.setUseWideViewPort(isUseWideViewPort);
        webSettings.setLoadsImagesAutomatically(isLoadImageAuto);
        webSettings.setJavaScriptEnabled(isJavaScriptEnabled);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(isJavaScriptCanOpenWindowAuto);
        webSettings.setMinimumFontSize(minimumFontSize);
        webSettings.setMinimumLogicalFontSize(minimumLogicalFontSize);
        webSettings.setDefaultFontSize(defaultFontSize);
        webSettings.setDefaultFixedFontSize(defaultFixedFontSize);
        webSettings.setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
        webSettings.setLightTouchEnabled(isLightTouch);
        webSettings.setSaveFormData(isSaveFormData);
        webSettings.setSavePassword(isRememberPassword);
        webSettings.setLoadWithOverviewMode(isLoadPageInOverviewMode);
        webSettings.setNeedInitialFocus(false);
        webSettings.setSupportMultipleWindows(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setSupportZoom(true);
    }

    public void initData() {
        mWvWebSite.loadUrl("http://www.aossh.com");
    }

    public void initListener() {
        mIvBack.setOnClickListener(this);
        mWvWebSite.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(mPageName);
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(mPageName);
        MobclickAgent.onResume(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.iv_back:
                finish();
                break;
        }
    }
}
