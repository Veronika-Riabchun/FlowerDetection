package com.example.flowerdetection.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "flowers_info")
public class Flower {

    @PrimaryKey
    public int id;

    public String className;
    public String imageList;
    public String color;
    public String size;
    public String lighting;
    public String temperature;
    public String soil;
    public String blooming;
    public String funFact;

    public String getClassName() { return className; }

    public String getImageList() { return imageList; }

    public String getColor() { return color; }

    public String getSize() { return size; }

    public String getLighting() { return lighting; }

    public String getTemperature() { return temperature; }

    public String getSoil() { return soil; }

    public String getBlooming() { return blooming; }

    public String getFunFact() { return funFact; }
}
