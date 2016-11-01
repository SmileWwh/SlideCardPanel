package com.wwh.slidecardpanel.card;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.wwh.slidecardpanel.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片滑动面板，主要逻辑实现类
 * Created by wwh on 2016/10/31.
 */
@SuppressLint({"HandlerLeak", "NewApi", "ClickableViewAccessibility"})
public class SlideCardPanel extends ViewGroup {
    private static final int X_VEL_THRESHOLD = 900;
    private static final int X_DISTANCE_THRESHOLD = 300;
    private static final int MAX_SLIDE_DISTANCE_LINKAGE = 400; // 卡片左右滑动距离临界值 水平距离+垂直距离
    private static final int AUTO_SLIDE_SPEED = 150;//控制拖拽过程中松手后，自动滑行的速度

    //卡片层叠阴影
    private static final int[] layerDrawables = new int[]{R.drawable.shape_rect_solid_trans,
            R.drawable.shape_rect_solid_gray1_t0, R.drawable.shape_rect_solid_gray2_t0};

    private static float SCALE_STEP = 0.08f; //卡片叠加缩放的步长
    private int  itemPaddingTop = 0;//卡片与顶部的距离
    private int yOffsetStep = 40; // view叠加垂直偏移量的步长


    private CardSwitchListener cardSwitchListener; // 回调接口
    private GestureDetectorCompat moveDetector;
    private ViewDragHelper mDragHelper; //相比原生的ViewDragHelper，仅仅只是修改了Interpolator
    private int initCenterViewX = 0, initCenterViewY = 0; //初始状态中间View的x位置,y位置
    private int allWidth = 0; // 面板的宽度
    private int allHeight = 0; // 面板的高度
    private int childWith = 0; // 每一个子View对应的宽度

    public static final int VANISH_TYPE_LEFT = 0;//左划
    public static final int VANISH_TYPE_RIGHT = 1;//右划

    private boolean slideLock = false;// 滑动锁
    private Object syncLock = new Object();
    private int isShowItem = 0; // 当前正在显示的项
    private List<CardBean> dataList; // 存储的数据链表
    private List<CardItemView> viewList = new ArrayList<>(); // 存放的是每一层的view，从顶到底
    private List<View> releasedViewList = new ArrayList<>(); // 手指松开后存放的view列表

    public SlideCardPanel(Context context) {
        this(context, null);
    }

    public SlideCardPanel(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlideCardPanel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlideCard);
        itemPaddingTop = (int) a.getDimension(R.styleable.SlideCard_itemPaddingTop, itemPaddingTop);
        yOffsetStep = (int) a.getDimension(R.styleable.SlideCard_yOffsetStep, yOffsetStep);
        SCALE_STEP = a.getFloat(R.styleable.SlideCard_scaleRatio, SCALE_STEP);
        // 滑动相关类
        mDragHelper = ViewDragHelper
                .create(this, 10f, new DragHelperCallback());
        mDragHelper.setEdgeTrackingEnabled(ViewDragHelper.EDGE_LEFT);
        a.recycle();

