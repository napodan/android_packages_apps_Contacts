/*
 * Copyright (C) 2010 Google Inc.
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
 * limitations under the License
 */

package com.android.contacts.views.editor;

import com.android.contacts.ContactOptionsActivity;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.ui.EditContactActivity;
import com.android.contacts.views.ContactLoader;
import com.android.contacts.views.editor.view.ViewTypes;
import com.android.contacts.views.editor.viewModel.BaseViewModel;
import com.android.contacts.views.editor.viewModel.EmailViewModel;
import com.android.contacts.views.editor.viewModel.FooterViewModel;
import com.android.contacts.views.editor.viewModel.ImViewModel;
import com.android.contacts.views.editor.viewModel.NicknameViewModel;
import com.android.contacts.views.editor.viewModel.NoteViewModel;
import com.android.contacts.views.editor.viewModel.OrganizationViewModel;
import com.android.contacts.views.editor.viewModel.PhoneViewModel;
import com.android.contacts.views.editor.viewModel.PhotoViewModel;
import com.android.contacts.views.editor.viewModel.StructuredNameViewModel;
import com.android.contacts.views.editor.viewModel.StructuredPostalViewModel;
import com.android.contacts.views.editor.viewModel.WebsiteViewModel;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.LoaderManagingFragment;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.Intent;
import android.content.Loader;
import android.content.Entity.NamedContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

