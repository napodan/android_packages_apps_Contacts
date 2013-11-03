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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Fragment containing a list of starred contacts followed by a list of frequently contacted.
 */
public class StrequentContactListFragment extends ContactBrowseListFragment {

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        adapter.moveToPosition(position);
        viewContact(adapter.getContactUri());
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        StrequentContactListAdapter adapter = new StrequentContactListAdapter(getActivity());
        adapter.setSectionHeaderDisplayEnabled(false);

        adapter.setContactNameDisplayOrder(getContactNameDisplayOrder());
        adapter.setSortOrder(getSortOrder());

        adapter.setDisplayPhotos(true);
        adapter.setQuickContactEnabled(true);

        adapter.configureLoader(getLoader());
        return adapter;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content, null);
    }
}
