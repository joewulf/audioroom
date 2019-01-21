package com.netease.audioroom.demo.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.netease.audioroom.demo.R;
import com.netease.audioroom.demo.adapter.ChatRoomListAdapter;
import com.netease.audioroom.demo.base.BaseActivity;
import com.netease.audioroom.demo.base.BaseAdapter;
import com.netease.audioroom.demo.cache.DemoCache;
import com.netease.audioroom.demo.http.ChatRoomHttpClient;
import com.netease.audioroom.demo.model.AccountInfo;
import com.netease.audioroom.demo.model.DemoRoomInfo;
import com.netease.audioroom.demo.util.Network;
import com.netease.audioroom.demo.util.ScreenUtil;
import com.netease.audioroom.demo.util.ToastHelper;
import com.netease.audioroom.demo.widget.HeadImageView;
import com.netease.audioroom.demo.widget.VerticalItemDecoration;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.EmptyCallback;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.ErrorCallback;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.LoadingCallback;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.NetErrCallback;
import com.netease.audioroom.demo.widget.unitepage.loadsir.core.Transport;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.StatusCode;
import com.netease.nimlib.sdk.auth.AuthService;
import com.netease.nimlib.sdk.auth.LoginInfo;

import java.util.ArrayList;

public class MainActivity extends BaseActivity implements BaseAdapter.ItemClickListener<DemoRoomInfo> {


    private static final String TAG = "MainActivity";
    private HeadImageView ivAvatar;
    private TextView tvNick;
    private ChatRoomListAdapter chatRoomListAdapter;

    private StatusCode loginStatus = StatusCode.UNLOGIN;


    @Override
    protected int getContentViewID() {
        return R.layout.activity_main;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        loadService.showSuccess();
    }

    @Override
    protected void initView() {
        ivAvatar = findViewById(R.id.iv_self_avatar);
        tvNick = findViewById(R.id.tv_self_nick);
        RecyclerView rcyChatList = findViewById(R.id.rcy_chat_room_list);
        rcyChatList.setLayoutManager(new LinearLayoutManager(this));
        chatRoomListAdapter = new ChatRoomListAdapter(null, this);
        // 每个item 16dp 的间隔
        rcyChatList.addItemDecoration(new VerticalItemDecoration(Color.TRANSPARENT, ScreenUtil.dip2px(this, 16)));
        rcyChatList.setAdapter(chatRoomListAdapter);
        chatRoomListAdapter.setItemClickListener(this);
        setNetworkReconnection(new NetworkReconnection() {
            @Override
            public void onNetworkReconnection() {
                loadService.showCallback(LoadingCallback.class);
                onNetWork();
            }

            @Override
            public void onNetworkInterrupt() {
                loadService.showCallback(NetErrCallback.class);
                loadService.setCallBack(NetErrCallback.class, new Transport() {
                    @Override
                    public void order(Context context, View view) {
                        view.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (new Network().isConnected()) {
                                    onNetWork();
                                }
                            }
                        });
                    }
                });
            }
        });

    }


    @Override
    protected void onNetWork() {
        super.onNetWork();
        tryLogin();
        fetchChatRoomList();
    }


    private void tryLogin() {
        final AccountInfo accountInfo = DemoCache.getAccountInfo();
        if (accountInfo == null) {
            fetchLoginAccount(null);
            return;
        }
        LoginInfo loginInfo = new LoginInfo(accountInfo.account, accountInfo.token);

        NIMClient.getService(AuthService.class).login(loginInfo).setCallback(new RequestCallback() {
            @Override
            public void onSuccess(Object o) {
                afterLogin(accountInfo);
            }

            @Override
            public void onFailed(int i) {
                loadService.showCallback(ErrorCallback.class);
                fetchLoginAccount(accountInfo.account);
            }

            @Override
            public void onException(Throwable throwable) {
                loadService.showCallback(ErrorCallback.class);
                fetchLoginAccount(accountInfo.account);
            }
        });

    }

    private void fetchLoginAccount(String preAccount) {
        ChatRoomHttpClient.getInstance().fetchAccount(preAccount, new ChatRoomHttpClient.ChatRoomHttpCallback<AccountInfo>() {
            @Override
            public void onSuccess(AccountInfo accountInfo) {

                login(accountInfo);
            }

            @Override
            public void onFailed(int code, String errorMsg) {
                ToastHelper.showToast("获取登录帐号失败 ， code = " + code);
            }
        });
    }

    private void login(final AccountInfo accountInfo) {
        LoginInfo loginInfo = new LoginInfo(accountInfo.account, accountInfo.token);
        NIMClient.getService(AuthService.class).login(loginInfo).setCallback(new RequestCallback() {
            @Override
            public void onSuccess(Object o) {
                loadService.showCallback(EmptyCallback.class);
                afterLogin(accountInfo);
            }

            @Override
            public void onFailed(int i) {
                loadService.showCallback(ErrorCallback.class);
                ToastHelper.showToast("SDK登录失败 , code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                loadService.showCallback(ErrorCallback.class);
                ToastHelper.showToast("SDK登录异常 , e = " + throwable);
            }
        });
    }

    private void afterLogin(AccountInfo accountInfo) {
//        ToastHelper.showToast("登录成功");
        DemoCache.setAccountId(accountInfo.account);
        DemoCache.saveAccountInfo(accountInfo);
        ivAvatar.loadAvatar(accountInfo.avatar);
        tvNick.setText(accountInfo.nick);
        Log.i(TAG, "after login  , account = " + accountInfo.account);

    }


    private void fetchChatRoomList() {
        ChatRoomHttpClient.getInstance().fetchChatRoomList(0, 20
                , new ChatRoomHttpClient.ChatRoomHttpCallback<ArrayList<DemoRoomInfo>>() {
                    @Override
                    public void onSuccess(ArrayList<DemoRoomInfo> roomList) {
                        if (roomList.size() > 0) {
                            chatRoomListAdapter.clearAll();
                            loadService.showSuccess();
                            chatRoomListAdapter.appendItems(roomList);
                        } else {
                            loadService.showCallback(EmptyCallback.class);
                        }
                    }

                    @Override
                    public void onFailed(int code, String errorMsg) {
                        loadService.showCallback(ErrorCallback.class);
                    }
                });
    }


    @Override
    public void onItemClick(DemoRoomInfo model, int position) {
        if (loginStatus != StatusCode.LOGINED) {
            ToastHelper.showToast("登录失败，请杀掉APP重新登录");
            return;
        }
        //当前帐号创建的房间
        if (TextUtils.equals(DemoCache.getAccountId(), model.getCreator()) && model != null) {
            AudioLiveActivity.start(mContext, model);
        } else {
            AudienceActivity.start(mContext, model);
        }
    }



    //重写返回键事件
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            home.addCategory(Intent.CATEGORY_HOME);
            startActivity(home);
            return true;
        }
        return super.onKeyDown(keyCode, event);

    }

    @Override
    protected void onLoginEvent(StatusCode statusCode) {
        loginStatus = statusCode;
    }
}
