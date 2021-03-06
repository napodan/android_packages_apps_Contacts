/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import com.android.contacts.list.CallOrSmsInitiator;
import com.android.contacts.list.ContactBrowseListContextMenuAdapter;
import com.android.contacts.list.ContactEntryListFragment;
import com.android.contacts.list.ContactPickerFragment;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.OnContactBrowserActionListener;
import com.android.contacts.list.OnContactPickerActionListener;
import com.android.contacts.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.list.OnPostalAddressPickerActionListener;
import com.android.contacts.list.PhoneNumberPickerFragment;
import com.android.contacts.list.PostalAddressPickerFragment;
import com.android.contacts.list.StrequentContactListFragment;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.widget.ContextMenuAdapter;
import com.android.contacts.widget.SearchEditText;
import com.android.contacts.widget.SearchEditText.OnFilterTextListener;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
@SuppressWarnings("deprecation")
public class ContactsListActivity extends Activity implements View.OnCreateContextMenuListener {

    private static final String TAG = "ContactsListActivity";

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;
    protected static final int SUBACTIVITY_FILTER = 5;

    private static final String[] RAW_CONTACTS_PROJECTION = new String[] {
        RawContacts._ID, //0
        RawContacts.CONTACT_ID, //1
        RawContacts.ACCOUNT_TYPE, //2
    };

    private Uri mSelectedContactUri;

    private ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int  mWritableSourcesCnt;
    private int  mReadOnlySourcesCnt;

