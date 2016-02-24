/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.support.v7.widget;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v4.util.LongSparseArray;
import android.support.v7.widget.TestedFrameLayout.FullControlLayoutParams;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class to test any generic wrap content behavior.
 * It does so by running the same view scenario twice. Once with match parent setup to record all
 * dimensions and once with wrap_content setup. Then compares all child locations & ids +
 * RecyclerView size.
 */
abstract public class BaseWrapContentTest extends BaseRecyclerViewInstrumentationTest {

    static final boolean DEBUG = false;
    static final String TAG = "WrapContentTest";
    RecyclerView.LayoutManager mLayoutManager;

    TestAdapter mTestAdapter;

    LoggingItemAnimator mLoggingItemAnimator;

    boolean mIsWrapContent;

    protected final WrapContentConfig mWrapContentConfig;

    public BaseWrapContentTest(WrapContentConfig config) {
        mWrapContentConfig = config;
    }

    abstract RecyclerView.LayoutManager createLayoutManager();

    protected void testScenerio(Scenario scenario) throws Throwable {
        FullControlLayoutParams matchParent = new FullControlLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        FullControlLayoutParams wrapContent = new FullControlLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (mWrapContentConfig.isUnlimitedHeight()) {
            wrapContent.hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }
        if (mWrapContentConfig.isUnlimitedWidth()) {
            wrapContent.wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        }

        mIsWrapContent = false;
        List<Snapshot> s1 = runScenario(scenario, matchParent, null);
        mIsWrapContent = true;

        List<Snapshot> s2 = runScenario(scenario, wrapContent, s1);
        assertEquals("test sanity", s1.size(), s2.size());

        for (int i = 0; i < s1.size(); i++) {
            Snapshot step1 = s1.get(i);
            Snapshot step2 = s2.get(i);
            step1.assertSame(step2, i);
        }
    }

    public List<Snapshot> runScenario(Scenario scenario, ViewGroup.LayoutParams lp,
            @Nullable List<Snapshot> compareWith)
            throws Throwable {
        removeRecyclerView();
        Item.idCounter.set(0);
        List<Snapshot> result = new ArrayList<>();
        RecyclerView.LayoutManager layoutManager = scenario.createLayoutManager();
        WrappedRecyclerView recyclerView = new WrappedRecyclerView(getActivity());
        recyclerView.setBackgroundColor(Color.rgb(0, 0, 255));
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setLayoutParams(lp);
        mLayoutManager = layoutManager;
        mTestAdapter = new TestAdapter(scenario.getSeedAdapterSize());
        recyclerView.setAdapter(mTestAdapter);
        mLoggingItemAnimator = new LoggingItemAnimator();
        recyclerView.setItemAnimator(mLoggingItemAnimator);
        setRecyclerView(recyclerView);
        recyclerView.waitUntilLayout();
        int stepIndex = 0;
        for (Step step : scenario.mStepList) {
            mLoggingItemAnimator.reset();
            step.onRun();
            recyclerView.waitUntilLayout();
            recyclerView.waitUntilAnimations();
            Snapshot snapshot = takeSnapshot();
            if (mIsWrapContent) {
                snapshot.assertRvSize();
            }
            result.add(snapshot);
            if (compareWith != null) {
                compareWith.get(stepIndex).assertSame(snapshot, stepIndex);
            }
            stepIndex++;
        }
        recyclerView.waitUntilLayout();
        recyclerView.waitUntilAnimations();
        Snapshot snapshot = takeSnapshot();
        if (mIsWrapContent) {
            snapshot.assertRvSize();
        }
        result.add(snapshot);
        if (compareWith != null) {
            compareWith.get(stepIndex).assertSame(snapshot, stepIndex);
        }
        return result;
    }

