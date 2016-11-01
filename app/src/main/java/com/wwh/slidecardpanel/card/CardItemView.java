package com.wwh.slidecardpanel.card;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wwh.slidecardpanel.R;

/**
 * Created by wwh on 2016/10/31.
 */

public class CardItemView extends LinearLayout {
    Context mContext;

    View shadeView;
    TextView nameTv, ageTv;
    ImageView coverIv;

    public CardItemView(Context context) {
        this(context, null);
    }

    public CardItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        inflate(mContext, R.layout.layout_card_item, this);
        initViews();
    }

    private void initViews() {
        shadeView = findViewById(R.id.card_item_shade);
        coverIv = (ImageView) findViewById(R.id.card_item_cover_iv);
        nameTv = (TextView) findViewById(R.id.card_item_name_tv);
        ageTv = (TextView) findViewById(R.id.card_item_age_tv);
    }

    //设置阴影
    public void setShadeLayer(int shaderLayer) {
        shadeView.setBackgroundResource(shaderLayer);
    }

    public void fillData(CardBean bean) {
        nameTv.setText(bean.getName());
        ageTv.setText(" , " + bean.getAge());
        coverIv.setBackgroundDrawable(bean.getImg());
    }
}