        moveDetector = new GestureDetectorCompat(context,
                new MoveDetector());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
                resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
                resolveSizeAndState(maxHeight, heightMeasureSpec, 0));

        allWidth = getMeasuredWidth();
        allHeight = getMeasuredHeight();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // 布局卡片view
        int size = viewList.size();
        for (int i = 0; i < size; i++) {
            View viewItem = viewList.get(i);
            int childHeight = viewItem.getMeasuredHeight();
            viewItem.layout(left, top + itemPaddingTop, right, top + itemPaddingTop + childHeight);
            int offset = yOffsetStep * i;
            float scale = 1 - SCALE_STEP * i;
            if (i > 2) {
                // 备用的view
                offset = yOffsetStep * 2;
                scale = 1 - SCALE_STEP * 2;
            }

            viewItem.offsetTopAndBottom(offset);
            viewItem.setScaleX(scale);
            viewItem.setScaleY(scale);
        }

        // 初始化一些中间参数
        initCenterViewX = viewList.get(0).getLeft();
        initCenterViewY = viewList.get(0).getTop();
        childWith = viewList.get(0).getMeasuredWidth();
    }

    /**
     * 这是View的方法，该方法不支持android低版本（2.2、2.3）的操作系统，所以手动复制过来以免强制退出
     */
    public static int resolveSizeAndState(int size, int measureSpec,
                                          int childMeasuredState) {
        int result = size;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                result = size;
                break;
            case MeasureSpec.AT_MOST:
                if (specSize < size) {
                    result = specSize | MEASURED_STATE_TOO_SMALL;
                } else {
                    result = size;
                }
                break;
            case MeasureSpec.EXACTLY:
                result = specSize;
                break;
        }
        return result | (childMeasuredState & MEASURED_STATE_MASK);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // 渲染完成，初始化卡片view列表
        viewList.clear();
        int num = getChildCount();
        for (int i = num - 1; i >= 0; i--) {
            View childView = getChildAt(i);
            if (childView instanceof CardItemView) {
                CardItemView viewItem = (CardItemView) childView;
                viewItem.setTag(i + 1);
                final View root = viewItem.findViewById(R.id.card_root);
                root.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (cardSwitchListener != null && root.getScaleX() > 1 - SCALE_STEP) {
                            cardSwitchListener.onItemClick(v, isShowItem);
                        }
                    }
                });
                viewList.add(viewItem);
            }
        }
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        } else {
            // 动画结束
            synchronized (this) {
                if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                    orderViewStack();
                    slideLock = false;
                }
            }
        }
    }

    /* touch事件的拦截与处理都交给mDraghelper来处理 */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean shouldIntercept = mDragHelper.shouldInterceptTouchEvent(ev);
        boolean moveFlag = moveDetector.onTouchEvent(ev);
        int action = ev.getActionMasked();
        //加入对btnLock的判断,防止在动画期间点击卡片执行orderViewStack出现BUG
        if (!slideLock && action == MotionEvent.ACTION_DOWN) {
            // ACTION_DOWN的时候就对view重新排序
            orderViewStack();

            // 保存初次按下时arrowFlagView的Y坐标
            // action_down时就让mDragHelper开始工作，否则有时候导致异常
            mDragHelper.processTouchEvent(ev);
        }

        return shouldIntercept && moveFlag;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        try {
            // 统一交给mDragHelper处理，由DragHelperCallback实现拖动效果
            // 该行代码可能会抛异常，正式发布时请将这行代码加上try catch
            mDragHelper.processTouchEvent(e);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return true;
    }

    /**
     * 对View重新排序
     */
    private void orderViewStack() {
        synchronized (syncLock) {
            if (releasedViewList.size() == 0) {
                return;
            }
            CardItemView changedView = (CardItemView) releasedViewList.get(0);
            if (changedView.getLeft() == initCenterViewX) {
                releasedViewList.remove(0);
                return;
            }
            // 1. 消失的卡片View位置重置，由于大多手机会重新调用onLayout函数，所以此处大可以不做处理，不信你注释掉看看
            changedView.offsetLeftAndRight(initCenterViewX
                    - changedView.getLeft());
            changedView.offsetTopAndBottom(initCenterViewY
                    - changedView.getTop() + yOffsetStep * 2);
            float scale = 1.0f - SCALE_STEP * 2;
            changedView.setScaleX(scale);
            changedView.setScaleY(scale);

            //更新并绘制卡片阴影
            updateShaderLayer();

            // 2. 卡片View在ViewGroup中的顺次调整
            int num = viewList.size();
            for (int i = num - 1; i > 0; i--) {
                View tempView = viewList.get(i);
                tempView.bringToFront();
            }

            // 3. changedView填充新数据
            int newIndex = isShowItem + 4;
            if (newIndex < dataList.size()) {
                CardBean dataItem = dataList.get(newIndex);
                changedView.fillData(dataItem);
            } else {
                changedView.setVisibility(View.INVISIBLE);
            }

            // 4. viewList中的卡片view的位次调整
            viewList.remove(changedView);
            viewList.add(changedView);
            releasedViewList.remove(0);

            // 5. 更新showIndex、接口回调
            if (isShowItem + 1 < dataList.size()) {
                isShowItem++;
            }
            if (null != cardSwitchListener) {
                cardSwitchListener.onShow(viewList.get(viewList.size() - 1), isShowItem);
            }
        }
    }

    /**
     * 顶层卡片View位置改变，底层的位置需要调整
     *
     * @param changedView 顶层的卡片view
     */
    private void processLinkageView(View changedView) {
        int changeViewLeft = changedView.getLeft();
        int changeViewTop = changedView.getTop();
        int distance = Math.abs(changeViewTop - initCenterViewY)
                + Math.abs(changeViewLeft - initCenterViewX);
        float rate = distance / (float) MAX_SLIDE_DISTANCE_LINKAGE;

        float rate1 = rate;
        float rate2 = rate - 0.2f;

        if (rate > 1) {
            rate1 = 1;
        }

        if (rate2 < 0) {
            rate2 = 0;
        } else if (rate2 > 1) {
            rate2 = 1;
        }

        updateShaderLayer();
        if (rate < 0.1f) {
            //卡片回到原来的位置
            drawShaderLayer();
        }
        ajustLinkageViewItem(changedView, rate1, 1);
        ajustLinkageViewItem(changedView, rate2, 2);

    }

    /**
     * 松手时处理滑动到边缘的动画
     *
     * @param xvel X方向上的滑动速度
     */
    private void animToSide(View changedView, float xvel, float yvel) {
        int finalX = initCenterViewX;
        int finalY = initCenterViewY;
        int flyType = -1;

        // 1. 下面这一坨计算finalX和finalY，要读懂代码需要建立一个比较清晰的数学模型才能理解，不信拉倒
        int dx = changedView.getLeft() - initCenterViewX;
        int dy = changedView.getTop() - initCenterViewY;
        if (dx == 0) {
            // 由于dx作为分母，此处保护处理
            dx = 1;
        }
        if (xvel > X_VEL_THRESHOLD || dx > X_DISTANCE_THRESHOLD) {
            finalX = allWidth;
            finalY = dy * (childWith + initCenterViewX) / dx + initCenterViewY;
            flyType = VANISH_TYPE_RIGHT;
        } else if (xvel < -X_VEL_THRESHOLD || dx < -X_DISTANCE_THRESHOLD) {
            finalX = -childWith;
            finalY = dy * (childWith + initCenterViewX) / (-dx) + dy
                    + initCenterViewY;
            flyType = VANISH_TYPE_LEFT;
        }

        // 如果斜率太高，就折中处理
        if (finalY > allHeight) {
            finalY = allHeight;
        } else if (finalY < -allHeight / 2) {
            finalY = -allHeight / 2;
        }

        /*****************/
        // 如果没有飞向两侧，而是回到了中间，需要谨慎处理
        if (finalX != initCenterViewX) {
            releasedViewList.add(changedView);
            // 2. 启动动画
            if (mDragHelper.smoothSlideViewTo(changedView, finalX, finalY)) {
                ViewCompat.postInvalidateOnAnimation(this);
            }

            // 3. 消失动画即将进行，listener回调
            if (flyType >= 0 && cardSwitchListener != null) {
                cardSwitchListener.onCardVanish(changedView, isShowItem, flyType);
            }
        } else {
            // 2. 启动动画
            if (mDragHelper.smoothSlideViewTo(changedView, finalX, finalY)) {
                ViewCompat.postInvalidateOnAnimation(this);
            }
        }
        /*****************/
    }

    /**
     * 手动触发卡片消失动画
     */
    private void vanishOnBtnClick(int type) {
        synchronized (syncLock) {
            View animateView = viewList.get(0);
            if (animateView.getVisibility() != View.VISIBLE || releasedViewList.contains(animateView)) {
                return;
            }

            int finalX = 0;
            if (type == VANISH_TYPE_LEFT) {
                finalX = -childWith;
            } else if (type == VANISH_TYPE_RIGHT) {
                finalX = allWidth;
            }

            if (finalX != 0) {
                releasedViewList.add(animateView);
                if (mDragHelper.smoothSlideViewTo(animateView, finalX, initCenterViewY + allHeight)) {
                    ViewCompat.postInvalidateOnAnimation(this);
                }
                if (type >= 0 && cardSwitchListener != null) {
                    cardSwitchListener.onCardVanish(animateView, isShowItem, type);
                }
            }
        }
    }

    // 由index对应view变成index-1对应的view
    private void ajustLinkageViewItem(View changedView, float rate, int index) {
        int changeIndex = viewList.indexOf(changedView);
        int initPosY = yOffsetStep * index;
        float initScale = 1 - SCALE_STEP * index;

        int nextPosY = yOffsetStep * (index - 1);
        float nextScale = 1 - SCALE_STEP * (index - 1);

        int offset = (int) (initPosY + (nextPosY - initPosY) * rate);
        float scale = initScale + (nextScale - initScale) * rate;

        View ajustView = viewList.get(changeIndex + index);
        ajustView.findViewById(R.id.card_item_dislike_iv).setAlpha(0);
        ajustView.findViewById(R.id.card_item_like_iv).setAlpha(0);
        ajustView.offsetTopAndBottom(offset - ajustView.getTop()
                + initCenterViewY);
        ajustView.setScaleX(scale);
        ajustView.setScaleY(scale);
    }

    //初始化绘制卡片阴影
    private void drawShaderLayer() {
        int i = 0;
        for (CardItemView view : viewList) {
            if (i < layerDrawables.length) {
                view.setShadeLayer(layerDrawables[i++]);
            } else {
                view.setShadeLayer(layerDrawables[layerDrawables.length - 1]);
            }
        }
    }

    //更新并绘制卡片阴影
    private void updateShaderLayer() {
        int i = 0;
        for (CardItemView view : viewList) {
            if (i == 0) {
                view.setShadeLayer(layerDrawables[0]);
            } else {
                if ((i - 1) < layerDrawables.length) {
                    view.setShadeLayer(layerDrawables[i - 1]);
                } else {
                    view.setShadeLayer(layerDrawables[layerDrawables.length - 1]);
                }
            }
            ++i;
        }
    }

    /**
     * 填充数据（刷新数据时也调用该方法）
     */
    public void fillData(List<CardBean> list) {
        isShowItem = 0;
        if (list != null && list.size() > 0) {
            //如果有旧数据，先清空旧数据，并将卡片位置重置，避免卡片顺序混乱
            if (releasedViewList.size() > 0) {
                CardItemView changedView = (CardItemView) releasedViewList.get(0);
                if (changedView != null) {
                    if (changedView.getLeft() == initCenterViewX) {
                        releasedViewList.remove(0);
                    } else {
                        //改变View布局
                        changedView.offsetLeftAndRight(initCenterViewX
                                - changedView.getLeft());
                        changedView.offsetTopAndBottom(initCenterViewY
                                - changedView.getTop() + yOffsetStep * 2);
                        float scale = 1.0f - SCALE_STEP * 2;
                        changedView.setScaleX(scale);
                        changedView.setScaleY(scale);
                        //卡片View在ViewGroup中的顺次调整
                        for (int i = viewList.size() - 1; i > 0; i--) {
                            View tempView = viewList.get(i);
                            tempView.bringToFront();
                        }
                        viewList.remove(changedView);
                        viewList.add(changedView);
                        releasedViewList.remove(0);
                    }
                }
            }
            //填充新数据
            this.dataList = list;
            for (int i = 0; i < viewList.size(); i++) {
                CardItemView itemView = viewList.get(i);
                if (i < dataList.size()) {
                    itemView.fillData(dataList.get(i));
                    itemView.setVisibility(View.VISIBLE);
                } else {
                    itemView.setVisibility(View.INVISIBLE);
                }
            }
            drawShaderLayer();
            if (null != cardSwitchListener) {
                cardSwitchListener.onShow(viewList.get(isShowItem), isShowItem);
            }
        } else {
            //无数据隐藏卡片
            for (int i = 0; i < viewList.size(); i++) {
                CardItemView itemView = viewList.get(i);
                itemView.setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * 手动触发卡片自动滑动
     */
    public void autoSlide(int type) {
        if (type == VANISH_TYPE_LEFT || type == VANISH_TYPE_RIGHT) {
            slideLock = true;
            vanishOnBtnClick(type);
        }
    }

    /**
     * 设置卡片操作回调
     *
     * @param cardSwitchListener 回调接口
     */
    public void setCardSwitchListener(CardSwitchListener cardSwitchListener) {
        this.cardSwitchListener = cardSwitchListener;
    }

    /**
     * 卡片回调接口
     */
    public interface CardSwitchListener {
        /**
         * 新卡片显示回调
         *
         * @param index 最顶层显示的卡片的index
         */
        public void onShow(View animateView, int index);

        /**
         * 卡片飞向两侧回调
         *
         * @param index 飞向两侧的卡片数据index
         * @param type  飞向哪一侧{@link #VANISH_TYPE_LEFT}或{@link #VANISH_TYPE_RIGHT}
         */
        public void onCardVanish(View animateView, int index, int type);

        /**
         * 卡片点击事件
         *
         * @param cardImageView 卡片上的图片view
         * @param index         点击到的index
         */
        public void onItemClick(View cardImageView, int index);


        public void onViewPosition(View changedView, float dx, float dy);
    }

    class MoveDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx,
                                float dy) {
            // 拖动了，touch不往下传递
            return Math.abs(dy) + Math.abs(dx) > 5;
        }
    }

    /**
     * 这是viewdraghelper拖拽效果的主要逻辑
     */
    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public void onViewPositionChanged(View changedView, int left, int top,
                                          int dx, int dy) {
            if (cardSwitchListener != null) {
                float y = (initCenterViewX - changedView.getLeft()) / ((float) changedView.getMeasuredWidth() / 2);
                float x = -y;
                cardSwitchListener.onViewPosition(changedView, x, y);
            }
            // 调用offsetLeftAndRight导致viewPosition改变，会调到此处，所以此处对index做保护处理
            int index = viewList.indexOf(changedView);
            if (index + 2 > viewList.size()) {
                return;
            }
            processLinkageView(changedView);
        }

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            // 如果数据List为空，或者子View不可见，则不予处理
            if (dataList == null || dataList.size() == 0
                    || child.getVisibility() != View.VISIBLE || child.getScaleX() <= 1.0f - SCALE_STEP) {
                // 一般来讲，如果拖动的是第三层、或者第四层的View，则直接禁止
                // 此处用getScale的用法来巧妙回避
                return false;
            }
            if (slideLock) {
                return false;
            }
            // 只捕获顶部view(rotation=0)
            int childIndex = viewList.indexOf(child);
            if (childIndex > 0) {
                return false;
            }
            return true;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            // 这个用来控制拖拽过程中松手后，自动滑行的速度
            return AUTO_SLIDE_SPEED;
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            animToSide(releasedChild, xvel, yvel);
            releasedChild.findViewById(R.id.card_item_dislike_iv).setAlpha(0);
            releasedChild.findViewById(R.id.card_item_like_iv).setAlpha(0);
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return left;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }
    }

}