    private final String[] sLookupProjection = new String[] {
            Contacts.LOOKUP_KEY
    };
    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (mSelectedContactUri != null) {
                getContentResolver().delete(mSelectedContactUri, null, null);
            }
        }
    }

    private ContactsIntentResolver mIntentResolver;
    protected ContactEntryListFragment<?> mListFragment;

    protected CallOrSmsInitiator mCallOrSmsInitiator;

    private int mActionCode;

    private boolean mSearchInitiated;

    private ContactsRequest mRequest;
    private SearchEditText mSearchEditText;

    public ContactsListActivity() {
        mIntentResolver = new ContactsIntentResolver(this);
    }

    /**
     * Visible for testing: makes queries run on the UI thread.
     */
    /* package */ void runQueriesSynchronously() {
        // TODO
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent redirect = mRequest.getRedirectIntent();
        if (redirect != null) {
            // Need to start a different activity
            startActivity(redirect);
            finish();
            return;
        }

        setTitle(mRequest.getActivityTitle());

        onCreateFragment();

        int listFragmentContainerId;
        if (mRequest.isSearchMode()) {
            setContentView(R.layout.contacts_search_content);
            listFragmentContainerId = R.id.list_container;
            setupSearchUI();
        } else {
            listFragmentContainerId = android.R.id.content;
        }
        FragmentTransaction transaction = openFragmentTransaction();
        transaction.add(mListFragment, listFragmentContainerId);
        transaction.commit();
    }

    private void setupSearchUI() {
        mSearchEditText = (SearchEditText)findViewById(R.id.search_src_text);
        mSearchEditText.setText(mRequest.getQueryString());
        mSearchEditText.setOnFilterTextListener(new OnFilterTextListener() {
            public void onFilterChange(String queryString) {
                mListFragment.setQueryString(queryString);
            }

            public void onCancelSearch() {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRequest.isSearchMode()) {
            mSearchEditText.requestFocus();
        }
    }

    /**
     * Creates the fragment based on the current request.
     */
    private void onCreateFragment() {
        mActionCode = mRequest.getActionCode();
        switch (mActionCode) {
            case ContactsRequest.ACTION_DEFAULT:
            case ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT: {
                DefaultContactBrowseListFragment fragment = new DefaultContactBrowseListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());

                if (mActionCode == ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT) {
                    fragment.setEditMode(true);
                    fragment.setCreateContactEnabled(true);
                }

                fragment.setDisplayWithPhonesOnlyOption(mRequest.getDisplayWithPhonesOnlyOption());

                fragment.setVisibleContactsRestrictionEnabled(
                        !mRequest.isSearchResultsMode()
                        && mRequest.getDisplayOnlyVisible());

                fragment.setContextMenuAdapter(new ContactBrowseListContextMenuAdapter(fragment));
                fragment.setSearchMode(mRequest.isSearchMode());
                fragment.setSearchResultsMode(mRequest.isSearchResultsMode());
                fragment.setQueryString(mRequest.getQueryString());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_GROUP: {
                throw new UnsupportedOperationException("Not yet implemented");
            }

            case ContactsRequest.ACTION_STARRED: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(false);
                fragment.setStarredContactsIncluded(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_FREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(false);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_STREQUENT: {
                StrequentContactListFragment fragment = new StrequentContactListFragment();
                fragment.setOnContactListActionListener(new ContactBrowserActionListener());
                fragment.setFrequentlyContactedContactsIncluded(true);
                fragment.setStarredContactsIncluded(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setCreateContactEnabled(true);
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT: {
                ContactPickerFragment fragment = new ContactPickerFragment();
                fragment.setOnContactPickerActionListener(new ContactPickerActionListener());
                fragment.setCreateContactEnabled(true);
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setShortcutRequested(true);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_PHONE: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_CALL: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setSectionHeaderDisplayEnabled(false);
                fragment.setShortcutAction(Intent.ACTION_CALL);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_CREATE_SHORTCUT_SMS: {
                PhoneNumberPickerFragment fragment = new PhoneNumberPickerFragment();
                fragment.setOnPhoneNumberPickerActionListener(
                        new PhoneNumberPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                fragment.setSectionHeaderDisplayEnabled(false);
                fragment.setShortcutAction(Intent.ACTION_SENDTO);
                mListFragment = fragment;
                break;
            }

            case ContactsRequest.ACTION_PICK_POSTAL: {
                PostalAddressPickerFragment fragment = new PostalAddressPickerFragment();
                fragment.setOnPostalAddressPickerActionListener(
                        new PostalAddressPickerActionListener());
                fragment.setLegacyCompatibilityMode(mRequest.isLegacyCompatibilityMode());
                mListFragment = fragment;
                break;
            }

            default:
                throw new IllegalStateException("Invalid action code: " + mActionCode);
        }
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        public void onSearchAllContactsAction(String queryString) {
            searchAllContacts(queryString, false);
        }

        public void onViewContactAction(Uri contactLookupUri) {
            startActivity(new Intent(Intent.ACTION_VIEW, contactLookupUri));
        }

        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        public void onEditContactAction(Uri contactLookupUri) {
            Intent intent = new Intent(Intent.ACTION_EDIT, contactLookupUri);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
        }

        public void onAddToFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 1);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onRemoveFromFavoritesAction(Uri contactUri) {
            ContentValues values = new ContentValues(1);
            values.put(Contacts.STARRED, 0);
            getContentResolver().update(contactUri, values, null, null);
        }

        public void onCallContactAction(Uri contactUri) {
            getCallOrSmsInitiator().initiateCall(contactUri);
        }

        public void onSmsContactAction(Uri contactUri) {
            getCallOrSmsInitiator().initiateSms(contactUri);
        }

        public void onDeleteContactAction(Uri contactUri) {
            doContactDelete(contactUri);
        }

        public void onFinishAction() {
            onBackPressed();
        }
    }

    private final class ContactPickerActionListener implements OnContactPickerActionListener {
        public void onSearchAllContactsAction(String queryString) {
            searchAllContacts(queryString, true);
        }

        public void onCreateNewContactAction() {
            Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            startActivityAndForwardResult(intent);
        }

        public void onPickContactAction(Uri contactUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(contactUri));
            finish();
        }

        public void onShortcutIntentCreated(Intent intent) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private final class PhoneNumberPickerActionListener implements
            OnPhoneNumberPickerActionListener {
        public void onSearchAllContactsAction(String queryString) {
            searchAllContacts(queryString, true);
        }

        public void onPickPhoneNumberAction(Uri dataUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(dataUri));
            finish();
        }

        public void onShortcutIntentCreated(Intent intent) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    private final class PostalAddressPickerActionListener implements
            OnPostalAddressPickerActionListener {
        public void onSearchAllContactsAction(String queryString) {
            searchAllContacts(queryString, true);
        }

        public void onPickPostalAddressAction(Uri dataUri) {
            Intent intent = new Intent();
            setResult(RESULT_OK, intent.setData(dataUri));
            finish();
        }
    }

    public void startActivityAndForwardResult(final Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);

        // Forward extras to the new activity
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // TODO what other actions need the menu?
        if (mActionCode == ContactsRequest.ACTION_DEFAULT ||
                mActionCode == ContactsRequest.ACTION_STREQUENT) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.list, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_display_groups).setVisible(
                mActionCode == ContactsRequest.ACTION_DEFAULT);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_display_groups: {
                final Intent intent = new Intent(this, ContactsPreferencesActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                displayImportExportDialog();
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(Settings.EXTRA_AUTHORITIES, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
// TODO
//        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return;
//        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
//            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
//                if ((mMode & MODE_MASK_PICKER) != 0) {
//                    ContactsSearchManager.startSearchForResult(this, initialQuery,
//                            SUBACTIVITY_FILTER, null);
//                } else {
                    ContactsSearchManager.startSearch(this, initialQuery, mRequest);
//                }
//            }
        }
    }

    /**
     * Starts a new activity that will run a search query and display search results.
     */
    protected void searchAllContacts(String queryString, boolean returnResult) {
        String query = mListFragment.getQueryString();
        if (TextUtils.isEmpty(query)) {
            return;
        }

        Intent intent = new Intent(this, SearchResultsActivity.class);
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        intent.putExtra(ContactsSearchManager.ORIGINAL_REQUEST_KEY, mRequest);

        if (returnResult) {
            startActivityForResult(intent, SUBACTIVITY_SEARCH);
        } else {
            startActivity(intent);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.string.import_from_sim:
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(this, id);
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_hide_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_multiple_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
        }
        return super.onCreateDialog(id, bundle);
    }

    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private void displayImportExportDialog() {
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final Resources res = dialogContext.getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                            parent, false);
                }

                final int resId = this.getItem(position);
                ((TextView)convertView).setText(resId);
                return convertView;
            }
        };

        if (TelephonyManager.getDefault().hasIccCard()) {
            adapter.add(R.string.import_from_sim);
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            adapter.add(R.string.export_to_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            adapter.add(R.string.share_visible_contacts);
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_sim:
                    case R.string.import_from_sdcard: {
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        Context context = ContactsListActivity.this;
                        Intent exportIntent = new Intent(context, ExportVCardActivity.class);
                        context.startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: " +
                                getResources().getResourceEntryName(resId));
                    }
                }
            }
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_import_export)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(adapter, -1, clickListener)
            .show();
    }

    private void doShareVisibleContacts() {
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                sLookupProjection, Contacts.IN_VISIBLE_GROUP + "!=0", null, null);
        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder uriListBuilder = new StringBuilder();
            int index = 0;
            for (;!cursor.isAfterLast(); cursor.moveToNext()) {
                if (index != 0)
                    uriListBuilder.append(':');
                uriListBuilder.append(cursor.getString(0));
                index++;
            }
            Uri uri = Uri.withAppendedPath(
                    Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(uriListBuilder.toString()));

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Contacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(intent);
        } finally {
            cursor.close();
        }
    }

    private void handleImportRequest(int resId) {
        // There's three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final Sources sources = Sources.getInstance(this);
        final List<Account> accountList = sources.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            showDialog(resId);
            return;
        }

        AccountSelectionUtil.doImport(this, resId, (size == 1 ? accountList.get(0) : null));
    }

