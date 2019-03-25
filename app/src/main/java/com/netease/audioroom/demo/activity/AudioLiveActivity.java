package com.netease.audioroom.demo.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.netease.audioroom.demo.R;
import com.netease.audioroom.demo.base.BaseAudioActivity;
import com.netease.audioroom.demo.base.LoginManager;
import com.netease.audioroom.demo.base.action.INetworkReconnection;
import com.netease.audioroom.demo.cache.DemoCache;
import com.netease.audioroom.demo.cache.RoomMemberCache;
import com.netease.audioroom.demo.custom.P2PNotificationHelper;
import com.netease.audioroom.demo.dialog.BottomMenuDialog;
import com.netease.audioroom.demo.dialog.RequestLinkDialog;
import com.netease.audioroom.demo.dialog.TopTipsDialog;
import com.netease.audioroom.demo.http.ChatRoomHttpClient;
import com.netease.audioroom.demo.model.AccountInfo;
import com.netease.audioroom.demo.model.AudioMixingInfo;
import com.netease.audioroom.demo.model.DemoRoomInfo;
import com.netease.audioroom.demo.model.QueueInfo;
import com.netease.audioroom.demo.model.QueueMember;
import com.netease.audioroom.demo.util.CommonUtil;
import com.netease.audioroom.demo.util.JsonUtil;
import com.netease.audioroom.demo.util.ToastHelper;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.ErrorCallback;
import com.netease.audioroom.demo.widget.unitepage.loadsir.callback.LoadingCallback;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.avchat.AVChatCallback;
import com.netease.nimlib.sdk.avchat.AVChatManager;
import com.netease.nimlib.sdk.avchat.constant.AVChatUserRole;
import com.netease.nimlib.sdk.avchat.model.AVChatChannelInfo;
import com.netease.nimlib.sdk.avchat.model.AVChatParameters;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomQueueChangeAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomRoomMemberInAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomTempMuteAddAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomTempMuteRemoveAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomUpdateInfo;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData;
import com.netease.nimlib.sdk.msg.constant.ChatRoomQueueChangeType;
import com.netease.nimlib.sdk.msg.model.CustomNotification;
import com.netease.nimlib.sdk.util.Entry;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.netease.audioroom.demo.dialog.BottomMenuDialog.BOTTOMMENUS;

/**
 * 主播页
 */
public class AudioLiveActivity extends BaseAudioActivity implements LoginManager.IAudioLive, View.OnClickListener {
    BottomMenuDialog bottomMenuDialog;
    EnterChatRoomResultData resultData;