public class ContactEditorFragment extends LoaderManagingFragment<ContactLoader.Result>
        implements OnCreateContextMenuListener {
    private static final String TAG = "ContactEditorFragment";

    private static final String BUNDLE_RAW_CONTACT_ID = "rawContactId";

    private static final int LOADER_DETAILS = 1;

    private Context mContext;
    private Uri mLookupUri;
    private Listener mListener;

    private boolean mIsInitialized;

    private ContactLoader.Result mContactData;
    private ContactEditorHeaderView mHeaderView;
    private LinearLayout mFieldContainer;

    private int mReadOnlySourcesCnt;
    private int mWritableSourcesCnt;
    private boolean mAllRestricted;

    /**
     * A list of RawContacts included in this Contact.
     */
    private ArrayList<DisplayRawContact> mRawContacts = new ArrayList<DisplayRawContact>();

    private LayoutInflater mInflater;

    public ContactEditorFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        final View view = inflater.inflate(R.layout.contact_editor_fragment, container, false);

        setHasOptionsMenu(true);

        mInflater = inflater;

        mHeaderView = (ContactEditorHeaderView) view.findViewById(R.id.contact_header_widget);

        mFieldContainer = (LinearLayout) view.findViewById(R.id.field_container);
        mFieldContainer.setOnCreateContextMenuListener(this);
        mFieldContainer.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

        return view;
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    public void loadUri(Uri lookupUri) {
        mLookupUri = lookupUri;
        if (mIsInitialized) startLoading(LOADER_DETAILS, null);
    }

    @Override
    protected void onInitializeLoaders() {
        mIsInitialized = true;
        if (mLookupUri != null) startLoading(LOADER_DETAILS, null);
    }

    @Override
    protected Loader<ContactLoader.Result> onCreateLoader(int id, Bundle args) {
        switch (id) {
            case LOADER_DETAILS: {
                return new ContactLoader(mContext, mLookupUri);
            }
            default: {
                Log.wtf(TAG, "Unknown ID in onCreateLoader: " + id);
            }
        }
        return null;
    }

    @Override
    protected void onLoadFinished(Loader<ContactLoader.Result> loader,
            ContactLoader.Result data) {
        final int id = loader.getId();
        switch (id) {
            case LOADER_DETAILS:
                if (data == ContactLoader.Result.NOT_FOUND) {
                    // Item has been deleted
                    Log.i(TAG, "No contact found. Closing activity");
                    mListener.onContactNotFound();
                    return;
                }
                if (data == ContactLoader.Result.ERROR) {
                    // Item has been deleted
                    Log.i(TAG, "Error fetching contact. Closing activity");
                    mListener.onError();
                    return;
                }
                mContactData = data;
                bindData();
                break;
            default: {
                Log.wtf(TAG, "Unknown ID in onLoadFinished: " + id);
            }
        }
    }

    private void bindData() {
        // Build up the contact entries
        buildEntries();

        mHeaderView.setMergeInfo(mRawContacts.size());

        createFieldViews();
    }

    /**
     * Build up the entries to display on the screen.
     */
    private final void buildEntries() {
        // Clear out the old entries
        mRawContacts.clear();

        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mAllRestricted = true;

        // TODO: This should be done in the background thread
        final Sources sources = Sources.getInstance(mContext);

        // Build up method entries
        if (mContactData == null) {
            return;
        }

        for (Entity entity: mContactData.getEntities()) {
            final ContentValues entValues = entity.getEntityValues();
            final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
            final String accountName = entValues.getAsString(RawContacts.ACCOUNT_NAME);
            final long rawContactId = entValues.getAsLong(RawContacts._ID);
            final String rawContactUriString = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                    rawContactId).toString();

            // Mark when this contact has any unrestricted components
            final boolean isRestricted = entValues.getAsInteger(RawContacts.IS_RESTRICTED) != 0;
            if (!isRestricted) mAllRestricted = false;

            final ContactsSource contactsSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_SUMMARY);
            final boolean writable = contactsSource == null || !contactsSource.readOnly;
            if (writable) {
                mWritableSourcesCnt += 1;
            } else {
                mReadOnlySourcesCnt += 1;
            }

            final DisplayRawContact rawContact = new DisplayRawContact(mContext, contactsSource,
                    accountName, rawContactId, writable, mRawContactFooterListener);
            mRawContacts.add(rawContact);

            for (NamedContentValues subValue : entity.getSubValues()) {
                final ContentValues entryValues = subValue.values;
                entryValues.put(Data.RAW_CONTACT_ID, rawContactId);

                final long dataId = entryValues.getAsLong(Data._ID);
                final String mimeType = entryValues.getAsString(Data.MIMETYPE);
                if (mimeType == null) continue;

                final DataKind kind = sources.getKindOrFallback(accountType, mimeType, mContext,
                        ContactsSource.LEVEL_MIMETYPES);
                if (kind == null) continue;

                // TODO: This surely can be written more nicely. Think about a factory once
                // all editors are done
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final StructuredNameViewModel itemEditor =
                            StructuredNameViewModel.createForExisting(mContext, rawContact, dataId,
                            entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final StructuredPostalViewModel itemEditor =
                            StructuredPostalViewModel.createForExisting(mContext, rawContact,
                            dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final PhoneViewModel itemEditor = PhoneViewModel.createForExisting(mContext,
                            rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final EmailViewModel itemEditor = EmailViewModel.createForExisting(mContext,
                            rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final ImViewModel itemEditor = ImViewModel.createForExisting(mContext,
                            rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final NicknameViewModel itemEditor = NicknameViewModel.createForExisting(
                            mContext, rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Note.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final NoteViewModel itemEditor = NoteViewModel.createForExisting(mContext,
                            rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final WebsiteViewModel itemEditor = WebsiteViewModel.createForExisting(
                            mContext, rawContact, dataId, entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final OrganizationViewModel itemEditor =
                            OrganizationViewModel.createForExisting(mContext, rawContact, dataId,
                            entryValues, kind.titleRes);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }

                if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final PhotoViewModel itemEditor =
                            PhotoViewModel.createForExisting(mContext, rawContact, dataId,
                            entryValues);
                    rawContact.getFields().add(itemEditor);
                    continue;
                }
            }
        }
    }

    private void createFieldViews() {
        mFieldContainer.removeAllViews();

        for (int i = 0; i < mRawContacts.size(); i++) {
            final DisplayRawContact rawContact = mRawContacts.get(i);
            // Header
            mFieldContainer.addView(
                            rawContact.getHeader().getView(mInflater, mFieldContainer));

            // Data items
            final ArrayList<BaseViewModel> fields = rawContact.getFields();
            for (int j = 0; j < fields.size(); j++) {
                final BaseViewModel field = fields.get(j);
                mFieldContainer.addView(field.getView(mInflater, mFieldContainer));
            }

            // Footer
            mFieldContainer.addView(
                    rawContact.getFooter().getView(mInflater, mFieldContainer));
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.view, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // TODO: Prepare options
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                // TODO: This is temporary code to invoke the old editor. We can get rid of this
                // later
                final Intent intent = new Intent();
                intent.setClass(mContext, EditContactActivity.class);
                final long rawContactId = mRawContacts.get(0).getId();
                final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                        rawContactId);
                intent.setAction(Intent.ACTION_EDIT);
                intent.setData(rawContactUri);
                startActivity(intent);
                return true;
            }
            case R.id.menu_delete: {
                showDeleteConfirmationDialog();
                return true;
            }
            case R.id.menu_options: {
                final Intent intent = new Intent(mContext, ContactOptionsActivity.class);
                intent.setData(mContactData.getLookupUri());
                mContext.startActivity(intent);
                return true;
            }
            case R.id.menu_share: {
                if (mAllRestricted) return false;

                final String lookupKey = mContactData.getLookupKey();
                final Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, shareUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = mContext.getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    mContext.startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(mContext, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }
        return false;
    }

    private void showDeleteConfirmationDialog() {
        final int dialogId;
        if (mReadOnlySourcesCnt > 0 & mWritableSourcesCnt > 0) {
            dialogId = R.id.detail_dialog_confirm_readonly_delete;
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            dialogId = R.id.detail_dialog_confirm_readonly_hide;
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            dialogId = R.id.detail_dialog_confirm_multiple_delete;
        } else {
            dialogId = R.id.detail_dialog_confirm_delete;
        }
        if (mListener != null) mListener.onDialogRequested(dialogId, null);
    }

    // This was the ListView based code to expand/collapse sections.
//    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        if (mListener == null) return;
//        final BaseViewModel baseEntry = mAdapter.getEntry(position);
//        if (baseEntry == null) return;
//
//        if (baseEntry instanceof HeaderViewModel) {
//            // Toggle rawcontact visibility
//            final HeaderViewModel entry = (HeaderViewModel) baseEntry;
//            entry.setCollapsed(!entry.isCollapsed());
//            mAdapter.notifyDataSetChanged();
//        }
//    }

    private final DialogInterface.OnClickListener mDeleteListener =
            new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            mContext.getContentResolver().delete(mContactData.getLookupUri(), null, null);
        }
    };

    public Dialog onCreateDialog(int id, Bundle bundle) {
        // TODO The delete dialogs mirror the functionality from the Contact-Detail-Fragment.
        //      Consider whether we can extract common logic here
        // TODO The actual removal is not in a worker thread currently
        switch (id) {
            case R.id.detail_dialog_confirm_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_readonly_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_multiple_delete:
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .setCancelable(false)
                        .create();
            case R.id.detail_dialog_confirm_readonly_hide: {
                return new AlertDialog.Builder(mContext)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setPositiveButton(android.R.string.ok, mDeleteListener)
                        .create();
            }
            case R.id.edit_dialog_add_information: {
                final long rawContactId = bundle.getLong(BUNDLE_RAW_CONTACT_ID);
                final DisplayRawContact rawContact = findRawContactById(rawContactId);
                if (rawContact == null) return null;
                final ContactsSource source = rawContact.getSource();

                final ArrayList<DataKind> originalDataKinds = source.getSortedDataKinds();
                // We should not modify the result returned from getSortedDataKinds but
                // we have to filter some items out. Therefore we copy items into a new ArrayList
                final ArrayList<DataKind> filteredDataKinds =
                        new ArrayList<DataKind>(originalDataKinds.size());
                final ArrayList<String> items = new ArrayList<String>(filteredDataKinds.size());
                for (DataKind dataKind : originalDataKinds) {
                    // TODO: Filter out fields that do not make sense in the current Context
                    //       (Name, Photo, Notes etc)
                    if (dataKind.titleRes == -1) continue;
                    if (!dataKind.editable) continue;
                    final String title = mContext.getString(dataKind.titleRes);
                    items.add(title);
                    filteredDataKinds.add(dataKind);
                }
                final DialogInterface.OnClickListener itemClickListener =
                        new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Create an Intent for the INSERT-Editor. Its data is null
                        // and the RawContact is identified in the Extras
                        final String rawContactUriString = ContentUris.withAppendedId(
                                RawContacts.CONTENT_URI, rawContactId).toString();
                        final DataKind dataKind = filteredDataKinds.get(which);
                        // TODO: Add new row
//                        final Intent intent = new Intent();
//                        intent.setType(dataKind.mimeType);
//                        intent.setAction(Intent.ACTION_INSERT);
//                        intent.putExtra(ContactFieldEditorActivity.BUNDLE_RAW_CONTACT_URI,
//                                rawContactUriString);
//                        if (mListener != null) mListener.onEditorRequested(intent);
                    }
                };
                return new AlertDialog.Builder(mContext)
                        .setItems(items.toArray(new String[0]), itemClickListener)
                        .create();
            }
            default:
                return null;
        }
    }

    private DisplayRawContact findRawContactById(long rawContactId) {
        for (DisplayRawContact rawContact : mRawContacts) {
            if (rawContact.getId() == rawContactId) return rawContact;
        }
        return null;
    }

    private FooterViewModel.Listener mRawContactFooterListener =
            new FooterViewModel.Listener() {
        public void onAddClicked(DisplayRawContact rawContact) {
            // Create a bundle to show the Dialog
            final Bundle bundle = new Bundle();
            bundle.putLong(BUNDLE_RAW_CONTACT_ID, rawContact.getId());
            if (mListener != null) {
                mListener.onDialogRequested(R.id.edit_dialog_add_information, bundle);
            }
        }
        public void onSeparateClicked(DisplayRawContact rawContact) {
        }
        public void onDeleteClicked(DisplayRawContact rawContact) {
        }
    };

    public static interface Listener {
        /**
         * Contact was not found, so somehow close this fragment.
         */
        public void onContactNotFound();

        /**
         * There was an error loading the contact
         */
        public void onError();

        /**
         * User clicked a single item (e.g. mail) to edit it or is adding a new field
         */
        public void onEditorRequested(Intent intent);

        /**
         * Show a dialog using the globally unique id
         */
        public void onDialogRequested(int id, Bundle bundle);
    }
}