package com.refoler.app.process.db.inner;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.refoler.app.ui.PrefsKeyConst;

public class DbHelper {
    @Database(entities = {ChatDatabase.ChatHistory.class}, version = 1)
    public abstract static class AppDatabase extends RoomDatabase {
        public abstract ChatDatabase.ChatDao chatDao();
    }

    private static DbHelper instance;
    private final AppDatabase appDatabase;

    private DbHelper(Context context) {
        appDatabase = Room.databaseBuilder(context, AppDatabase.class, PrefsKeyConst.DIR_FILE_DATABASE).build();
    }

    public static AppDatabase getInstance(Context context) {
        if(instance == null) {
            instance = new DbHelper(context);
        }
        return instance.appDatabase;
    }
}