    void layoutAndCheck(TestedFrameLayout.FullControlLayoutParams lp,
            BaseWrapContentWithAspectRatioTest.WrapContentAdapter adapter, Rect[] expected,
            int width, int height) throws Throwable {
        WrappedRecyclerView recyclerView = new WrappedRecyclerView(getActivity());
        recyclerView.setBackgroundColor(Color.rgb(0, 0, 255));
        recyclerView.setLayoutManager(createLayoutManager());
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutParams(lp);
        Rect padding = mWrapContentConfig.padding;
        recyclerView.setPadding(padding.left, padding.top,
                padding.right, padding.bottom);
        setRecyclerView(recyclerView);
        recyclerView.waitUntilLayout();
        Snapshot snapshot = takeSnapshot();
        int index = 0;
        Rect tmp = new Rect();
        for (BaseWrapContentWithAspectRatioTest.MeasureBehavior behavior : adapter.behaviors) {
            tmp.set(expected[index]);
            tmp.offset(padding.left, padding.top);
            assertThat("behavior " + index, snapshot.mChildCoordinates.get(behavior.getId()),
                    is(tmp));
            index ++;
        }
        Rect boundingBox = new Rect(0, 0, 0, 0);
        for (Rect rect : expected) {
            boundingBox.union(rect);
        }
        assertThat(recyclerView.getWidth(), is(width + padding.left + padding.right));
        assertThat(recyclerView.getHeight(), is(height + padding.top + padding.bottom));
    }


    abstract protected int getVerticalGravity(RecyclerView.LayoutManager layoutManager);

    abstract protected int getHorizontalGravity(RecyclerView.LayoutManager layoutManager);

    protected Snapshot takeSnapshot() throws Throwable {
        Snapshot snapshot = new Snapshot(mRecyclerView, mLoggingItemAnimator,
                getHorizontalGravity(mLayoutManager), getVerticalGravity(mLayoutManager));
        return snapshot;
    }

    abstract class Scenario {

        ArrayList<Step> mStepList = new ArrayList<>();

        public Scenario(Step... steps) {
            Collections.addAll(mStepList, steps);
        }

        public int getSeedAdapterSize() {
            return 10;
        }

        public RecyclerView.LayoutManager createLayoutManager() {
            return BaseWrapContentTest.this.createLayoutManager();
        }
    }

    abstract static class Step {

        abstract void onRun() throws Throwable;
    }

    class Snapshot {

        Rect mRawChildrenBox = new Rect();

        Rect mRvSize = new Rect();

        Rect mRvPadding = new Rect();

        Rect mRvParentSize = new Rect();

        LongSparseArray<Rect> mChildCoordinates = new LongSparseArray<>();

        LongSparseArray<String> mAppear = new LongSparseArray<>();

        LongSparseArray<String> mDisappear = new LongSparseArray<>();

        LongSparseArray<String> mPersistent = new LongSparseArray<>();

        LongSparseArray<String> mChanged = new LongSparseArray<>();

        int mVerticalGravity;

        int mHorizontalGravity;

        int mOffsetX, mOffsetY;// how much we should offset children

        public Snapshot(RecyclerView recyclerView, LoggingItemAnimator loggingItemAnimator,
                int horizontalGravity, int verticalGravity)
                throws Throwable {
            mRvSize = getViewBounds(recyclerView);
            mRvParentSize = getViewBounds((View) recyclerView.getParent());
            mRvPadding = new Rect(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(), recyclerView.getPaddingBottom());
            mVerticalGravity = verticalGravity;
            mHorizontalGravity = horizontalGravity;
            if (mVerticalGravity == Gravity.TOP) {
                mOffsetY = 0;
            } else {
                mOffsetY = mRvParentSize.bottom - mRvSize.bottom;
            }

            if (mHorizontalGravity == Gravity.LEFT) {
                mOffsetX = 0;
            } else {
                mOffsetX = mRvParentSize.right - mRvSize.right;
            }
            collectChildCoordinates(recyclerView);
            if (loggingItemAnimator != null) {
                collectInto(mAppear, loggingItemAnimator.mAnimateAppearanceList);
                collectInto(mDisappear, loggingItemAnimator.mAnimateDisappearanceList);
                collectInto(mPersistent, loggingItemAnimator.mAnimatePersistenceList);
                collectInto(mChanged, loggingItemAnimator.mAnimateChangeList);
            }
        }

        public boolean doesChildrenFitVertically() {
            return mRawChildrenBox.top >= mRvPadding.top
                    && mRawChildrenBox.bottom <= mRvSize.bottom - mRvPadding.bottom;
        }

        public boolean doesChildrenFitHorizontally() {
            return mRawChildrenBox.left >= mRvPadding.left
                    && mRawChildrenBox.right <= mRvSize.right - mRvPadding.right;
        }

