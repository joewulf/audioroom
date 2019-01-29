package com.netease.audioroom.demo.base;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.netease.audioroom.demo.R;
import com.netease.audioroom.demo.adapter.MessageListAdapter;
import com.netease.audioroom.demo.adapter.QueueAdapter;
import com.netease.audioroom.demo.audio.SimpleNRtcCallback;
import com.netease.audioroom.demo.cache.DemoCache;
import com.netease.audioroom.demo.model.AccountInfo;
import com.netease.audioroom.demo.model.DemoRoomInfo;
import com.netease.audioroom.demo.model.QueueInfo;
import com.netease.audioroom.demo.model.QueueMember;
import com.netease.audioroom.demo.model.SimpleMessage;
import com.netease.audioroom.demo.util.CommonUtil;
import com.netease.audioroom.demo.util.ScreenUtil;
import com.netease.audioroom.demo.util.ToastHelper;
import com.netease.audioroom.demo.widget.HeadImageView;
import com.netease.audioroom.demo.widget.VerticalItemDecoration;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.chatroom.ChatRoomMessageBuilder;
import com.netease.nimlib.sdk.chatroom.ChatRoomService;
import com.netease.nimlib.sdk.chatroom.ChatRoomServiceObserver;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMessage;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomNotificationAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomPartClearAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomQueueChangeAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomRoomMemberInAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomTempMuteAddAttachment;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomTempMuteRemoveAttachment;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomData;
import com.netease.nimlib.sdk.chatroom.model.EnterChatRoomResultData;
import com.netease.nimlib.sdk.msg.MsgServiceObserve;
import com.netease.nimlib.sdk.msg.constant.ChatRoomQueueChangeType;
import com.netease.nimlib.sdk.msg.constant.MsgTypeEnum;
import com.netease.nimlib.sdk.msg.constant.NotificationType;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.nimlib.sdk.msg.model.CustomNotification;
import com.netease.nimlib.sdk.util.Entry;
import com.netease.nrtc.engine.rawapi.RtcRole;
import com.netease.nrtc.sdk.NRtc;
import com.netease.nrtc.sdk.NRtcCallback;
import com.netease.nrtc.sdk.NRtcEx;
import com.netease.nrtc.sdk.NRtcParameters;

import java.util.ArrayList;
import java.util.List;


/**
 * 主播与观众基础页，包含所有的基本UI元素
 */
public abstract class BaseAudioActivity extends BaseActivity implements ViewTreeObserver.OnGlobalLayoutListener {

    public static final String ROOM_INFO_KEY = "room_info_key";
    public static final String TAG = "AudioRoom";
    private static final int KEY_BOARD_MIN_SIZE = ScreenUtil.dip2px(DemoCache.getContext(), 80);

    //主播基础信息
    protected HeadImageView ivLiverAvatar;
    protected ImageView ivLiverAudioCloseHint;
    protected TextView tvLiverNick;
    protected TextView tvRoomName;

    // 各种控制开关
    protected ImageView ivMuteOtherText;
    protected ImageView ivAudioQuality;
    protected ImageView ivSelfAudioSwitch;
    protected ImageView ivRoomAudioSwitch;
    protected ImageView ivCancelLink;
    protected ImageView ivExistRoom;

    private EditText edtInput;

    //自己的麦位，只有观众有
    protected QueueInfo selfQueue;

    //聊天室队列（麦位）
    protected RecyclerView rcyQueueRecyclerView;

    protected QueueAdapter queueAdapter;

    //消息列表
    protected RecyclerView rcyChatMsgList;
    private LinearLayoutManager msgLayoutManager;
    protected MessageListAdapter msgAdapter;

    // 聊天室信息
    protected DemoRoomInfo roomInfo;


    // 聊天室服务
    protected ChatRoomService chatRoomService;


    //音视频接口
    protected NRtcEx nrtcEx;
    protected long audioUid;

    private int rootViewVisibleHeight;
    private View rootView;


