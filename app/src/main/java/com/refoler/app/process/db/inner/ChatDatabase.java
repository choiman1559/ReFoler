package com.refoler.app.process.db.inner;

import androidx.room.ColumnInfo;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import java.util.Locale;

public class ChatDatabase {

    public static final String TABLE_CHAT_HISTORY = "chat";
    public static final String COLUMN_CHAT_ID = "id";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_SENDER = "sender";
    public static final String COLUMN_MESSAGE = "message";
    public static final String COLUMN_STATUS = "status";

    public static final String SENDER_ASSISTANCE = "ASSISTANCE";
    public static final String SENDER_SYSTEM = "SYSTEM";
    public static final String SENDER_USER = "USER";

    public static final int STATUS_NONE = 0;
    public static final int STATUS_COMPLETE = 1;
    public static final int STATUS_PENDING = 2;
    public static final int STATUS_RECEIVING = 4;
    public static final int STATUS_ERROR = 8;
    public static final int STATUS_CANCELLED = 16;

    @Entity(tableName = TABLE_CHAT_HISTORY)
    public static class ChatHistory {
        @PrimaryKey
        @ColumnInfo(name = COLUMN_CHAT_ID)
        public int chatId;
        @ColumnInfo(name = COLUMN_DATE)
        public long date;
        @ColumnInfo(name = COLUMN_SENDER)
        public String sender;
        @ColumnInfo(name = COLUMN_MESSAGE)
        public String message;
        @ColumnInfo(name = COLUMN_STATUS)
        public int status;

        void createId() {
            chatId = String.format(Locale.getDefault(),"date: %d, sender: %s", date, sender).hashCode();
        }
    }

    @Dao
    public interface ChatDao {
        @Query("SELECT * FROM " + TABLE_CHAT_HISTORY + " ORDER BY " + COLUMN_DATE + " ASC")
        List<ChatDatabase.ChatHistory> getAll();

        @Insert
        void insert(ChatDatabase.ChatHistory chat);

        @Query("DELETE FROM " + TABLE_CHAT_HISTORY)
        void deleteAll();

        @Delete
        void delete(ChatDatabase.ChatHistory chat);

        @Update
        void replace(ChatDatabase.ChatHistory replaceInto);
    }

    public static ChatHistory makeUserChatHistory(String message) {
        ChatHistory chat = new ChatHistory();
        chat.date = System.currentTimeMillis();
        chat.sender = SENDER_USER;
        chat.message = message;
        chat.status = STATUS_NONE;

        chat.createId();
        return chat;
    }

    public static ChatHistory makeAssistanceChatHistory(String message) {
        ChatHistory chat = new ChatHistory();
        chat.date = System.currentTimeMillis() + 1L;
        chat.sender = SENDER_ASSISTANCE;
        chat.status = STATUS_PENDING;
        chat.message = message;

        chat.createId();
        return chat;
    }
}
