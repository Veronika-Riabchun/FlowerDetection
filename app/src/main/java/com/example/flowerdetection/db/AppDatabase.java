package com.example.flowerdetection.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.flowerdetection.dao.FlowerDao;
import com.example.flowerdetection.model.Flower;

@Database(entities = {Flower.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static AppDatabase INSTANCE;

    public abstract FlowerDao flowerDao();

    public static synchronized AppDatabase getInstance(Context context, String dbName) {
        if (INSTANCE == null || !INSTANCE.getOpenHelper().getDatabaseName().equals(dbName)) {
            INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, dbName)
                    .createFromAsset(dbName)
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return INSTANCE;
    }
}
