package com.example.flowerdetection.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.example.flowerdetection.model.Flower;

import java.util.List;

@Dao
public interface FlowerDao {

    @Query("SELECT * FROM flowers_info WHERE id = :id+1")
    Flower getFlowerById(int id);

    @Query("SELECT * FROM flowers_info WHERE LOWER(TRIM(className)) = LOWER(TRIM(:className)) LIMIT 1")
    Flower getFlowerByClassName(String className);

    @Query("SELECT * FROM flowers_info")
    List<Flower> getAllFlowers();
}
