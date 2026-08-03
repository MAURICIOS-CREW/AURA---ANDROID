package com.mexadev.aura.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@Entity(tableName = "notifications")
public class NotificationEntity {
    
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @NonNull
    public String notificationId = "";
    
    @NonNull
    public String title = "";
    
    @NonNull
    public String body = "";
    
    @Nullable
    public String type;
    
    @Nullable
    public String status;
    
    @NonNull
    public String timestamp = "";
    
    @Nullable
    public String message;
    
    @Nullable
    public String guestName;
    
    @Nullable
    public String scannedCode;
    
    @Nullable
    public String deviceIdentifier;
    
    @Nullable
    public String accessType;
    
    @Nullable
    public String method;
    
    @Nullable
    public String validFrom;
    
    @Nullable
    public String validUntil;
    
    @Nullable
    public String uses;
    
    @Nullable
    public String maxUses;
    
    @Nullable
    public String activeDays;
    
    @Nullable
    public String startTime;
    
    @Nullable
    public String endTime;
    
    public boolean isRead = false;
    
    @NonNull
    public String payloadJson = "";
}
