package com.mexadev.aura.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.mexadev.aura.data.local.entity.NotificationEntity;

import java.util.List;
import kotlinx.coroutines.flow.Flow;

@Dao
public interface NotificationDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotification(NotificationEntity notification);

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    Flow<List<NotificationEntity>> getAllNotifications();

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    Flow<Integer> getUnreadCount();

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    void markAsRead(int id);
    
    @Query("UPDATE notifications SET isRead = 1")
    void markAllAsRead();
    
    @Query("DELETE FROM notifications")
    void clearAll();
}
