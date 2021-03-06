/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.list;

import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.R;
import com.android.contacts.widget.TextWithHighlighting;
import com.android.contacts.widget.TextWithHighlightingFactory;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

/**
 * A custom view for an item in the contact list.
 */
public class ContactListItemView extends ViewGroup {

    private static final int QUICK_CONTACT_BADGE_STYLE =
            com.android.internal.R.attr.quickContactBadgeStyleWindowMedium;

    protected final Context mContext;

    private final int mPreferredHeight;
    private final int mVerticalDividerMargin;
    private final int mPaddingTop;
    private final int mPaddingRight;
    private final int mPaddingBottom;
    private final int mPaddingLeft;
    private final int mGapBetweenImageAndText;
    private final int mGapBetweenLabelAndData;
    private final int mCallButtonPadding;
    private final int mPresenceIconMargin;
    private final int mHeaderTextWidth;

    private boolean mHorizontalDividerVisible = true;
    private Drawable mHorizontalDividerDrawable;
    private int mHorizontalDividerHeight;

    private boolean mVerticalDividerVisible;
    private Drawable mVerticalDividerDrawable;
    private int mVerticalDividerWidth;

    private boolean mHeaderVisible;
    private Drawable mHeaderBackgroundDrawable;
    private int mHeaderBackgroundHeight;
    private TextView mHeaderTextView;

    private QuickContactBadge mQuickContact;
    private ImageView mPhotoView;
    private TextView mNameTextView;
    private TextView mPhoneticNameTextView;
    private DontPressWithParentImageView mCallButton;
    private TextView mLabelView;
    private TextView mDataView;
    private TextView mSnippetView;
    private ImageView mPresenceIcon;

    private int mPhotoViewWidth;
    private int mPhotoViewHeight;
    private int mLine1Height;
    private int mLine2Height;
    private int mLine3Height;
    private int mLine4Height;

