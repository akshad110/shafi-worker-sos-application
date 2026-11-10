package com.example.shafi.data.local;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cache_blobs")
public class BlobEntity {
    @PrimaryKey
    @NonNull
    public String bucket = "";
    public String json;
}
