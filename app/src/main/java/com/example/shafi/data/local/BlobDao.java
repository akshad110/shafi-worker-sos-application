package com.example.shafi.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface BlobDao {
    @Query("SELECT * FROM cache_blobs WHERE bucket = :bucket LIMIT 1")
    BlobEntity get(String bucket);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(BlobEntity entity);
}
