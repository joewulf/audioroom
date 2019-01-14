package com.netease.audioroom.demo.app;

import android.app.Application;

import com.netease.audioroom.demo.custom.CustomAttachParser;
import com.netease.audioroom.demo.cache.DemoCache;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.msg.MsgService;
import com.netease.nimlib.sdk.util.NIMUtil;

public class NimApplication extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
        DemoCache.init(this);
        NIMClient.init(this, null, null);

        if (NIMUtil.isMainProcess(this)) {
            // 注册自定义消息附件解析器
            NIMClient.getService(MsgService.class).registerCustomAttachmentParser(new CustomAttachParser());
        }
    }


}
