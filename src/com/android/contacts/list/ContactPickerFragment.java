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
import com.android.contacts.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

/**
 * Fragment for the contact list used for browsing contacts (as compared to
 * picking a contact with one of the PICK or SHORTCUT intents).
 */
public class ContactPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener {

    private OnContactPickerActionListener mListener;
    private boolean mCreateContactEnabled;
    private boolean mShortcutRequested;

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isShortcutRequested() {
        return mShortcutRequested;
    }

    public void setShortcutRequested(boolean flag) {
        mShortcutRequested = flag;
    }

    @Override
    protected View createView(LayoutInflater inflater, ViewGroup container) {
        View view = super.createView(inflater, container);
        if (mCreateContactEnabled) {
            getListView().addHeaderView(inflater.inflate(R.layout.create_new_contact, null, false));
        }
        return view;
    }

    public boolean isSearchAllContactsItemPosition(int position) {
        return isSearchMode() && position == getAdapter().getCount() - 1;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0 && !isSearchMode() && mCreateContactEnabled) {
            mListener.onCreateNewContactAction();
        } else {
            super.onItemClick(parent, view, position, id);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (isSearchAllContactsItemPosition(position)) {
            mListener.onSearchAllContactsAction((String)null);
        } else {
            Uri uri;
            ContactEntryListAdapter adapter = getAdapter();
            adapter.moveToPosition(position);
            if (isLegacyCompatibilityMode()) {
                uri = ((LegacyContactListAdapter)adapter).getPersonUri();
            } else {
                uri = ((ContactListAdapter)adapter).getContactUri();
            }
            if (mShortcutRequested) {
                ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
                builder.createContactShortcutIntent(uri);
            } else {
                mListener.onPickContactAction(uri);
            }
        }
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        if (!isLegacyCompatibilityMode()) {
            ContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
            adapter.setSectionHeaderDisplayEnabled(true);
            adapter.setDisplayPhotos(true);
            adapter.setQuickContactEnabled(false);
            return adapter;
        } else {
            LegacyContactListAdapter adapter = new LegacyContactListAdapter(getActivity());
            adapter.setSectionHeaderDisplayEnabled(false);
            adapter.setDisplayPhotos(false);
            return adapter;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        ContactEntryListAdapter adapter = getAdapter();

        // If "Create new contact" is shown, don't display the empty list UI
        adapter.setEmptyListEnabled(!isCreateContactEnabled());
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        if (isSearchMode()) {
            return inflater.inflate(R.layout.contacts_search_content, null);
        } else if (isSearchResultsMode()) {
            return inflater.inflate(R.layout.contacts_list_search_results, null);
        } else {
            return inflater.inflate(R.layout.contacts_list_content, null);
        }
    }

    @Override
    protected void prepareEmptyView() {
        if (isSearchMode()) {
            return;
        } else if (isSearchResultsMode()) {
            setEmptyText(R.string.noMatchingContacts);
        } else if (isSyncActive()) {
            if (mShortcutRequested) {
                // Help text is the same no matter whether there is SIM or not.
                setEmptyText(R.string.noContactsHelpTextWithSyncForCreateShortcut);
            } else if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpTextWithSync);
            } else {
                setEmptyText(R.string.noContactsNoSimHelpTextWithSync);
            }
        } else {
            if (mShortcutRequested) {
                // Help text is the same no matter whether there is SIM or not.
                setEmptyText(R.string.noContactsHelpTextWithSyncForCreateShortcut);
            } else if (hasIccCard()) {
                setEmptyText(R.string.noContactsHelpText);
            } else {
                setEmptyText(R.string.noContactsNoSimHelpText);
            }
        }
    }

    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }
}