    public static void start(Context context, DemoRoomInfo demoRoomInfo) {
        Intent intent = new Intent(context, AudioLiveActivity.class);
        intent.putExtra(BaseAudioActivity.ROOM_INFO_KEY, demoRoomInfo);
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(R.anim.in_from_right, R.anim.out_from_left);
        }
    }

    private String[] musicPathArray;
    private int currentPlayIndex;
    private int inviteIndex = -1;//抱麦位置
    TopTipsDialog topTipsDialog;

    //聊天室队列元素
    private HashMap<String, QueueInfo> queueMap = new HashMap<>();

    TextView semicircleView;
    ArrayList<QueueInfo> requestMemberList;//申请麦位列表

    private TextView tvMusicPlayHint;
    private ImageView ivPauseOrPlay;
    private ImageView ivNext;
    private RequestLinkDialog requestLinkDialog;
    protected AudioMixingInfo mixingInfo;

    @Override
    protected int getContentViewID() {
        return R.layout.activity_live;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterChatRoom(roomInfo.getRoomId());
        createAudioRoom(roomInfo.getRoomId());
        checkFile();

    }

    @Override
    public void enterChatRoom(String roomId) {
        //加入聊天室
        AccountInfo accountInfo = DemoCache.getAccountInfo();
        EnterChatRoomData roomData = new EnterChatRoomData(roomId);
        roomData.setAvatar(accountInfo.avatar);
        roomData.setNick(accountInfo.nick);
        chatRoomService.enterChatRoom(roomData).setCallback(new RequestCallback<EnterChatRoomResultData>() {
            @Override
            public void onSuccess(EnterChatRoomResultData resultData) {
                loadService.showSuccess();
                enterRoomSuccess(resultData);
                updateRoomInfo(true);
            }

            @Override
            public void onFailed(int i) {
                loadService.showCallback(ErrorCallback.class);
                exitRoom();
            }

            @Override
            public void onException(Throwable throwable) {
                loadService.showCallback(ErrorCallback.class);
                finish();
            }
        });
    }

    private void createAudioRoom(String roomId) {
        AVChatManager.getInstance().createRoom(roomId, "", new AVChatCallback<AVChatChannelInfo>() {
            @Override
            public void onSuccess(AVChatChannelInfo avChatChannelInfo) {
                ToastHelper.showToast("创建音频房间成功");
                Log.e(TAG, "创建音频房间成功 ， room id = " + roomId);
                joinAudioRoom();
            }

            @Override
            public void onFailed(int code) {
                ToastHelper.showToast("创建音频房间失败， code = " + code);
                Log.e(TAG, "创建音频房间失败， code = " + code + " , room id = " + roomId);
            }

            @Override
            public void onException(Throwable exception) {
                ToastHelper.showToast("创建音频房间失败 , e = " + exception.getMessage());
                Log.e(TAG, "创建音频房间失败 , e = " + exception.getMessage());
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
        setNetworkReconnection(new INetworkReconnection() {
            @Override
            public void onNetworkReconnection() {
                if (topTipsDialog != null) {
                    topTipsDialog.dismiss();
                }
                LoginManager loginManager = LoginManager.getInstance();
                loginManager.tryLogin();
                loginManager.setCallback(new LoginManager.Callback() {
                    @Override
                    public void onSuccess(AccountInfo accountInfo) {
                        if (resultData != null) {
                            enterChatRoom(roomInfo.getRoomId());
                            joinAudioRoom();
                        } else {
                            enterRoomSuccess(resultData);
                        }
                    }

                    @Override
                    public void onFailed(int code, String errorMsg) {
                        loadService.showCallback(ErrorCallback.class);
                    }
                });
            }

            @Override
            public void onNetworkInterrupt() {
                Bundle bundle = new Bundle();
                TopTipsDialog.Style style = topTipsDialog.new Style(
                        "网络断开",
                        0,
                        R.drawable.neterrricon,
                        0);
                bundle.putParcelable(topTipsDialog.TAG, style);
                topTipsDialog.setArguments(bundle);
                if (!topTipsDialog.isVisible()) {
                    topTipsDialog.show(getSupportFragmentManager(), topTipsDialog.TAG);
                    topTipsDialog.setClickListener(() -> {
                    });
                }

            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            //抱麦
            loadService.showCallback(LoadingCallback.class);
            QueueMember queueMember = (QueueMember) data.getSerializableExtra(MemberActivity.MEMBERACTIVITY);
            //判断当前用户是否离开
            RoomMemberCache.getInstance().fetchMembers(roomInfo.getRoomId(), 0, 100000, new RequestCallback<List<ChatRoomMember>>() {
                @Override
                public void onSuccess(List<ChatRoomMember> param) {
                    ArrayList<QueueMember> allQueueMemberArrayList = new ArrayList<>();
                    for (ChatRoomMember chatRoomMember : param) {
                        allQueueMemberArrayList.add(new QueueMember(chatRoomMember.getAccount(), chatRoomMember.getNick(), chatRoomMember.getAvatar()));
                    }
                    boolean isContains = false;
                    for (QueueMember member : allQueueMemberArrayList) {
                        if (member != null && member.getAccount() != null && member.getAccount().equals(queueMember.getAccount())) {
                            isContains = true;
                            break;
                        }
                    }
                    if (isContains) {//用户没有离开房间
                        chatRoomService.fetchQueue(roomInfo.getRoomId()).setCallback(new RequestCallback<List<Entry<String, String>>>() {
                            @Override
                            public void onSuccess(List<Entry<String, String>> param) {
                                boolean isInQueue = false;
                                ArrayList<QueueInfo> allQueueInfoArrayList = getQueueList(param);
                                for (QueueInfo q : allQueueInfoArrayList) {
                                    if (q.getQueueMember() != null && q.getQueueMember().getAccount().equals(queueMember.getAccount())) {//存在于列表中
                                        if (QueueInfo.hasOccupancy(q) || q.getStatus() == QueueInfo.STATUS_LOAD) {
                                            ToastHelper.showToast("操作失败:当前用户已在麦位上");
                                            isInQueue = true;
                                            break;
                                        }
                                    }
                                }
                                if (!isInQueue) {
                                    if (allQueueInfoArrayList.get(inviteIndex).getStatus() == QueueInfo.STATUS_FORBID) {
                                        invitedLink(new QueueInfo(inviteIndex, queueMember, QueueInfo.STATUS_FORBID, QueueInfo.Reason.inviteByHost));
                                    } else {
                                        invitedLink(new QueueInfo(inviteIndex, queueMember, QueueInfo.STATUS_NORMAL, QueueInfo.Reason.inviteByHost));
                                    }

                                }
                            }

                            @Override
                            public void onFailed(int code) {

                            }

                            @Override
                            public void onException(Throwable exception) {

                            }
                        });

                    } else {
                        ToastHelper.showToast("操作失败:用户离开房间");

                    }

                }

                @Override
                public void onFailed(int code) {

                }

                @Override
                public void onException(Throwable exception) {

                }
            });
        }
    }

    @Override
    protected void setupBaseView() {
        mixingInfo = new AudioMixingInfo();
        topTipsDialog = new TopTipsDialog();
        semicircleView = findViewById(R.id.semicircleView);
        tvMusicPlayHint = findViewById(R.id.tv_music_play_hint);
        ivPauseOrPlay = findViewById(R.id.iv_pause_or_play);
        ivNext = findViewById(R.id.iv_next);
        ivCancelLink.setVisibility(View.GONE);
        ivMuteOtherText.setOnClickListener(this);
        ivSelfAudioSwitch.setOnClickListener(this);
        ivRoomAudioSwitch.setOnClickListener(this);
        ivExistRoom.setOnClickListener(this);
        semicircleView.setOnClickListener(this);
        ivPauseOrPlay.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        ivPauseOrPlay.setOnClickListener(this);
        ivNext.setOnClickListener(this);
        requestMemberList = new ArrayList<>();
        semicircleView.setVisibility(View.INVISIBLE);
        semicircleView.setClickable(true);
        updateMusicPlayHint();
    }


    @Override
    protected void onQueueItemClick(QueueInfo queueInfo, int position) {
        Bundle bundle = new Bundle();
        bottomMenuDialog = new BottomMenuDialog();
        ArrayList<String> mune = new ArrayList<>();
        //当前麦位有人了
        switch (queueInfo.getStatus()) {
            case QueueInfo.STATUS_INIT:
                mune.add("将成员抱上麦位");
                mune.add("屏蔽麦位");
                mune.add("关闭麦位");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将成员抱上麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将成员抱上麦位");
                            break;
                        case "屏蔽麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "屏蔽麦位");
                            break;
                        case "关闭麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "关闭麦位");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;
                    }
                });
                break;
            case QueueInfo.STATUS_LOAD:
                ToastHelper.showToast("正在申请");
                break;
            case QueueInfo.STATUS_NORMAL:
                mune.add("将TA踢下麦位");
                mune.add("屏蔽麦位");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将TA踢下麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将TA踢下麦位");
                            break;
                        case "屏蔽麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "屏蔽麦位");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;
                    }
                });
                break;
            case QueueInfo.STATUS_CLOSE:
                mune.add("打开麦位");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "打开麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "打开麦位");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;
                    }
                });
                break;
            case QueueInfo.STATUS_FORBID:
                mune.add("将成员抱上麦位");
                mune.add("解除语音屏蔽");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将成员抱上麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将成员抱上麦位");
                            break;
                        case "解除语音屏蔽":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "解除语音屏蔽");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;

                    }
                });
                break;
            case QueueInfo.STATUS_BE_MUTED_AUDIO:
                mune.add("将TA踢下麦位");
                mune.add("解除语音屏蔽");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将TA踢下麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将TA踢下麦位");
                            break;
                        case "解除语音屏蔽":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "解除语音屏蔽");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;

                    }
                });
                break;
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO:
                mune.add("将TA踢下麦位");
                mune.add("屏蔽麦位");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将TA踢下麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将TA踢下麦位");
                            break;
                        case "屏蔽麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "屏蔽麦位");
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;
                    }
                });
                break;
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED:
                mune.add("将TA踢下麦位");
                mune.add("解除语音屏蔽");
                mune.add("取消");
                bundle.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "将TA踢下麦位":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "将TA踢下麦位");
                            break;
                        case "解除语音屏蔽":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "解除语音屏蔽");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, queueInfo, "取消");
                            break;
                    }
                });
                break;

        }
        if (queueInfo.getStatus() != QueueInfo.STATUS_LOAD) {
            bottomMenuDialog.show(getSupportFragmentManager(), bottomMenuDialog.TAG);
        }
    }

    @Override
    protected boolean onQueueItemLongClick(QueueInfo model, int position) {
        return false;
    }

    @Override
    protected void receiveNotification(CustomNotification customNotification) {
        String content = customNotification.getContent();
        if (TextUtils.isEmpty(content)) {
            return;
        }
        JSONObject jsonObject = JsonUtil.parse(content);
        if (jsonObject == null) {
            return;
        }
        int command = jsonObject.optInt(P2PNotificationHelper.COMMAND, 0);
        int index;
        QueueInfo queueInfo;
        String nick;
        String avatar;
        QueueMember queueMember;
        switch (command) {
            case P2PNotificationHelper.REQUEST_LINK://请求连麦
                index = jsonObject.optInt(P2PNotificationHelper.INDEX);
                nick = jsonObject.optString(P2PNotificationHelper.NICK);
                avatar = jsonObject.optString(P2PNotificationHelper.AVATAR);
                queueMember = new QueueMember(customNotification.getFromAccount(), nick, avatar);
                queueInfo = queueMap.get(QueueInfo.getKeyByIndex(index));
                if (queueInfo != null) {
                    if (queueInfo.getStatus() == QueueInfo.STATUS_CLOSE) {
                        return;
                    }
                    queueInfo.setQueueMember(queueMember);
                    if (queueInfo.getStatus() == QueueInfo.STATUS_FORBID) {
                        queueInfo.setReason(QueueInfo.Reason.applyInMute);
                    } else {
                        queueInfo.setReason(QueueInfo.Reason.init);
                    }
                    queueInfo.setStatus(QueueInfo.STATUS_LOAD);
                } else {
                    queueInfo = new QueueInfo(index, queueMember, QueueInfo.STATUS_LOAD, QueueInfo.Reason.init);
                }
                linkRequest(queueInfo);
                break;

            case P2PNotificationHelper.CANCEL_REQUEST_LINK://取消请求
                index = jsonObject.optInt(P2PNotificationHelper.INDEX);
                queueInfo = queueMap.get(QueueInfo.getKeyByIndex(index));
                if (queueInfo == null) {
                    queueInfo = new QueueInfo(index, null, QueueInfo.STATUS_INIT, QueueInfo.Reason.init);
                }
                linkRequestCancel(queueInfo);
                break;
            case P2PNotificationHelper.CANCEL_LINK://主动下麦
                index = jsonObject.optInt(P2PNotificationHelper.INDEX, -1);
                queueInfo = queueMap.get(QueueInfo.getKeyByIndex(index));
                if (queueInfo != null) {
                    if (queueInfo.getStatus() == QueueInfo.STATUS_BE_MUTED_AUDIO || queueInfo.getStatus() == QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED) {
                        queueInfo.setStatus(QueueInfo.STATUS_FORBID);
                    } else {
                        queueInfo.setStatus(QueueInfo.STATUS_INIT);
                    }
                    queueInfo.setReason(QueueInfo.Reason.kickedBySelf);
                    chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(), queueInfo.toString());
                }
                break;
        }

    }

    @Override
    protected void exitRoom() {
        loadService.showCallback(LoadingCallback.class);
        //离开聊天室
        AVChatManager.getInstance().disableRtc();
        AVChatManager.getInstance().leaveRoom2(roomInfo.getRoomId(), new AVChatCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                //关闭应用服务器聊天室
                ChatRoomHttpClient.getInstance().closeRoom(DemoCache.getAccountId(),
                        roomInfo.getRoomId(), new ChatRoomHttpClient.ChatRoomHttpCallback() {
                            @Override
                            public void onSuccess(Object o) {
                                loadService.showSuccess();
                                ToastHelper.showToast("房间已解散");
                                if (roomInfo != null) {
                                    RoomMemberCache.getInstance().removeCache(roomInfo.getRoomId());
                                    roomInfo = null;
                                }
                                finish();
                            }

                            @Override
                            public void onFailed(int code, String errorMsg) {
                                ToastHelper.showToast("房间解散失败" + errorMsg);
                            }
                        });

            }

            @Override
            public void onFailed(int code) {
                ToastHelper.showToast("解散失败code：" + code);
            }

            @Override
            public void onException(Throwable exception) {
                ToastHelper.showToast("解散失败code：" + exception.getMessage());
            }
        });

    }

    @Override
    protected void onQueueChange(ChatRoomQueueChangeAttachment queueChange) {
        super.onQueueChange(queueChange);
        ChatRoomQueueChangeType changeType = queueChange.getChatRoomQueueChangeType();
        // 队列被清空
        if (changeType == ChatRoomQueueChangeType.DROP) {
            queueMap.clear();
            return;
        }
        String value = queueChange.getContent();
        //新增元素或更新
        if (changeType == ChatRoomQueueChangeType.OFFER && !TextUtils.isEmpty(value)) {
            QueueInfo queueInfo = new QueueInfo(value);
            if (queueInfo.getIndex() != -1) {
                queueMap.put(queueInfo.getKey(), queueInfo);
            }
        }
    }

    @Override
    protected void enterRoomSuccess(EnterChatRoomResultData resultData) {
        AccountInfo accountInfo = DemoCache.getAccountInfo();
        ivLiverAvatar.loadAvatar(accountInfo.avatar);
        tvLiverNick.setText(accountInfo.nick);
        initQueue(null);
        RoomMemberCache.getInstance().fetchMembers(roomInfo.getRoomId(), 0, 100, null);
    }

    @Override
    public void linkRequest(QueueInfo queueInfo) {
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(), queueInfo.toString());
        requestMemberList.add(queueInfo);
        if (requestMemberList.size() > 0) {
            semicircleView.setVisibility(View.VISIBLE);
            semicircleView.setText(String.valueOf(requestMemberList.size()));
            if (requestLinkDialog != null && requestLinkDialog.isVisible()) {
                requestLinkDialog.updateDate();
            }
        } else {
            semicircleView.setVisibility(View.INVISIBLE);
        }

    }

    @Override
    public void linkRequestCancel(QueueInfo queueInfo) {
        if (queueInfo.getReason() == QueueInfo.Reason.applyInMute) {
            queueInfo.setStatus(QueueInfo.STATUS_FORBID);
        } else {
            queueInfo.setStatus(QueueInfo.STATUS_INIT);
        }
        queueInfo.setReason(QueueInfo.Reason.cancelApplyBySelf);
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(),
                queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                if (requestMemberList.size() != 0) {
                    requestMemberList.remove(queueInfo);
                }
                if (requestMemberList.size() == 0) {
                    semicircleView.setVisibility(View.INVISIBLE);
                    if (requestLinkDialog != null && requestLinkDialog.isVisible()) {
                        requestLinkDialog.dismiss();
                    }
                } else {
                    if (requestLinkDialog != null) {
                        requestLinkDialog.updateDate();
                    }
                }


            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("通过连麦请求失败 ， code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("通过连麦请求异常 ， e = " + throwable);
            }
        });
    }


    @Override
    public void rejectLink(QueueInfo queueInfo) {
        if (queueInfo.getReason() == QueueInfo.Reason.applyInMute) {
            queueInfo.setStatus(QueueInfo.STATUS_FORBID);
        } else {
            queueInfo.setStatus(QueueInfo.STATUS_INIT);
        }
        queueInfo.setReason(QueueInfo.Reason.cancelApplyByHost);
        requestMemberList.remove(queueInfo);
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(), queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ToastHelper.showToast("已拒绝" + queueInfo.getQueueMember().getNick() + "的申请");
                if (requestMemberList.size() == 0) {
                    if (requestLinkDialog != null && requestLinkDialog.isVisible()) {
                        requestLinkDialog.dismiss();
                    }
                } else {
                    if (requestLinkDialog.isVisible()) {
                        requestLinkDialog.updateDate();
                    }

                }
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("拒绝" + queueInfo.getQueueMember().getNick() + "的申请失败code" + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("拒绝" + queueInfo.getQueueMember().getNick() + "的申请失败throwable" + throwable.getMessage());

            }
        });
    }

    @Override
    public void acceptLink(QueueInfo queueInfo) {
        //当前麦位有人了
        if (QueueInfo.hasOccupancy(queueInfo)) {
            rejectLink(queueInfo);
        } else {
            if (queueInfo.getReason() == QueueInfo.Reason.applyInMute) {
                queueInfo.setStatus(QueueInfo.STATUS_BE_MUTED_AUDIO);
            } else {
                queueInfo.setStatus(QueueInfo.STATUS_NORMAL);
            }
            queueInfo.setReason(QueueInfo.Reason.agreeApply);

            chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(),
                    queueInfo.toString()).setCallback(new RequestCallback<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    ToastHelper.showToast("成功通过连麦请求");
                    Iterator<QueueInfo> queueInfoIterator = requestMemberList.iterator();
                    while (queueInfoIterator.hasNext()) {
                        QueueInfo q = queueInfoIterator.next();
                        if (q.getIndex() == queueInfo.getIndex()) {
                            queueInfoIterator.remove();
                        }
                    }
                    if (requestMemberList.size() != 0) {
                        requestLinkDialog.updateDate();
                    } else {
                        requestLinkDialog.dismiss();
                        semicircleView.setVisibility(View.INVISIBLE);
                    }
                }

                @Override
                public void onFailed(int i) {
                    ToastHelper.showToast("通过连麦请求失败 ， code = " + i);
                }

                @Override
                public void onException(Throwable throwable) {
                    ToastHelper.showToast("通过连麦请求异常 ， e = " + throwable);
                }
            });
        }
    }


    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_mute_other_text:
                //禁言
                MuteMemberListActivity.start(mContext, roomInfo);
                break;

            case R.id.iv_close_room_audio_switch:
                boolean close = ivRoomAudioSwitch.isSelected();
                ivRoomAudioSwitch.setSelected(!close);
                muteRoomAudio(!close);
                if (close) {
                    ToastHelper.showToast("已打开“聊天室声音”");
                } else {
                    ToastHelper.showToast("已关闭“聊天室声音”");
                }
                break;
            case R.id.iv_exist_room:
                bottomMenuDialog = new BottomMenuDialog();
                Bundle bundle1 = new Bundle();
                ArrayList<String> mune = new ArrayList<>();
                mune.add("<font color=\"#ff4f4f\">退出并解散房间</color>");
                mune.add("取消");
                bundle1.putStringArrayList(BOTTOMMENUS, mune);
                bottomMenuDialog.setArguments(bundle1);
                bottomMenuDialog.show(getSupportFragmentManager(), bottomMenuDialog.TAG);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "<font color=\"#ff4f4f\">退出并解散房间</color>":
                            bottomButtonAction(bottomMenuDialog, null, "退出并解散房间");
                            break;
                        case "取消":
                            bottomButtonAction(bottomMenuDialog, null, "取消");
                            break;
                    }
                });
                break;
            case R.id.semicircleView:
                requestLinkDialog = new RequestLinkDialog();
                Bundle bundle = new Bundle();
                bundle.putParcelableArrayList(requestLinkDialog.TAG, requestMemberList);
                requestLinkDialog.setArguments(bundle);
                requestLinkDialog.show(getSupportFragmentManager(), requestLinkDialog.TAG);
                requestLinkDialog.setRequestAction(new RequestLinkDialog.IRequestAction() {
                    @Override
                    public void refuse(QueueInfo queueInfo) {
                        //拒绝上麦
                        rejectLink(queueInfo);
                    }

                    @Override
                    public void agree(QueueInfo queueInfo) {
                        //同意上麦
                        acceptLink(queueInfo);
                    }

                    @Override
                    public void dissmiss() {
                        if (requestMemberList.size() == 0) {
                            semicircleView.setVisibility(View.INVISIBLE);
                        } else {
                            semicircleView.setVisibility(View.VISIBLE);
                            semicircleView.setText(String.valueOf(requestMemberList.size()));
                        }

                    }
                });
                break;
            case R.id.iv_pause_or_play:
                playOrPauseMusic();
                break;
            case R.id.iv_next:
                playNextMusic();
                break;
            case R.id.iv_close_self_audio_switch:
                muteSelfAudio();
                if (AVChatManager.getInstance().isMicrophoneMute()) {
                    ToastHelper.showToast("话筒已关闭");
                    updateRoomInfo(false);

                } else {
                    ToastHelper.showToast("话筒已打开");
                    updateRoomInfo(true);
                }
                ivSelfAudioSwitch.setSelected(AVChatManager.getInstance().isMicrophoneMute());
                break;
        }

    }

    @Override
    protected void playMusicErr() {
        ToastHelper.showToast("伴音发现错误");
        ivPauseOrPlay.setTag(null);
        ivPauseOrPlay.setSelected(false);
        AVChatManager.getInstance().stopAudioMixing();
        updateMusicPlayHint();
    }

    @Override
    protected void playNextMusic() {
        currentPlayIndex = (currentPlayIndex + 1) % musicPathArray.length;
        AVChatManager.getInstance().startAudioMixing(musicPathArray[currentPlayIndex], true, false, 0, 0.3f);
        ivPauseOrPlay.setTag(musicPathArray[currentPlayIndex]);
        ivPauseOrPlay.setSelected(true);
        updateMusicPlayHint();
    }

    protected void playOrPauseMusic() {
        boolean isPlaying = ivPauseOrPlay.isSelected();
        String oldPath = (String) ivPauseOrPlay.getTag();
        // 如果正在播放，暂停
        if (isPlaying) {
            AVChatManager.getInstance().pauseAudioMixing();
        }
        //如果已经暂停了，重新播放
        else if (!TextUtils.isEmpty(oldPath)) {
            AVChatManager.getInstance().resumeAudioMixing();
        }
        //之前没有设置任何音乐在播放或暂停
        else {
            AVChatManager.getInstance().startAudioMixing(musicPathArray[currentPlayIndex], false, false, 0, 0.3f);
            ivPauseOrPlay.setTag(musicPathArray[currentPlayIndex]);

        }
        ivPauseOrPlay.setSelected(!isPlaying);
        updateMusicPlayHint();

    }

    private void updateMusicPlayHint() {
        boolean isPlaying = ivPauseOrPlay.isSelected();
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder("音乐" + (currentPlayIndex + 1));
        stringBuilder.setSpan(new ForegroundColorSpan(Color.parseColor("#ffa410")),
                0, stringBuilder.length(),
                SpannableStringBuilder.SPAN_INCLUSIVE_EXCLUSIVE);
        stringBuilder.append(isPlaying ? "播放中" : "已暂停");
        tvMusicPlayHint.setText(stringBuilder);

    }

    protected void memberMuteRemove(ChatRoomTempMuteRemoveAttachment muteRemove) {
        super.memberMuteRemove(muteRemove);
        RoomMemberCache.getInstance().muteChange(roomInfo.getRoomId(), muteRemove.getTargets(), false);

    }

    protected void memberMuteAdd(ChatRoomTempMuteAddAttachment addMuteMember) {
        super.memberMuteAdd(addMuteMember);
        RoomMemberCache.getInstance().muteChange(roomInfo.getRoomId(), addMuteMember.getTargets(), true);
    }

    protected void memberExit(ChatRoomQueueChangeAttachment memberExit) {
        if (requestMemberList != null && requestMemberList.size() > 0) {
            Iterator<QueueInfo> queueInfoIterator = requestMemberList.iterator();
            while (queueInfoIterator.hasNext()) {
                QueueInfo q = queueInfoIterator.next();
                if (q.getQueueMember() != null && q.getQueueMember().getAccount().equals(memberExit.getOperator())) {
                    queueInfoIterator.remove();
                }
            }
            if (requestMemberList.size() == 0) {
                if (requestLinkDialog != null)
                    requestLinkDialog.dismiss();
                semicircleView.setVisibility(View.INVISIBLE);

            } else {
                if (requestLinkDialog != null)
                    requestLinkDialog.updateDate();
            }

        }

        chatRoomService.fetchQueue(roomInfo.getRoomId()).setCallback(new RequestCallback<List<Entry<String, String>>>() {
            @Override
            public void onSuccess(List<Entry<String, String>> param) {
                ArrayList<QueueInfo> queueInfoArrayList = getQueueList(param);
                for (QueueInfo queueInfo : queueInfoArrayList) {
                    if (queueInfo.getQueueMember() != null
                            && queueInfo.getQueueMember().getAccount().equals(memberExit.getOperator())
                            && (QueueInfo.hasOccupancy(queueInfo) || queueInfo.getStatus() == QueueInfo.STATUS_LOAD)) {
                        if (queueInfo.getStatus() == QueueInfo.STATUS_LOAD) {
                            rejectLink(queueInfo);
                        } else {
                            removeLink(queueInfo);
                        }
                    }
                }
            }

            @Override
            public void onFailed(int code) {

            }

            @Override
            public void onException(Throwable exception) {

            }
        });
        super.memberExit(memberExit);
        RoomMemberCache.getInstance().removeMember(roomInfo.getRoomId(), memberExit.getOperator());

    }

    protected void memberIn(ChatRoomRoomMemberInAttachment memberIn) {
        super.memberIn(memberIn);
        if (TextUtils.equals(memberIn.getOperator(), DemoCache.getAccountId())) {
            return;
        }
        RoomMemberCache.getInstance().fetchMember(roomInfo.getRoomId(), memberIn.getOperator(), null);
    }


    //抱麦操作
    @Override
    public void invitedLink(QueueInfo queueInfo) {
        if (queueInfo.getStatus() == QueueInfo.STATUS_FORBID) {
            queueInfo.setStatus(QueueInfo.STATUS_BE_MUTED_AUDIO);
        } else {
            queueInfo.setStatus(QueueInfo.STATUS_NORMAL);
        }
        queueInfo.setReason(QueueInfo.Reason.inviteByHost);

        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(), queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ToastHelper.showToast("已将" + queueInfo.getQueueMember().getNick() + "抱上麦位" + inviteIndex + 1);
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("通过连麦请求失败 ， code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("通过连麦请求异常 ， e = " + throwable);
            }
        });
    }

    //踢人下麦
    @Override
    public void removeLink(QueueInfo queueInfo) {
        if (queueInfo.getStatus() == QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED
                || queueInfo.getStatus() == QueueInfo.STATUS_BE_MUTED_AUDIO) {
            queueInfo.setStatus(QueueInfo.STATUS_FORBID);
        } else {
            queueInfo.setStatus(QueueInfo.STATUS_INIT);
        }

        queueInfo.setReason(QueueInfo.Reason.kickByHost);
        String Tempname = queueInfo.getQueueMember().getNick();
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(),
                queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ToastHelper.showToast("已将“" + Tempname + "”踢下麦位");
            }

            @Override
            public void onFailed(int i) {
            }

            @Override
            public void onException(Throwable throwable) {

            }
        });

    }

    //有人主动下麦
    @Override
    public void linkCanceled() {

    }

    //
    @Override
    public void mutedText() {

    }

    //
    @Override
    public void muteTextAll() {

    }

    String msg = "", errmsg = "";

    @Override
    public void openAudio(QueueInfo queueInfo) {
        //麦上没人
        switch (queueInfo.getStatus()) {
            case QueueInfo.STATUS_CLOSE:
                int position = queueInfo.getIndex() + 1;
                msg = "“麦位" + position + "”已打开”";
                errmsg = "“麦位" + position + "”打开失败”";
                queueInfo.setStatus(QueueInfo.STATUS_INIT);
                break;

            case QueueInfo.STATUS_FORBID:
                msg = "“该麦位已“解除语音屏蔽”";
                errmsg = "该麦位“解除语音屏蔽”失败";
                queueInfo.setStatus(QueueInfo.STATUS_INIT);
                break;
            case QueueInfo.STATUS_BE_MUTED_AUDIO:
                msg = "“该麦位已“解除语音屏蔽”";
                errmsg = "该麦位“解除语音屏蔽”失败";
                queueInfo.setStatus(QueueInfo.STATUS_NORMAL);
                queueInfo.setReason(QueueInfo.Reason.cancelMuted);
                break;
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED:
                msg = "该麦位已“解除语音屏蔽”";
                errmsg = "该麦位“解除语音屏蔽”失败";
                queueInfo.setStatus(QueueInfo.STATUS_CLOSE_SELF_AUDIO);
                break;
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO:
                queueInfo.setStatus(QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED);
                break;
        }
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(),
                queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ToastHelper.showToast(msg);
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast(errmsg + "code" + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast(errmsg + throwable.getMessage());
            }
        });

    }

    //关闭麦位
    @Override
    public void closeAudio(QueueInfo queueInfo) {
        if (queueInfo.getStatus() == QueueInfo.STATUS_LOAD) {
            requestMemberList.remove(queueInfo.getIndex());
            if (requestMemberList.size() == 0) {
                semicircleView.setVisibility(View.INVISIBLE);
            } else {
                semicircleView.setVisibility(View.VISIBLE);
                semicircleView.setText(String.valueOf(requestMemberList.size()));
            }
        }
        queueInfo.setStatus(QueueInfo.STATUS_CLOSE);
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(),
                queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                int position = queueInfo.getIndex() + 1;
                ToastHelper.showToast("\"麦位" + position + "\"已关闭");
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("通过连麦请求失败 ， code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("通过连麦请求异常 ， e = " + throwable);
            }
        });
    }

    //屏蔽某个麦位的语音
    @Override
    public void mutedAudio(QueueInfo queueInfo) {
        switch (queueInfo.getStatus()) {
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO:
                queueInfo.setStatus(QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED);
                break;
            case QueueInfo.STATUS_CLOSE_SELF_AUDIO_AND_MUTED:
                queueInfo.setStatus(QueueInfo.STATUS_CLOSE_SELF_AUDIO);
                break;
            default:
                if (QueueInfo.hasOccupancy(queueInfo)) {
                    queueInfo.setStatus(QueueInfo.STATUS_BE_MUTED_AUDIO);
                } else {
                    queueInfo.setStatus(QueueInfo.STATUS_FORBID);
                }
                break;
        }
        chatRoomService.updateQueue(roomInfo.getRoomId(), queueInfo.getKey(), queueInfo.toString()).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                ToastHelper.showToast("该麦位语音已被屏蔽，无法发言");
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("屏蔽连麦请求失败 ， code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("屏蔽连麦请求异常 ， e = " + throwable);
            }
        });
    }

    /**
     * 判断麦位是否有人
     * return true:有人
     * false:没人
     */
    private void bottomButtonAction(BottomMenuDialog dialog, QueueInfo queueInfo, String s) {
        switch (s) {
            case "确定踢下麦位":
                bottomMenuDialog = new BottomMenuDialog();
                ArrayList arrayList = new ArrayList();
                arrayList.add("<font color = \"#ff4f4f\">确定踢下麦位</color>");
                arrayList.add("取消");
                removeLink(queueInfo);
                bottomMenuDialog.setItemClickListener((d, p) -> {
                    switch (d.get(p)) {
                        case "<font color = \"#ff4f4f\">确定踢下麦位</color>":
                            removeLink(queueInfo);
                            break;
                        case "取消":
                            bottomMenuDialog.dismiss();
                            break;
                    }
                });
                break;
            case "关闭麦位":
                closeAudio(queueInfo);
                break;
            case "将成员抱上麦位":
                inviteIndex = queueInfo.getIndex();
                chatRoomService.fetchQueue(roomInfo.getRoomId()).setCallback(new RequestCallback<List<Entry<String, String>>>() {
                    @Override
                    public void onSuccess(List<Entry<String, String>> param) {
                        MemberActivity.startRepeat(AudioLiveActivity.this, roomInfo.getRoomId(), getQueueList(param));
                    }

                    @Override
                    public void onFailed(int code) {

                    }

                    @Override
                    public void onException(Throwable exception) {

                    }
                });

                break;
            case "将TA踢下麦位":
                removeLink(queueInfo);
                break;
            case "屏蔽麦位":
                mutedAudio(queueInfo);
                break;
            case "解除语音屏蔽":
                openAudio(queueInfo);
                break;
            case "打开麦位":
                openAudio(queueInfo);
                break;
            case "退出并解散房间":
                exitRoom();
                break;
            case "取消":
                dialog.dismiss();
                break;
        }
        if (dialog.isVisible()) {
            dialog.dismiss();
        }
    }

    private void checkFile() {
        mixingInfo.path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + getPackageName() + File.separator + "music" + File.separator;
        mixingInfo.cycle = 3;
        mixingInfo.loop = true;
        mixingInfo.replace = false;
        mixingInfo.volume = 1f;
        musicPathArray = new String[2];
        musicPathArray[0] = mixingInfo.path + "first_song.mp3";
        musicPathArray[1] = mixingInfo.path + "second_song.mp3";
        currentPlayIndex = 0;
        new Thread(() -> {
            CommonUtil.copyAssetToFile(this, "music/first_song.mp3", mixingInfo.path, "first_song.mp3");
            CommonUtil.copyAssetToFile(this, "music/second_song.mp3", mixingInfo.path, "second_song.mp3");
        }).start();
    }

    @Override
    public void onBackPressed() {
        exitRoom();
        super.onBackPressed();
    }

    @Override
    protected AVChatParameters getRtcParameters() {
        AVChatParameters parameters = new AVChatParameters();
        parameters.setInteger(AVChatParameters.KEY_SESSION_MULTI_MODE_USER_ROLE, AVChatUserRole.NORMAL);
        return parameters;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    //初始化聊天室扩展字段（用于主播聊天室）默认打开
    private void updateRoomInfo(boolean isOpenMicrophone) {
        ChatRoomUpdateInfo chatRoomUpdateInfo = new ChatRoomUpdateInfo();
        Map<String, Object> param = new HashMap<>();
        if (isOpenMicrophone) {
            param.put(ROOM_MICROPHONE_OPEN, 1);
        } else {
            param.put(ROOM_MICROPHONE_OPEN, 0);
        }

        chatRoomUpdateInfo.setExtension(param);
        chatRoomService.updateRoomInfo(roomInfo.getRoomId(), chatRoomUpdateInfo, true, null);
    }


}
