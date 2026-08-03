package com.mexadev.aura.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.mexadev.aura.data.local.dao.NotificationDao;
import com.mexadev.aura.data.local.entity.NotificationEntity;

@Database(entities = {NotificationEntity.class}, version = 2, exportSchema = false)
public abstract class AuraDatabase extends RoomDatabase {

    public abstract NotificationDao notificationDao();

    private static volatile AuraDatabase INSTANCE;

    public static AuraDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AuraDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AuraDatabase.class, "aura_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