    private BaseAdapter.ItemClickListener<QueueInfo> itemClickListener = new BaseAdapter.ItemClickListener<QueueInfo>() {
        @Override
        public void onItemClick(QueueInfo model, int position) {
            onQueueItemClick(model, position);
        }
    };
    private BaseAdapter.ItemLongClickListener<QueueInfo> itemLongClickListener = new BaseAdapter.ItemLongClickListener<QueueInfo>() {
        @Override
        public boolean onItemLongClick(QueueInfo model, int position) {
            return onQueueItemLongClick(model, position);
        }
    };

    // 自定义通知观察者
    private Observer<CustomNotification> customNotification = new Observer<CustomNotification>() {
        @Override
        public void onEvent(CustomNotification customNotification) {
            receiveNotification(customNotification);
        }
    };


    //聊天室消息观察者
    private Observer<List<ChatRoomMessage>> messageObserver = new Observer<List<ChatRoomMessage>>() {
        @Override
        public void onEvent(List<ChatRoomMessage> chatRoomMessages) {
            if (chatRoomMessages == null || chatRoomMessages.isEmpty()) {
                return;
            }
            StringBuffer logInfo = new StringBuffer();
            for (ChatRoomMessage message : chatRoomMessages) {
                if (message.getSessionType() != SessionTypeEnum.ChatRoom
                        || !TextUtils.equals(message.getSessionId(), roomInfo.getRoomId())) {
                    continue;
                }

                if (message.getAttachment() instanceof ChatRoomNotificationAttachment) {
                    NotificationType type = ((ChatRoomNotificationAttachment) message.getAttachment()).getType();
                    switch (type) {
                        // 成员进入聊天室 , 自己进来也有通知
                        case ChatRoomMemberIn:
                            ChatRoomRoomMemberInAttachment memberIn = (ChatRoomRoomMemberInAttachment) message.getAttachment();
                            logInfo.append("成员进入聊天室：nick =  ").append(memberIn.getOperatorNick())
                                    .append(", account = ").append(memberIn.getOperator());

                            memberIn(memberIn);
                            break;
                        // 成员退出聊天室
                        case ChatRoomMemberExit:
                            ChatRoomQueueChangeAttachment memberExit = (ChatRoomQueueChangeAttachment) message.getAttachment();
                            logInfo.append("成员退出聊天室：nick = ").append(memberExit.getOperatorNick()).
                                    append(",  account = ").append(memberExit.getOperator());
                            memberExit(memberExit);
                            break;

                        //成员被禁言
                        case ChatRoomMemberTempMuteAdd:
                            ChatRoomTempMuteAddAttachment addMuteMember = (ChatRoomTempMuteAddAttachment) message.getAttachment();
                            logInfo.append("成员被禁言：nick list =  ").append(addMuteMember.getTargetNicks()).
                                    append(" , account list = ").append(addMuteMember.getTargets());
                            memberMuteAdd(addMuteMember);
                            break;

                        //成员被解除禁言
                        case ChatRoomMemberTempMuteRemove:
                            ChatRoomTempMuteRemoveAttachment muteRemove = (ChatRoomTempMuteRemoveAttachment) message.getAttachment();
                            logInfo.append("成员被解除禁言：nick list =  ").append(muteRemove.getTargetNicks()).
                                    append(" , account list = ").append(muteRemove.getTargets());
                            memberMuteRemove(muteRemove);
                            break;
                        //队列变更
                        case ChatRoomQueueChange:
                            ChatRoomQueueChangeAttachment queueChange = (ChatRoomQueueChangeAttachment) message.getAttachment();
                            logInfo.append("队列变更：type = ").append(queueChange.getChatRoomQueueChangeType())
                                    .append(", key = ").append(queueChange.getKey())
                                    .append(", value = ").append(queueChange.getContent());

                            onQueueChange(queueChange);
                            break;

                        //队列批量变更，好像没用了
                        case ChatRoomQueueBatchChange:
                            ChatRoomPartClearAttachment queuePartClear = (ChatRoomPartClearAttachment) message.getAttachment();
                            logInfo.append("队列批量变更：").append(queuePartClear.getChatRoomQueueChangeType());
                            for (String key : queuePartClear.getContentMap().keySet()) {
                                logInfo.append("key = " + key + ", value= " + queuePartClear.getContentMap().get(key)).append(" ");
                            }
                            break;
                    }
                } else {
                    messageInComing(message);
                }
            }
            if (logInfo.length() > 0) {
                Log.i(TAG, logInfo.toString());
            }
        }
    };


