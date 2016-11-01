package com.wwh.slidecardpanel.card;

import android.graphics.drawable.Drawable;

/**
 * Card数据实体类
 * <p>
 * Created by wwh on 2016/10/31.
 */

public class CardBean {
    private Drawable img;
    private String name;
    private int age;

    public Drawable getImg() {
        return img;
    }

    public void setImg(Drawable img) {
        this.img = img;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