//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        switch (requestCode) {
//            case SUBACTIVITY_NEW_CONTACT:
//                if (resultCode == RESULT_OK) {
////                    returnPickerResult(null, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
////                            data.getData());
//                }
//                break;
//
//            case SUBACTIVITY_VIEW_CONTACT:
//                if (resultCode == RESULT_OK) {
//                    mAdapter.notifyDataSetChanged();
//                }
//                break;
//
//            case SUBACTIVITY_DISPLAY_GROUP:
//                // Mark as just created so we re-run the view query
////                mJustCreated = true;
//                break;
//
//            case SUBACTIVITY_FILTER:
//            case SUBACTIVITY_SEARCH:
//                // Pass through results of filter or search UI
//                if (resultCode == RESULT_OK) {
//                    setResult(RESULT_OK, data);
//                    finish();
//                } else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
//                    // Finish the activity if the sub activity was canceled as back key is used
//                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
//                    finish();
//                }
//                break;
//        }
//    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuAdapter menuAdapter = mListFragment.getContextMenuAdapter();
        if (menuAdapter != null) {
            return menuAdapter.onContextItemSelected(item);
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Event handler for the use case where the user starts typing without
     * bringing up the search UI first.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mSearchInitiated && !mRequest.isSearchMode()
                && !mRequest.isSearchResultsMode()) {
            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar != 0) {
                mSearchInitiated = true;
                startSearch(new String(new int[]{unicodeChar}, 0, 1), false, null, false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // TODO move to the fragment
        switch (keyCode) {
//            case KeyEvent.KEYCODE_CALL: {
//                if (callSelection()) {
//                    return true;
//                }
//                break;
//            }

            case KeyEvent.KEYCODE_DEL: {
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteSelection() {
        // TODO move to the fragment
//        if (mActionCode == ContactsRequest.ACTION_DEFAULT) {
//            final int position = mListView.getSelectedItemPosition();
//            if (position != ListView.INVALID_POSITION) {
//                Uri contactUri = getContactUri(position);
//                if (contactUri != null) {
//                    doContactDelete(contactUri);
//                    return true;
//                }
//            }
//        }
        return false;
    }

    /**
     * Prompt the user before deleting the given {@link Contacts} entry.
     */
    protected void doContactDelete(Uri contactUri) {
        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mWritableRawContactIds.clear();

        Sources sources = Sources.getInstance(ContactsListActivity.this);
        Cursor c = getContentResolver().query(RawContacts.CONTENT_URI, RAW_CONTACTS_PROJECTION,
                RawContacts.CONTACT_ID + "=" + ContentUris.parseId(contactUri), null,
                null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    final String accountType = c.getString(2);
                    final long rawContactId = c.getLong(0);
                    ContactsSource contactsSource = sources.getInflatedSource(accountType,
                            ContactsSource.LEVEL_SUMMARY);
                    if (contactsSource != null && contactsSource.readOnly) {
                        mReadOnlySourcesCnt += 1;
                    } else {
                        mWritableSourcesCnt += 1;
                        mWritableRawContactIds.add(rawContactId);
                    }
                }
            } finally {
                c.close();
            }
        }

        mSelectedContactUri = contactUri;
        if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt > 0) {
            showDialog(R.id.dialog_readonly_contact_delete_confirmation);
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            showDialog(R.id.dialog_readonly_contact_hide_confirmation);
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            showDialog(R.id.dialog_multiple_contact_delete_confirmation);
        } else {
            showDialog(R.id.dialog_delete_contact_confirmation);
        }
    }

    private CallOrSmsInitiator getCallOrSmsInitiator() {
        if (mCallOrSmsInitiator == null) {
            mCallOrSmsInitiator = new CallOrSmsInitiator(this);
        }
        return mCallOrSmsInitiator;
    }
}
