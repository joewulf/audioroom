package com.netease.audioroom.demo.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.daimajia.swipe.SwipeLayout;
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter;
import com.netease.audioroom.demo.R;
import com.netease.audioroom.demo.widget.HeadImageView;
import com.netease.nimlib.sdk.chatroom.model.ChatRoomMember;

import java.util.List;

public class MuteMemberListAdapter extends RecyclerSwipeAdapter<MuteMemberListAdapter.MuteMemberViewHolder> {

    private Context context;
    private List<ChatRoomMember> list;

    public interface IRemoveMute {
        void remove(int position);
    }

    IRemoveMute removeMute;

    public MuteMemberListAdapter(Context context, List<ChatRoomMember> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }


    @Override
    public int getSwipeLayoutResourceId(int position) {
        return position;
    }

    @Override
    public MuteMemberViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new MuteMemberViewHolder(LayoutInflater.from(context).inflate(R.layout.item_mute_swipe, parent, false));
    }

    @Override
    public void onBindViewHolder(MuteMemberViewHolder viewHolder, int position) {
        viewHolder.swipeLayout.setShowMode(SwipeLayout.ShowMode.LayDown);
        viewHolder.linearLayout.setOnClickListener(v -> removeMute.remove(position));
        viewHolder.name.setText(list.get(position).getNick());
        viewHolder.headImageView.loadAvatar(list.get(position).getAvatar());
    }

    public class MuteMemberViewHolder extends RecyclerView.ViewHolder {
        SwipeLayout swipeLayout;
        LinearLayout linearLayout;
        HeadImageView headImageView;
        TextView name;

        public MuteMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            swipeLayout = itemView.findViewById(R.id.swipeLayout);
            linearLayout = itemView.findViewById(R.id.bottom_wrapper);
            headImageView = itemView.findViewById(R.id.memberinfo).findViewById(R.id.headview);
            name = itemView.findViewById(R.id.memberinfo).findViewById(R.id.chatroom_name);
        }
    }

    public void setRemoveMute(IRemoveMute removeMute) {
        this.removeMute = removeMute;
    }
}