    protected void memberMuteRemove(ChatRoomTempMuteRemoveAttachment muteRemove) {
    }

    protected void memberMuteAdd(ChatRoomTempMuteAddAttachment addMuteMember) {
    }

    protected void memberExit(ChatRoomQueueChangeAttachment memberExit) {
        ArrayList<String> exitNicks = memberExit.getTargetNicks();
        if (CommonUtil.isEmpty(exitNicks)) {
            return;
        }
        for (String nick : exitNicks) {
            SimpleMessage simpleMessage = new SimpleMessage("", "“" + nick + "”离开了房间", SimpleMessage.TYPE_MEMBER_CHANGE);
            msgAdapter.appendItem(simpleMessage);
        }
        scrollToBottom();
    }

    protected void memberIn(ChatRoomRoomMemberInAttachment memberIn) {
        ArrayList<String> inNicks = memberIn.getTargetNicks();
        if (CommonUtil.isEmpty(inNicks)) {
            return;
        }
        for (String nick : inNicks) {
            SimpleMessage simpleMessage = new SimpleMessage("", "“" + nick + "”进了房间", SimpleMessage.TYPE_MEMBER_CHANGE);
            msgAdapter.appendItem(simpleMessage);
        }
        scrollToBottom();
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        roomInfo = getIntent().getParcelableExtra(ROOM_INFO_KEY);
        if (roomInfo == null) {
            ToastHelper.showToast("聊天室信息不能为空");
            finish();
            return;
        }
        chatRoomService = NIMClient.getService(ChatRoomService.class);

        nrtcEx = (NRtcEx) NRtc.create(this, CommonUtil.readAppKey(), createNrtcCallBack());
        nrtcEx.setParameter(NRtcParameters.KEY_SESSION_MULTI_MODE, true);
        nrtcEx.setParameter(NRtcParameters.KEY_AUDIO_HIGH_QUALITY, true);
        audioUid = System.nanoTime();
        enterChatRoom(roomInfo.getRoomId());
        findBaseView();
        setupBaseViewInner();
        setupBaseView();


        rootView = getWindow().getDecorView();
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    protected NRtcCallback createNrtcCallBack() {
        return new SimpleNRtcCallback();
    }

    @Override
    protected void onDestroy() {
        release();
        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        super.onDestroy();
    }

    private void findBaseView() {
        View baseAudioView = findViewById(R.id.rl_base_audio_ui);

        if (baseAudioView == null) {
            throw new IllegalStateException("xml layout must include base_audio_ui.xml layout");
        }

        ivLiverAvatar = baseAudioView.findViewById(R.id.iv_liver_avatar);
        ivLiverAudioCloseHint = baseAudioView.findViewById(R.id.iv_liver_audio_close_hint);
        tvLiverNick = baseAudioView.findViewById(R.id.tv_liver_nick);

        tvRoomName = baseAudioView.findViewById(R.id.tv_chat_room_name);

        ivMuteOtherText = baseAudioView.findViewById(R.id.iv_mute_other_text);
        ivAudioQuality = baseAudioView.findViewById(R.id.iv_audio_quality_switch);
        ivSelfAudioSwitch = baseAudioView.findViewById(R.id.iv_close_self_audio_switch);
        ivRoomAudioSwitch = baseAudioView.findViewById(R.id.iv_close_room_audio_switch);
        ivCancelLink = baseAudioView.findViewById(R.id.iv_cancel_link);
        ivExistRoom = baseAudioView.findViewById(R.id.iv_exist_room);


        rcyQueueRecyclerView = baseAudioView.findViewById(R.id.rcy_queue_list);
        rcyChatMsgList = baseAudioView.findViewById(R.id.rcy_chat_message_list);

        edtInput = baseAudioView.findViewById(R.id.edt_input_text);
        baseAudioView.findViewById(R.id.tv_send_text).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendTextMessage();
            }
        });


    }


    private void setupBaseViewInner() {
        String name = roomInfo.getName();
        name = "房间：" + (TextUtils.isEmpty(name) ? roomInfo.getRoomId() : name) + "（" + roomInfo.getOnlineUserCount() + "人）";

        tvRoomName.setText(name);

        rcyQueueRecyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        queueAdapter = new QueueAdapter(null, this);
        rcyQueueRecyclerView.setAdapter(queueAdapter);

        queueAdapter.setItemClickListener(itemClickListener);
        queueAdapter.setItemLongClickListener(itemLongClickListener);


        msgLayoutManager = new LinearLayoutManager(this);
        rcyChatMsgList.setLayoutManager(msgLayoutManager);
        msgAdapter = new MessageListAdapter(null, this);
        rcyChatMsgList.addItemDecoration(new VerticalItemDecoration(Color.TRANSPARENT, ScreenUtil.dip2px(this, 9)));
        rcyChatMsgList.setAdapter(msgAdapter);
    }

    protected void initQueue(List<Entry<String, String>> entries) {
        ArrayList<QueueInfo> queueInfoList = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            QueueInfo queue = new QueueInfo();
            queue.setIndex(i);
            queueInfoList.add(queue);
        }
        if (entries == null) {
            queueAdapter.setItems(queueInfoList);
            return;
        }

        for (Entry<String, String> entry : entries) {
            if (TextUtils.isEmpty(entry.key) || !entry.key.startsWith(QueueInfo.QUEUE_KEY_PREFIX)) {
                continue;
            }
            if (TextUtils.isEmpty(entry.value)) {
                continue;
            }
            QueueInfo queueInfo = new QueueInfo(entry.value);
            queueInfoList.set(queueInfo.getIndex(), queueInfo);
            QueueMember member = queueInfo.getQueueMember();
            if (member != null && TextUtils.equals(member.getAccount(), DemoCache.getAccountId())) {
                selfQueue = queueInfo;
            }

        }
        queueAdapter.setItems(queueInfoList);
    }


    private void sendTextMessage() {
        String content = edtInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            ToastHelper.showToast("请输入消息内容");
            return;
        }
        ChatRoomMessage chatRoomMessage = ChatRoomMessageBuilder.createChatRoomTextMessage(roomInfo.getRoomId(), content);
        chatRoomService.sendMessage(chatRoomMessage, false).setCallback(new RequestCallback<Void>() {
            @Override
            public void onSuccess(Void aVoid) {

            }

            @Override
            public void onFailed(int i) {

            }

            @Override
            public void onException(Throwable throwable) {

            }
        });

        msgAdapter.appendItem(new SimpleMessage(DemoCache.getAccountInfo().nick, content, SimpleMessage.TYPE_NORMAL_MESSAGE));
        edtInput.setText("");
    }


    protected void messageInComing(ChatRoomMessage message) {
        if (message.getMsgType() != MsgTypeEnum.text) {
            return;
        }
        msgAdapter.appendItem(new SimpleMessage(message.getChatRoomMessageExtension().getSenderNick(),
                message.getContent(),
                SimpleMessage.TYPE_NORMAL_MESSAGE));
        scrollToBottom();
    }


    public void enterChatRoom(String roomId) {
        AccountInfo accountInfo = DemoCache.getAccountInfo();
        EnterChatRoomData roomData = new EnterChatRoomData(roomId);
        roomData.setAvatar(accountInfo.avatar);
        roomData.setNick(accountInfo.nick);
        chatRoomService.enterChatRoomEx(roomData, 2).setCallback(new RequestCallback<EnterChatRoomResultData>() {
            @Override
            public void onSuccess(EnterChatRoomResultData resultData) {
                enterRoomSuccess(resultData);
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("进入聊天室失败 ， code = " + i);
                finish();
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("进入聊天室异常 ，  e = " + throwable);
                finish();
            }
        });

    }

    protected void enterRoomSuccess(EnterChatRoomResultData resultData) {

        chatRoomService.fetchQueue(roomInfo.getRoomId()).setCallback(new RequestCallback<List<Entry<String, String>>>() {
            @Override
            public void onSuccess(List<Entry<String, String>> entries) {
                initQueue(entries);
            }

            @Override
            public void onFailed(int i) {
                ToastHelper.showToast("获取队列失败 ，  code = " + i);
            }

            @Override
            public void onException(Throwable throwable) {
                ToastHelper.showToast("获取队列异常，  e = " + throwable);
            }
        });

    }


    @Override
    protected void registerObserver(boolean register) {
        super.registerObserver(register);

        NIMClient.getService(MsgServiceObserve.class).observeCustomNotification(customNotification, register);
        NIMClient.getService(ChatRoomServiceObserver.class).observeReceiveMessage(messageObserver, register);
    }

    /**
     * 加入音视频 频道
     */
    protected boolean joinChannel(long selfUid) {
        if (nrtcEx == null) {
            return false;
        }
        return nrtcEx.joinChannel(null, roomInfo.getRoomId(), selfUid) == 0;
    }

    /**
     * 关闭自己的语音
     */
    protected void muteSelfAudio(boolean isMutex) {
        nrtcEx.muteLocalAudioStream(isMutex);
    }

    /**
     * 关闭聊天室的语音
     */
    protected void muteRoomAudio(boolean isMutex) {
        nrtcEx.muteAllRemoteAudioStream(isMutex);
    }

    /**
     * 设置观众模式，主播和连麦者都属于非观众
     */
    protected boolean enableAudienceRole(boolean enable) {
        if (nrtcEx == null) {
            return false;
        }
        return nrtcEx.setRole(enable ? RtcRole.AUDIENCE : RtcRole.NORMAL) == 0;
    }


    protected void release() {
        if (nrtcEx == null) {
            return;
        }
        nrtcEx.leaveChannel();
        nrtcEx.dispose();
        nrtcEx = null;
    }

    private void scrollToBottom() {
        msgLayoutManager.scrollToPosition(msgAdapter.getItemCount() - 1);
    }


    protected abstract int getContentViewID();

    protected abstract void setupBaseView();

    protected abstract void onQueueItemClick(QueueInfo model, int position);

    protected abstract boolean onQueueItemLongClick(QueueInfo model, int position);

    protected abstract void receiveNotification(CustomNotification customNotification);


    protected abstract void exitRoom();

    protected void onQueueChange(ChatRoomQueueChangeAttachment queueChange) {
        ChatRoomQueueChangeType changeType = queueChange.getChatRoomQueueChangeType();
        // 队列被清空
        if (changeType == ChatRoomQueueChangeType.DROP) {
            initQueue(null);
            return;
        }
        String value = queueChange.getContent();
        //新增元素或更新
        if (changeType == ChatRoomQueueChangeType.OFFER && !TextUtils.isEmpty(value)) {
            QueueInfo queueInfo = new QueueInfo(value);
            queueAdapter.updateItem(queueInfo.getIndex(), queueInfo);
            return;
        }
    }


    @Override
    public void onGlobalLayout() {

        int preHeight = rootViewVisibleHeight;

        //获取当前根视图在屏幕上显示的大小
        Rect r = new Rect();
        rootView.getWindowVisibleDisplayFrame(r);
        rootViewVisibleHeight = r.height();

        if (preHeight == 0 || preHeight == rootViewVisibleHeight) {
            return;
        }
        //根视图显示高度变大超过KEY_BOARD_MIN_SIZE，可以看作软键盘隐藏了
        if (rootViewVisibleHeight - preHeight >= KEY_BOARD_MIN_SIZE) {
            scrollToBottom();
            return;
        }
    }


}
