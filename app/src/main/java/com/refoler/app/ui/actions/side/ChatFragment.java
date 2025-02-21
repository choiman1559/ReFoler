package com.refoler.app.ui.actions.side;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.refoler.Refoler;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.WebSocketWrapper;
import com.refoler.app.backend.consts.EndPointConst;
import com.refoler.app.backend.consts.LlmConst;
import com.refoler.app.process.db.inner.ChatDatabase;
import com.refoler.app.process.db.inner.DbHelper;
import com.refoler.app.ui.holder.SideFragment;
import com.refoler.app.ui.holder.SideFragmentHolder;
import com.refoler.app.ui.utils.ToastHelper;
import com.refoler.app.utils.JsonRequest;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;

public class ChatFragment extends SideFragment {

    Activity mContext;
    EditText messageEditText;
    ImageButton sendButton;
    ImageButton removeAllButton;
    RecyclerView chatRecyclerView;

    DbHelper.AppDatabase appDatabase;
    List<ChatDatabase.ChatHistory> chatMessages = new ArrayList<>();
    ChatAdapter chatAdapter;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof Activity) mContext = (Activity) context;
        else throw new RuntimeException("Can't get Activity instanceof Context!");
    }

    @Override
    public OnBackPressedCallback getOnBackDispatcher() {
        return new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishScreen();
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_llm_chat, container, false);
    }

    private int findIndexOf(ChatDatabase.ChatHistory item) {
        for (int i = 0; i < chatMessages.size(); i++) {
            if (chatMessages.get(i).chatId == item.chatId) return i;
        }
        return -1;
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onViewCreated(@NonNull View baseView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(baseView, savedInstanceState);

        this.messageEditText = baseView.findViewById(R.id.searchKeyword);
        this.sendButton = baseView.findViewById(R.id.actionKeyword);
        this.removeAllButton = baseView.findViewById(R.id.removeAllButton);
        this.chatRecyclerView = baseView.findViewById(R.id.chatHistoryView);

        chatAdapter = new ChatAdapter(mContext, chatMessages);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        chatRecyclerView.setAdapter(chatAdapter);

        appDatabase = DbHelper.getInstance(mContext);
        new Thread(() -> {
            chatMessages.clear();
            chatMessages.addAll(appDatabase.chatDao().getAll());

            mContext.runOnUiThread(() -> {
                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
                chatAdapter.notifyDataSetChanged();
            });
        }).start();

        removeAllButton.setOnClickListener(v -> new Thread(() -> {
            appDatabase.chatDao().deleteAll();
            chatMessages.clear();
            mContext.runOnUiThread(() -> chatAdapter.notifyDataSetChanged());
        }).start());

        sendButton.setOnClickListener(v -> {
            String messageText = Objects.requireNonNull(messageEditText.getText()).toString();
            if (!messageText.isEmpty()) {
                sendButton.setEnabled(false);
                messageEditText.setText("");

                ChatDatabase.ChatHistory userChatHistory = ChatDatabase.makeUserChatHistory(messageText);
                ChatDatabase.ChatHistory assistanceChatHistory = ChatDatabase.makeAssistanceChatHistory(messageText);

                chatMessages.add(userChatHistory);
                chatMessages.add(assistanceChatHistory);

                chatAdapter.notifyItemInserted(chatMessages.size() - 2);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);

                new Thread(() -> {
                    appDatabase.chatDao().insert(userChatHistory);
                    appDatabase.chatDao().insert(assistanceChatHistory);
                    requestChat(assistanceChatHistory);
                }).start();
            }
        });

        MaterialToolbar toolbar = baseView.findViewById(R.id.toolbar);
        setToolbar(toolbar, false);
    }

    public void requestChat(ChatDatabase.ChatHistory chat) {
        Log.d("ddd", "requestChat: " + chat.chatId + " messageId: " + chat.message.hashCode());
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
        requestBuilder.setExtraData(chat.message);
        JsonRequest.postRequestPacket(mContext, EndPointConst.SERVICE_TYPE_LLM, requestBuilder, receivedPacket -> {
            if (receivedPacket.gotOk()) {
                Log.d("dddd", "Websocket Establishing!");
                chat.status = ChatDatabase.STATUS_RECEIVING;
                connectSocket(chat);
            } else {
                chat.status = ChatDatabase.STATUS_ERROR;
                onTerminateChat(null, chat);
            }
            mContext.runOnUiThread(() -> chatAdapter.notifyItemChanged(findIndexOf(chat)));
        });
    }

    public void connectSocket(ChatDatabase.ChatHistory chat) {
        Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
        requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(mContext));
        requestBuilder.setExtraData(Integer.toString(chat.message.hashCode()));

        WebSocketWrapper webSocketWrapper = new WebSocketWrapper(mContext);
        webSocketWrapper.setOnDataReceiveListener(new WebSocketWrapper.OnDataReceiveListener() {
            @Override
            public void onConnect() {
                Log.d("dddd", "Websocket Connected!");
                webSocketWrapper.postRequestPacket(requestBuilder);
            }

            @Override
            public void onReceive(@NonNull String data) {
                switch (data) {
                    case LlmConst.RAW_DATA_END_OF_CONVERSATION -> {
                        chat.status = ChatDatabase.STATUS_COMPLETE;
                        chat.message = chatAdapter.getFullMessage(chat);
                        Log.d("ddd", "Completed: " + chat.message);
                        onTerminateChat(webSocketWrapper, chat);
                    }
                    case LlmConst.RAW_DATA_ERROR_THROWN -> {
                        chat.status = ChatDatabase.STATUS_ERROR;
                        onTerminateChat(webSocketWrapper, chat);
                    }
                    default -> chatAdapter.appendToken(chat, data);
                }
                mContext.runOnUiThread(() -> chatAdapter.notifyItemChanged(findIndexOf(chat)));
            }
        });
        webSocketWrapper.connect(EndPointConst.SERVICE_TYPE_LLM);
    }

    public void onTerminateChat(@Nullable WebSocketWrapper webSocketWrapper, ChatDatabase.ChatHistory chat) {
        if (webSocketWrapper != null) {
            webSocketWrapper.disconnect();
        }
        mContext.runOnUiThread(() -> sendButton.setEnabled(true));
        appDatabase.chatDao().replace(chat);
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return DefaultFragment.class.getName();
    }

    private static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

        private final Markwon markwon;
        private final List<ChatDatabase.ChatHistory> chatMessages;
        private final ConcurrentHashMap<Integer, ArrayList<String>> tokensCache;

        public ChatAdapter(Context context, List<ChatDatabase.ChatHistory> chatMessages) {
            this.chatMessages = chatMessages;
            this.tokensCache = new ConcurrentHashMap<>();
            this.markwon = Markwon.builder(context)
                    .usePlugin(new AbstractMarkwonPlugin() {
                        @Override
                        public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                            builder.linkResolver((view, link) -> linkResolver(context, link));
                        }
                    }).build();
        }

        private void linkResolver(Context context, String link) {
            String[] metaInfo = link.split(LlmConst.RAW_DATA_PATH_TOKEN);
            Refoler.Device device = null;

            for (Refoler.Device found : DeviceWrapper.getAllRegisteredDeviceList(context)) {
                if (found.getDeviceId().equals(metaInfo[1].trim())) {
                    device = found;
                    break;
                }
            }

            if (device == null) {
                ToastHelper.show(context, context.getString(R.string.llm_chat_info_error_device_not_found), ToastHelper.LENGTH_SHORT);
                return;
            }

            String filePath;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                filePath = URLDecoder.decode(metaInfo[0].trim(), StandardCharsets.UTF_8);
            } else {
                try {
                    //noinspection CharsetObjectCanBeUsed
                    filePath = URLDecoder.decode(metaInfo[0].trim(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }

            SideFragmentHolder.getInstance().pushFragment(context,
                    new ReFileFragment(device, filePath)
                            .isFetchDbOnFirstLoad(true)
                            .isFindingFile(true));
        }

        public String getFullMessage(ChatDatabase.ChatHistory chat) {
            synchronized (tokensCache) {
                if (tokensCache.containsKey(chat.chatId)) {
                    ArrayList<String> tokens = Objects.requireNonNullElse(tokensCache.get(chat.chatId), new ArrayList<>());
                    tokensCache.remove(chat.chatId);
                    return String.join("", tokens).trim();
                }
                return "";
            }
        }

        /**
         * @noinspection SequencedCollectionMethodCanBeUsed
         */
        public String getLatestToken(ChatDatabase.ChatHistory chat) {
            synchronized (tokensCache) {
                if (tokensCache.containsKey(chat.chatId)) {
                    ArrayList<String> tokens = Objects.requireNonNullElse(tokensCache.get(chat.chatId), new ArrayList<>());
                    return tokens.get(tokens.size() - 1);
                }
                return "";
            }
        }

        public void appendToken(ChatDatabase.ChatHistory chat, String appendToken) {
            appendToken = appendToken
                    .replace(LlmConst.RAW_DATA_LINE_SEPARATION, System.lineSeparator())
                    .replace(LlmConst.RAW_DATA_SPACE, " ");

            synchronized (tokensCache) {
                ArrayList<String> tokens = Objects.requireNonNullElse(tokensCache.get(chat.chatId), new ArrayList<>());
                tokens.add(appendToken);
                tokensCache.put(chat.chatId, tokens);
            }
        }

        public static class AssistanceViewHolder extends ChatViewHolder {

            ProgressBar chatProcessBar;
            ImageView chatErrorIcon;
            TextView chatProcessTextview;
            TextView chatErrorTextview;

            public AssistanceViewHolder(@NonNull View itemView) {
                super(itemView);
                chatProcessBar = itemView.findViewById(R.id.chatProcessBar);
                chatErrorIcon = itemView.findViewById(R.id.chatErrorIcon);
                chatProcessTextview = itemView.findViewById(R.id.chatProcessTextview);
                chatErrorTextview = itemView.findViewById(R.id.chatErrorTextview);
            }

            public void setMessageComplete(Markwon markwon, String messageComplete) {
                chatProcessBar.setVisibility(View.GONE);
                chatErrorIcon.setVisibility(View.GONE);
                chatProcessTextview.setVisibility(View.GONE);
                chatErrorTextview.setVisibility(View.GONE);

                messageTextview.setVisibility(View.VISIBLE);
                markwon.setMarkdown(messageTextview, messageComplete);
            }

            public void appendMessage(String message) {
                chatProcessBar.setVisibility(View.GONE);
                chatErrorIcon.setVisibility(View.GONE);
                chatProcessTextview.setVisibility(View.GONE);
                chatErrorTextview.setVisibility(View.GONE);

                setMessage(String.format("%s%s", getMessage(), message));
            }

            public void setProgress() {
                chatProcessBar.setVisibility(View.VISIBLE);
                chatErrorIcon.setVisibility(View.GONE);
                chatProcessTextview.setVisibility(View.VISIBLE);
                chatErrorTextview.setVisibility(View.GONE);

                messageTextview.setText("");
                messageTextview.setVisibility(View.GONE);
            }

            public void setError() {
                chatProcessBar.setVisibility(View.GONE);
                chatErrorIcon.setVisibility(View.VISIBLE);
                chatProcessTextview.setVisibility(View.GONE);
                chatErrorTextview.setVisibility(View.VISIBLE);

                messageTextview.setText("");
                messageTextview.setVisibility(View.GONE);
            }
        }

        public static class ChatViewHolder extends RecyclerView.ViewHolder {

            TextView messageTextview;
            TextView dateTextview;

            public ChatViewHolder(@NonNull View itemView) {
                super(itemView);
                messageTextview = itemView.findViewById(R.id.messageTextview);
                dateTextview = itemView.findViewById(R.id.dateTextview);
            }

            public String getMessage() {
                return Objects.requireNonNullElse(messageTextview.getText(), "").toString();
            }

            public void setMessage(String message) {
                if (messageTextview.getVisibility() == View.GONE) {
                    messageTextview.setVisibility(View.VISIBLE);
                }
                messageTextview.setText(message);
            }

            public void setDate(long date) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm a", Locale.getDefault());
                Date dateTime = new Date(date);
                String formattedTime = dateFormat.format(dateTime);
                dateTextview.setText(formattedTime);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return chatMessages.get(position).sender.hashCode();
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == ChatDatabase.SENDER_ASSISTANCE.hashCode()) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cardview_chat_assistance, parent, false);
                return new AssistanceViewHolder(view);
            } else if (viewType == ChatDatabase.SENDER_USER.hashCode()) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.cardview_chat_user, parent, false);
                return new ChatViewHolder(view);
            }
            throw new IllegalStateException("Unknown view type: " + viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            ChatDatabase.ChatHistory chatMessage = chatMessages.get(position);
            holder.setDate(chatMessage.date);

            if (holder instanceof AssistanceViewHolder assistanceViewHolder) {
                switch (chatMessage.status) {
                    case ChatDatabase.STATUS_PENDING -> assistanceViewHolder.setProgress();
                    case ChatDatabase.STATUS_COMPLETE ->
                            assistanceViewHolder.setMessageComplete(markwon, chatMessage.message);
                    case ChatDatabase.STATUS_ERROR -> assistanceViewHolder.setError();
                    case ChatDatabase.STATUS_RECEIVING ->
                            assistanceViewHolder.appendMessage(getLatestToken(chatMessage));
                }
            } else {
                holder.setMessage(chatMessage.message);
            }
        }

        @Override
        public int getItemCount() {
            return chatMessages.size();
        }
    }
}
