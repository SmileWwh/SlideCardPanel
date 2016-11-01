package com.wwh.slidecardpanel;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.wwh.slidecardpanel.card.CardBean;
import com.wwh.slidecardpanel.card.SlideCardPanel;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by wwh on 2016/10/31.
 */

public class MainActivity extends Activity {

    Context mContext;

    TextView refreshTv;
    SlideCardPanel cardView;
    List<CardBean> cardList = new ArrayList<>();//当前显示卡片的数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        initViews();
        bindEvents();
        refreshData();
    }

    private void refreshData() {
        cardList.clear();
        for (int i = 0; i < 20; i++) {
            CardBean bean = new CardBean();
            bean.setName("卡片" + i);
            bean.setAge(i);
            switch (i % 3) {
                case 0:
                    bean.setImg(getResources().getDrawable(R.mipmap.photo_1));
                    break;
                case 1:
                    bean.setImg(getResources().getDrawable(R.mipmap.photo_2));
                    break;
                case 2:
                default:
                    bean.setImg(getResources().getDrawable(R.mipmap.photo_3));
                    break;
            }
            cardList.add(bean);
        }
        cardView.fillData(cardList);
        Toast.makeText(mContext, "更新数据成功", Toast.LENGTH_SHORT).show();
    }

    private void initViews() {
        cardView = (SlideCardPanel) findViewById(R.id.card_panel);
        refreshTv = (TextView) findViewById(R.id.refresh_data);
        cardView.setCardSwitchListener(cardSwitchListener);
    }

    private void bindEvents() {
        refreshTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshData();
            }
        });

    }

    SlideCardPanel.CardSwitchListener cardSwitchListener = new SlideCardPanel.CardSwitchListener() {

        @Override
        public void onShow(View cardView, int index) {
            cardView.findViewById(R.id.card_item_like_iv).setAlpha(0);
            cardView.findViewById(R.id.card_item_dislike_iv).setAlpha(0);
        }

        @Override
        public void onCardVanish(View changedView, int index, int type) {
            Toast.makeText(mContext, type == SlideCardPanel.VANISH_TYPE_LEFT ? "左划" : "右划", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onItemClick(View view, int index) {
            Toast.makeText(mContext, "点击了第" + index + "张卡片, 姓名：" + cardList.get(index).getName(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onViewPosition(View changedView, float dx, float dy) {
            changedView.findViewById(R.id.card_item_like_iv).setAlpha(dx);
            changedView.findViewById(R.id.card_item_dislike_iv).setAlpha(dy);
        }
    };

}