    private OnClickListener mCallButtonClickListener;
    private TextWithHighlightingFactory mTextWithHighlightingFactory;
    public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
    public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
    public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
    public TextWithHighlighting textWithHighlighting;
    public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);

    private CharSequence mUnknownNameText;

    /**
     * Special class to allow the parent to be pressed without being pressed itself.
     * This way the line of a tab can be pressed, but the image itself is not.
     */
    // TODO: understand this
    private static class DontPressWithParentImageView extends ImageView {

        public DontPressWithParentImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void setPressed(boolean pressed) {
            // If the parent is pressed, do not set to pressed.
            if (pressed && ((View) getParent()).isPressed()) {
                return;
            }
            super.setPressed(pressed);
        }
    }

    public ContactListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        // Obtain preferred item height from the current theme
        TypedArray a = context.obtainStyledAttributes(null, com.android.internal.R.styleable.Theme);
        mPreferredHeight =
                a.getDimensionPixelSize(android.R.styleable.Theme_listPreferredItemHeight, 0);
        a.recycle();

        Resources resources = context.getResources();
        mVerticalDividerMargin =
                resources.getDimensionPixelOffset(R.dimen.list_item_vertical_divider_margin);
        mPaddingTop =
                resources.getDimensionPixelOffset(R.dimen.list_item_padding_top);
        mPaddingBottom =
                resources.getDimensionPixelOffset(R.dimen.list_item_padding_bottom);
        mPaddingLeft =
                resources.getDimensionPixelOffset(R.dimen.list_item_padding_left);
        mPaddingRight =
                resources.getDimensionPixelOffset(R.dimen.list_item_padding_right);
        mGapBetweenImageAndText =
                resources.getDimensionPixelOffset(R.dimen.list_item_gap_between_image_and_text);
        mGapBetweenLabelAndData =
                resources.getDimensionPixelOffset(R.dimen.list_item_gap_between_label_and_data);
        mCallButtonPadding =
                resources.getDimensionPixelOffset(R.dimen.list_item_call_button_padding);
        mPresenceIconMargin =
                resources.getDimensionPixelOffset(R.dimen.list_item_presence_icon_margin);
        mHeaderTextWidth =
                resources.getDimensionPixelOffset(R.dimen.list_item_header_text_width);
    }

    /**
     * Installs a call button listener.
     */
    public void setOnCallButtonClickListener(OnClickListener callButtonClickListener) {
        mCallButtonClickListener = callButtonClickListener;
    }

    public void setTextWithHighlightingFactory(TextWithHighlightingFactory factory) {
        mTextWithHighlightingFactory = factory;
    }

    public void setUnknownNameText(CharSequence unknownNameText) {
        mUnknownNameText = unknownNameText;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We will match parent's width and wrap content vertically, but make sure
        // height is no less than listPreferredItemHeight.
        int width = resolveSize(0, widthMeasureSpec);
        int height = 0;

        mLine1Height = 0;
        mLine2Height = 0;
        mLine3Height = 0;
        mLine4Height = 0;

        // Obtain the natural dimensions of the name text (we only care about height)
        if (isVisible(mNameTextView)) {
            mNameTextView.measure(0, 0);
            mLine1Height = mNameTextView.getMeasuredHeight();
        }

        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.measure(0, 0);
            mLine2Height = mPhoneticNameTextView.getMeasuredHeight();
        }

        if (isVisible(mLabelView)) {
            mLabelView.measure(0, 0);
            mLine3Height = mLabelView.getMeasuredHeight();
        }

        if (isVisible(mDataView)) {
            mDataView.measure(0, 0);
            mLine3Height = Math.max(mLine3Height, mDataView.getMeasuredHeight());
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.measure(0, 0);
            mLine4Height = mSnippetView.getMeasuredHeight();
        }

        height += mLine1Height + mLine2Height + mLine3Height + mLine4Height
                + mPaddingTop + mPaddingBottom;

        if (isVisible(mCallButton)) {
            mCallButton.measure(0, 0);
        }
        if (isVisible(mPresenceIcon)) {
            mPresenceIcon.measure(0, 0);
        }

        ensurePhotoViewSize();

        height = Math.max(height, mPhotoViewHeight);
        height = Math.max(height, mPreferredHeight);

        if (mHeaderVisible) {
            ensureHeaderBackground();
            mHeaderTextView.measure(
                    MeasureSpec.makeMeasureSpec(mHeaderTextWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mHeaderBackgroundHeight, MeasureSpec.EXACTLY));
            height += mHeaderBackgroundHeight;
        }

        if (mHorizontalDividerVisible) {
            ensureHorizontalDivider();
            height += mHorizontalDividerHeight;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        // Determine the vertical bounds by laying out the header first.
        int topBound = 0;

        if (mHeaderVisible) {
            mHeaderBackgroundDrawable.setBounds(
                    0,
                    0,
                    width,
                    mHeaderBackgroundHeight);
            mHeaderTextView.layout(0, 0, width, mHeaderBackgroundHeight);
            topBound += mHeaderBackgroundHeight;
        }

        // Positions of views on the left are fixed and so are those on the right side.
        // The stretchable part of the layout is in the middle.  So, we will start off
        // by laying out the left and right sides. Then we will allocate the remainder
        // to the text fields in the middle.

        int leftBound = layoutLeftSide(height, topBound, mPaddingLeft);
        int rightBound = layoutRightSide(height, topBound, right);

        if (mHorizontalDividerVisible) {
            ensureHorizontalDivider();
            mHorizontalDividerDrawable.setBounds(
                    0,
                    height - mHorizontalDividerHeight,
                    width,
                    height);
        }

        topBound += mPaddingTop;
        int bottomBound = height - mPaddingBottom;

        // Text lines, centered vertically
        rightBound -= mPaddingRight;

        // Center text vertically
        int totalTextHeight = mLine1Height + mLine2Height + mLine3Height + mLine4Height;
        int textTopBound = (bottomBound + topBound - totalTextHeight) / 2;

        if (isVisible(mNameTextView)) {
            mNameTextView.layout(leftBound,
                    textTopBound,
                    rightBound,
                    textTopBound + mLine1Height);
        }

        int dataLeftBound = leftBound;
        if (isVisible(mPhoneticNameTextView)) {
            mPhoneticNameTextView.layout(leftBound,
                    textTopBound + mLine1Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height);
        }

        if (isVisible(mLabelView)) {
            dataLeftBound = leftBound + mLabelView.getMeasuredWidth();
            mLabelView.layout(leftBound,
                    textTopBound + mLine1Height + mLine2Height,
                    dataLeftBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height);
            dataLeftBound += mGapBetweenLabelAndData;
        }

        if (isVisible(mDataView)) {
            mDataView.layout(dataLeftBound,
                    textTopBound + mLine1Height + mLine2Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height);
        }

        if (isVisible(mSnippetView)) {
            mSnippetView.layout(leftBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height,
                    rightBound,
                    textTopBound + mLine1Height + mLine2Height + mLine3Height + mLine4Height);
        }
    }

    /**
     * Performs layout of the left side of the view
     *
     * @return new left boundary
     */
    protected int layoutLeftSide(int height, int topBound, int leftBound) {
        View photoView = mQuickContact != null ? mQuickContact : mPhotoView;
        if (photoView != null) {
            // Center the photo vertically
            int photoTop = topBound + (height - topBound - mPhotoViewHeight) / 2;
            photoView.layout(
                    leftBound,
                    photoTop,
                    leftBound + mPhotoViewWidth,
                    photoTop + mPhotoViewHeight);
            leftBound += mPhotoViewWidth + mGapBetweenImageAndText;
        }
        return leftBound;
    }

    /**
     * Performs layout of the right side of the view
     *
     * @return new right boundary
     */
    protected int layoutRightSide(int height, int topBound, int rightBound) {
        if (isVisible(mCallButton)) {
            int buttonWidth = mCallButton.getMeasuredWidth();
            rightBound -= buttonWidth;
            mCallButton.layout(
                    rightBound,
                    topBound,
                    rightBound + buttonWidth,
                    height - mHorizontalDividerHeight);
            mVerticalDividerVisible = true;
            ensureVerticalDivider();
            rightBound -= mVerticalDividerWidth;
            mVerticalDividerDrawable.setBounds(
                    rightBound,
                    topBound + mVerticalDividerMargin,
                    rightBound + mVerticalDividerWidth,
                    height - mVerticalDividerMargin);
        } else {
            mVerticalDividerVisible = false;
        }

        if (isVisible(mPresenceIcon)) {
            int iconWidth = mPresenceIcon.getMeasuredWidth();
            rightBound -= mPresenceIconMargin + iconWidth;
            mPresenceIcon.layout(
                    rightBound,
                    topBound,
                    rightBound + iconWidth,
                    height);
        }
        return rightBound;
    }

    protected boolean isVisible(View view) {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /**
     * Loads the drawable for the vertical divider if it has not yet been loaded.
     */
    private void ensureVerticalDivider() {
        if (mVerticalDividerDrawable == null) {
            mVerticalDividerDrawable = mContext.getResources().getDrawable(
                    R.drawable.divider_vertical_dark);
            mVerticalDividerWidth = mVerticalDividerDrawable.getIntrinsicWidth();
        }
    }

    /**
     * Loads the drawable for the horizontal divider if it has not yet been loaded.
     */
    private void ensureHorizontalDivider() {
        if (mHorizontalDividerDrawable == null) {
            mHorizontalDividerDrawable = mContext.getResources().getDrawable(
                    com.android.internal.R.drawable.divider_horizontal_dark_opaque);
            mHorizontalDividerHeight = mHorizontalDividerDrawable.getIntrinsicHeight();
        }
    }

    /**
     * Loads the drawable for the header background if it has not yet been loaded.
     */
    private void ensureHeaderBackground() {
        if (mHeaderBackgroundDrawable == null) {
            mHeaderBackgroundDrawable = mContext.getResources().getDrawable(
                    android.R.drawable.dark_header);
            mHeaderBackgroundHeight = mHeaderBackgroundDrawable.getIntrinsicHeight();
        }
    }

    /**
     * Extracts width and height from the style
     */
    private void ensurePhotoViewSize() {
        if (mPhotoViewWidth == 0 && mPhotoViewHeight == 0) {
            TypedArray a = mContext.obtainStyledAttributes(null,
                    com.android.internal.R.styleable.ViewGroup_Layout,
                    QUICK_CONTACT_BADGE_STYLE, 0);
            mPhotoViewWidth = a.getLayoutDimension(
                    android.R.styleable.ViewGroup_Layout_layout_width,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            mPhotoViewHeight = a.getLayoutDimension(
                    android.R.styleable.ViewGroup_Layout_layout_height,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            a.recycle();
        }
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        if (mHeaderVisible) {
            mHeaderBackgroundDrawable.draw(canvas);
        }
        if (mHorizontalDividerVisible) {
            mHorizontalDividerDrawable.draw(canvas);
        }
        if (mVerticalDividerVisible) {
            mVerticalDividerDrawable.draw(canvas);
        }
        super.dispatchDraw(canvas);
    }

    /**
     * Sets the flag that determines whether a divider should drawn at the bottom
     * of the view.
     */
    public void setDividerVisible(boolean visible) {
        mHorizontalDividerVisible = visible;
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeader(String title) {
        if (!TextUtils.isEmpty(title)) {
            if (mHeaderTextView == null) {
                mHeaderTextView = new TextView(mContext);
                mHeaderTextView.setTypeface(mHeaderTextView.getTypeface(), Typeface.BOLD);
                mHeaderTextView.setTextColor(mContext.getResources()
                        .getColor(com.android.internal.R.color.dim_foreground_dark));
                mHeaderTextView.setTextSize(14);
                mHeaderTextView.setGravity(Gravity.CENTER);
                addView(mHeaderTextView);
            }
            mHeaderTextView.setText(title);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mHeaderVisible = true;
        } else {
            if (mHeaderTextView != null) {
                mHeaderTextView.setVisibility(View.GONE);
            }
            mHeaderVisible = false;
        }
    }

    /**
     * Returns the quick contact badge, creating it if necessary.
     */
    public QuickContactBadge getQuickContact() {
        if (mQuickContact == null) {
            mQuickContact = new QuickContactBadge(mContext, null, QUICK_CONTACT_BADGE_STYLE);
            mQuickContact.setExcludeMimes(new String[] { Contacts.CONTENT_ITEM_TYPE });
            addView(mQuickContact);
        }
        return mQuickContact;
    }

    /**
     * Returns the photo view, creating it if necessary.
     */
    public ImageView getPhotoView() {
        if (mPhotoView == null) {
            mPhotoView = new ImageView(mContext, null, QUICK_CONTACT_BADGE_STYLE);
            // Quick contact style used above will set a background - remove it
            mPhotoView.setBackgroundDrawable(null);
            addView(mPhotoView);
        }
        return mPhotoView;
    }

    /**
     * Removes the photo view.  Should not be needed once we start handling different
     * types of views as different types of views from the List's perspective.
     *
     * @deprecated
     */
    @Deprecated
    public void removePhotoView() {
        if (mPhotoView != null) {
            removeView(mPhotoView);
            mPhotoView = null;
        }
        if (mQuickContact != null) {
            removeView(mQuickContact);
            mQuickContact = null;
        }
    }

    /**
     * Returns the text view for the contact name, creating it if necessary.
     */
    public TextView getNameTextView() {
        if (mNameTextView == null) {
            mNameTextView = new TextView(mContext);
            mNameTextView.setSingleLine(true);
            mNameTextView.setEllipsize(TruncateAt.MARQUEE);
            mNameTextView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
            mNameTextView.setGravity(Gravity.CENTER_VERTICAL);
            addView(mNameTextView);
        }
        return mNameTextView;
    }

    /**
     * Adds a call button using the supplied arguments as an id and tag.
     */
    public void showCallButton(int id, int tag) {
        if (mCallButton == null) {
            mCallButton = new DontPressWithParentImageView(mContext, null);
            mCallButton.setId(id);
            mCallButton.setOnClickListener(mCallButtonClickListener);
            mCallButton.setBackgroundResource(R.drawable.call_background);
            mCallButton.setImageResource(android.R.drawable.sym_action_call);
            mCallButton.setPadding(mCallButtonPadding, 0, mCallButtonPadding, 0);
            mCallButton.setScaleType(ScaleType.CENTER);
            addView(mCallButton);
        }

        mCallButton.setTag(tag);
        mCallButton.setVisibility(View.VISIBLE);
    }

    public void hideCallButton() {
        if (mCallButton != null) {
            mCallButton.setVisibility(View.GONE);
        }
    }

    /**
     * Adds or updates a text view for the phonetic name.
     */
    public void setPhoneticName(char[] text, int size) {
        if (text == null || size == 0) {
            if (mPhoneticNameTextView != null) {
                mPhoneticNameTextView.setVisibility(View.GONE);
            }
        } else {
            getPhoneticNameTextView();
            mPhoneticNameTextView.setText(text, 0, size);
            mPhoneticNameTextView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the phonetic name, creating it if necessary.
     */
    public TextView getPhoneticNameTextView() {
        if (mPhoneticNameTextView == null) {
            mPhoneticNameTextView = new TextView(mContext);
            mPhoneticNameTextView.setSingleLine(true);
            mPhoneticNameTextView.setEllipsize(TruncateAt.MARQUEE);
            mPhoneticNameTextView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mPhoneticNameTextView.setTypeface(mPhoneticNameTextView.getTypeface(), Typeface.BOLD);
            addView(mPhoneticNameTextView);
        }
        return mPhoneticNameTextView;
    }

    /**
     * Adds or updates a text view for the data label.
     */
    public void setLabel(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mLabelView != null) {
                mLabelView.setVisibility(View.GONE);
            }
        } else {
            getLabelView();
            mLabelView.setText(text);
            mLabelView.setVisibility(VISIBLE);
        }
    }

    /**
     * Adds or updates a text view for the data label.
     */
    public void setLabel(char[] text, int size) {
        if (text == null || size == 0) {
            if (mLabelView != null) {
                mLabelView.setVisibility(View.GONE);
            }
        } else {
            getLabelView();
            mLabelView.setText(text, 0, size);
            mLabelView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the data label, creating it if necessary.
     */
    public TextView getLabelView() {
        if (mLabelView == null) {
            mLabelView = new TextView(mContext);
            mLabelView.setSingleLine(true);
            mLabelView.setEllipsize(TruncateAt.MARQUEE);
            mLabelView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mLabelView.setTypeface(mLabelView.getTypeface(), Typeface.BOLD);
            addView(mLabelView);
        }
        return mLabelView;
    }

    /**
     * Adds or updates a text view for the data element.
     */
    public void setData(char[] text, int size) {
        if (text == null || size == 0) {
            if (mDataView != null) {
                mDataView.setVisibility(View.GONE);
            }
            return;
        } else {
            getDataView();
            mDataView.setText(text, 0, size);
            mDataView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the data text, creating it if necessary.
     */
    public TextView getDataView() {
        if (mDataView == null) {
            mDataView = new TextView(mContext);
            mDataView.setSingleLine(true);
            mDataView.setEllipsize(TruncateAt.MARQUEE);
            mDataView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            addView(mDataView);
        }
        return mDataView;
    }

    /**
     * Adds or updates a text view for the search snippet.
     */
    public void setSnippet(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            if (mSnippetView != null) {
                mSnippetView.setVisibility(View.GONE);
            }
        } else {
            getSnippetView();
            mSnippetView.setText(text);
            mSnippetView.setVisibility(VISIBLE);
        }
    }

    /**
     * Returns the text view for the search snippet, creating it if necessary.
     */
    public TextView getSnippetView() {
        if (mSnippetView == null) {
            mSnippetView = new TextView(mContext);
            mSnippetView.setSingleLine(true);
            mSnippetView.setEllipsize(TruncateAt.MARQUEE);
            mSnippetView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
            mSnippetView.setTypeface(mSnippetView.getTypeface(), Typeface.BOLD);
            addView(mSnippetView);
        }
        return mSnippetView;
    }

    /**
     * Adds or updates the presence icon view.
     */
    public void setPresence(Drawable icon) {
        if (icon != null) {
            if (mPresenceIcon == null) {
                mPresenceIcon = new ImageView(mContext);
                addView(mPresenceIcon);
            }
            mPresenceIcon.setImageDrawable(icon);
            mPresenceIcon.setScaleType(ScaleType.CENTER);
            mPresenceIcon.setVisibility(View.VISIBLE);
        } else {
            if (mPresenceIcon != null) {
                mPresenceIcon.setVisibility(View.GONE);
            }
        }
    }

    public void showDisplayName(Cursor cursor, int nameColumnIndex, boolean highlightingEnabled,
            int alternativeNameColumnIndex) {
        cursor.copyStringToBuffer(nameColumnIndex, nameBuffer);
        TextView nameView = getNameTextView();
        int size = nameBuffer.sizeCopied;
        if (size != 0) {
            if (highlightingEnabled) {
                if (textWithHighlighting == null) {
                    textWithHighlighting =
                            mTextWithHighlightingFactory.createTextWithHighlighting();
                }
                cursor.copyStringToBuffer(alternativeNameColumnIndex, highlightedTextBuffer);
                textWithHighlighting.setText(nameBuffer, highlightedTextBuffer);
                nameView.setText(textWithHighlighting);
            } else {
                nameView.setText(nameBuffer.data, 0, size);
            }
        } else {
            nameView.setText(mUnknownNameText);
        }
    }

    public void showPhoneticName(Cursor cursor, int phoneticNameColumnIndex) {
        cursor.copyStringToBuffer(phoneticNameColumnIndex, phoneticNameBuffer);
        int phoneticNameSize = phoneticNameBuffer.sizeCopied;
        if (phoneticNameSize != 0) {
            setPhoneticName(phoneticNameBuffer.data, phoneticNameSize);
        } else {
            setPhoneticName(null, 0);
        }
    }

    /**
     * Sets the proper icon (star or presence or nothing)
     */
    public void showPresence(Cursor cursor, int presenceColumnIndex) {
        int serverStatus;
        if (!cursor.isNull(presenceColumnIndex)) {
            serverStatus = cursor.getInt(presenceColumnIndex);

            // TODO consider caching these drawables
            Drawable icon = ContactPresenceIconUtil.getPresenceIcon(getContext(), serverStatus);
            if (icon != null) {
                setPresence(icon);
            } else {
                setPresence(null);
            }
        } else {
            setPresence(null);
        }
    }

    /**
     * Shows search snippet.
     */
    public void showSnippet(Cursor cursor, int summarySnippetMimetypeColumnIndex,
            int summarySnippetData1ColumnIndex, int summarySnippetData4ColumnIndex) {
        String snippet = null;
        String snippetMimeType = cursor.getString(summarySnippetMimetypeColumnIndex);
        if (Email.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String email = cursor.getString(summarySnippetData1ColumnIndex);
            if (!TextUtils.isEmpty(email)) {
                snippet = email;
            }
        } else if (Organization.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String company = cursor.getString(summarySnippetData1ColumnIndex);
            String title = cursor.getString(summarySnippetData4ColumnIndex);
            if (!TextUtils.isEmpty(company)) {
                if (!TextUtils.isEmpty(title)) {
                    snippet = company + " / " + title;
                } else {
                    snippet = company;
                }
            } else if (!TextUtils.isEmpty(title)) {
                snippet = title;
            }
        } else if (Nickname.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
            String nickname = cursor.getString(summarySnippetData1ColumnIndex);
            if (!TextUtils.isEmpty(nickname)) {
                snippet = nickname;
            }
        }

        setSnippet(snippet);
    }

    /**
     * Shows data element (e.g. phone number).
     */
    public void showData(Cursor cursor, int dataColumnIndex) {
        cursor.copyStringToBuffer(dataColumnIndex, dataBuffer);
        setData(dataBuffer.data, dataBuffer.sizeCopied);
    }
}
