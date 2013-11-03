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

import com.android.contacts.R;

import android.app.patterns.CursorLoader;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

/**
 * A cursor adapter for the {@link ContactsContract.Contacts#CONTENT_TYPE} content type loading
 * a combination of starred and frequently contacted.
 */
public class StrequentContactListAdapter extends ContactListAdapter {

    private int mFrequentSeparatorPos;
    private TextView mSeparatorView;

    public StrequentContactListAdapter(Context context) {
        super(context);
    }

    @Override
    public void configureLoader(CursorLoader loader) {
        loader.setUri(Contacts.CONTENT_STREQUENT_URI);
        loader.setProjection(CONTACTS_SUMMARY_PROJECTION);
        if (getSortOrder() == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Contacts.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);

        // Get the split between starred and frequent items, if the mode is strequent
        mFrequentSeparatorPos = ListView.INVALID_POSITION;
        int count = 0;
        if (cursor != null && (count = cursor.getCount()) > 0) {
            cursor.moveToPosition(-1);
            for (int i = 0; cursor.moveToNext(); i++) {
                int starred = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
                if (starred == 0) {
                    if (i > 0) {
                        // Only add the separator when there are starred items present
                        mFrequentSeparatorPos = i;
                    }
                    break;
                }
            }
        }
    }

    @Override
    public int getCount() {
        if (mFrequentSeparatorPos == ListView.INVALID_POSITION) {
            return super.getCount();
        } else {
            // Add a row for the separator
            return super.getCount() + 1;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return mFrequentSeparatorPos == ListView.INVALID_POSITION;
    }

    @Override
    public boolean isEnabled(int position) {
        return position != mFrequentSeparatorPos;
    }

    @Override
    public Object getItem(int position) {
        if (position < mFrequentSeparatorPos) {
            return super.getItem(position);
        } else {
            return super.getItem(position - 1);
        }
    }

    @Override
    public long getItemId(int position) {
        if (position < mFrequentSeparatorPos) {
            return super.getItemId(position);
        } else {
            return super.getItemId(position - 1);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == mFrequentSeparatorPos) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        return super.getItemViewType(position);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position < mFrequentSeparatorPos) {
            return super.getView(position, convertView, parent);
        } else if (position == mFrequentSeparatorPos) {
            if (mSeparatorView == null) {
                mSeparatorView = (TextView)LayoutInflater.from(getContext()).
                        inflate(R.layout.list_separator, parent, false);
                mSeparatorView.setText(R.string.favoritesFrquentSeparator);
            }
            return mSeparatorView;
        } else {
            return super.getView(position - 1, convertView, parent);
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(getUnknownNameText());
        view.setTextWithHighlightingFactory(getTextWithHighlightingFactory());
        // TODO
//        view.setOnCallButtonClickListener(contactsListActivity);
        return view;
    }

    @Override
    public void bindView(View itemView, Context context, Cursor cursor) {
        super.bindView(itemView, context, cursor);
    }
}