        public void assertSame(Snapshot other, int step) {
            if (mWrapContentConfig.isUnlimitedHeight() &&
                    (!doesChildrenFitVertically() || !other.doesChildrenFitVertically())) {
                if (DEBUG) {
                    Log.d(TAG, "cannot assert coordinates because it does not fit vertically");
                }
                return;
            }
            if (mWrapContentConfig.isUnlimitedWidth() &&
                    (!doesChildrenFitHorizontally() || !other.doesChildrenFitHorizontally())) {
                if (DEBUG) {
                    Log.d(TAG, "cannot assert coordinates because it does not fit horizontally");
                }
                return;
            }
            assertMap("child coordinates. step:" + step, mChildCoordinates,
                    other.mChildCoordinates);
            if (mWrapContentConfig.isUnlimitedHeight() || mWrapContentConfig.isUnlimitedWidth()) {
                return;//cannot assert animatinos in unlimited size
            }
            assertMap("appearing step:" + step, mAppear, other.mAppear);
            assertMap("disappearing step:" + step, mDisappear, other.mDisappear);
            assertMap("persistent step:" + step, mPersistent, other.mPersistent);
            assertMap("changed step:" + step, mChanged, other.mChanged);
        }

        private void assertMap(String prefix, LongSparseArray<?> map1, LongSparseArray<?> map2) {
            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append(prefix).append("\n");
            logBuilder.append("map1").append("\n");
            logInto(map1, logBuilder);
            logBuilder.append("map2").append("\n");
            logInto(map2, logBuilder);
            final String log = logBuilder.toString();
            assertEquals(log + " same size", map1.size(), map2.size());
            for (int i = 0; i < map1.size(); i++) {
                assertAtIndex(log, map1, map2, i);
            }
        }

        private void assertAtIndex(String prefix, LongSparseArray<?> map1, LongSparseArray<?> map2,
                int index) {
            long key1 = map1.keyAt(index);
            long key2 = map2.keyAt(index);
            assertEquals(prefix + "key mismatch at index " + index, key1, key2);
            Object value1 = map1.valueAt(index);
            Object value2 = map2.valueAt(index);
            assertEquals(prefix + " value mismatch at index " + index, value1, value2);
        }

        private void logInto(LongSparseArray<?> map, StringBuilder sb) {
            for (int i = 0; i < map.size(); i++) {
                long key = map.keyAt(i);
                Object value = map.valueAt(i);
                sb.append(key).append(" : ").append(value).append("\n");
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Snapshot{\n");
            sb.append("child coordinates:\n");
            logInto(mChildCoordinates, sb);
            sb.append("appear animations:\n");
            logInto(mAppear, sb);
            sb.append("disappear animations:\n");
            logInto(mDisappear, sb);
            sb.append("change animations:\n");
            logInto(mChanged, sb);
            sb.append("persistent animations:\n");
            logInto(mPersistent, sb);
            sb.append("}");
            return sb.toString();
        }

        @Override
        public int hashCode() {
            int result = mChildCoordinates.hashCode();
            result = 31 * result + mAppear.hashCode();
            result = 31 * result + mDisappear.hashCode();
            result = 31 * result + mPersistent.hashCode();
            result = 31 * result + mChanged.hashCode();
            return result;
        }

        private void collectInto(
                LongSparseArray<String> target,
                List<? extends BaseRecyclerViewAnimationsTest.AnimateLogBase> list) {
            for (BaseRecyclerViewAnimationsTest.AnimateLogBase base : list) {
                long id = getItemId(base.viewHolder);
                assertNull(target.get(id));
                target.put(id, log(base));
            }
        }

        private String log(BaseRecyclerViewAnimationsTest.AnimateLogBase base) {
            return base.getClass().getSimpleName() +
                    ((TextView) base.viewHolder.itemView).getText() + ": " +
                    "[pre:" + log(base.postInfo) +
                    ", post:" + log(base.postInfo) + "]";
        }

        private String log(BaseRecyclerViewAnimationsTest.LoggingInfo postInfo) {
            if (postInfo == null) {
                return "?";
            }
            return "PI[flags: " + postInfo.changeFlags
                    + ",l:" + (postInfo.left + mOffsetX)
                    + ",t:" + (postInfo.top + mOffsetY)
                    + ",r:" + (postInfo.right + mOffsetX)
                    + ",b:" + (postInfo.bottom + mOffsetY) + "]";
        }

        void collectChildCoordinates(RecyclerView recyclerView) throws Throwable {
            mRawChildrenBox = new Rect(0, 0, 0, 0);
            final int childCount = recyclerView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = recyclerView.getChildAt(i);
                Rect childBounds = getChildBounds(recyclerView, child, true);
                mRawChildrenBox.union(getChildBounds(recyclerView, child, false));
                RecyclerView.ViewHolder childViewHolder = recyclerView.getChildViewHolder(child);
                mChildCoordinates.put(getItemId(childViewHolder), childBounds);
            }
        }

        private Rect getViewBounds(View view) {
            return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
        }

        private Rect getChildBounds(RecyclerView recyclerView, View child, boolean offset) {
            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) child.getLayoutParams();
            Rect rect = new Rect(layoutManager.getDecoratedLeft(child) - lp.leftMargin,
                    layoutManager.getDecoratedTop(child) - lp.topMargin,
                    layoutManager.getDecoratedRight(child) + lp.rightMargin,
                    layoutManager.getDecoratedBottom(child) + lp.bottomMargin);
            if (offset) {
                rect.offset(mOffsetX, mOffsetY);
            }
            return rect;
        }

        private long getItemId(RecyclerView.ViewHolder vh) {
            if (vh instanceof TestViewHolder) {
                return ((TestViewHolder) vh).mBoundItem.mId;
            } else if (vh instanceof BaseWrapContentWithAspectRatioTest.WrapContentViewHolder) {
                BaseWrapContentWithAspectRatioTest.WrapContentViewHolder casted =
                        (BaseWrapContentWithAspectRatioTest.WrapContentViewHolder) vh;
                return casted.mView.mBehavior.getId();
            } else {
                throw new IllegalArgumentException("i don't support any VH");
            }
        }

        public void assertRvSize() {
            if (shouldWrapContentHorizontally()) {
                int expectedW = mRawChildrenBox.width() + mRvPadding.left + mRvPadding.right;
                assertTrue(mRvSize.width() + " <= " + expectedW, mRvSize.width() <= expectedW);
            }
            if (shouldWrapContentVertically()) {
                int expectedH = mRawChildrenBox.height() + mRvPadding.top + mRvPadding.bottom;
                assertTrue(mRvSize.height() + "<=" + expectedH, mRvSize.height() <= expectedH);
            }
        }
    }

    protected boolean shouldWrapContentHorizontally() {
        return true;
    }

    protected boolean shouldWrapContentVertically() {
        return true;
    }

    static class WrappedRecyclerView extends RecyclerView {

        public WrappedRecyclerView(Context context) {
            super(context);
        }

        public void waitUntilLayout() {
            while (isLayoutRequested()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void waitUntilAnimations() {
            while (mItemAnimator != null && mItemAnimator.isRunning()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
        }
    }

    static class WrapContentConfig {

        public boolean unlimitedWidth;
        public boolean unlimitedHeight;
        public Rect padding = new Rect(0, 0, 0, 0);

        public WrapContentConfig(boolean unlimitedWidth, boolean unlimitedHeight) {
            this.unlimitedWidth = unlimitedWidth;
            this.unlimitedHeight = unlimitedHeight;
        }

        public WrapContentConfig(boolean unlimitedWidth, boolean unlimitedHeight, Rect padding) {
            this.unlimitedWidth = unlimitedWidth;
            this.unlimitedHeight = unlimitedHeight;
            this.padding.set(padding);
        }

        public boolean isUnlimitedWidth() {
            return unlimitedWidth;
        }

        public WrapContentConfig setUnlimitedWidth(boolean unlimitedWidth) {
            this.unlimitedWidth = unlimitedWidth;
            return this;
        }

        public boolean isUnlimitedHeight() {
            return unlimitedHeight;
        }

        public WrapContentConfig setUnlimitedHeight(boolean unlimitedHeight) {
            this.unlimitedHeight = unlimitedHeight;
            return this;
        }

        @Override
        public String toString() {
            return "WrapContentConfig{"
                    + "unlimitedWidth=" + unlimitedWidth
                    + ", unlimitedHeight=" + unlimitedHeight
                    + ", padding=" + padding
                    + '}';
        }

        public TestedFrameLayout.FullControlLayoutParams toLayoutParams(int wDim, int hDim) {
            TestedFrameLayout.FullControlLayoutParams
                    lp = new TestedFrameLayout.FullControlLayoutParams(
                    wDim, hDim);
            if (unlimitedWidth) {
                lp.wSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            }
            if (unlimitedHeight) {
                lp.hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            }
            return lp;
        }
    }
}