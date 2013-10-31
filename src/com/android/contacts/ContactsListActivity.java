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

import com.android.contacts.TextHighlightingAnimation.TextWithHighlighting;
import com.android.contacts.list.ContactItemListAdapter;
import com.android.contacts.list.config.ContactListConfiguration;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.ui.ContactsPreferences;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.ui.ContactsPreferencesActivity.Prefs;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.Constants;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents.Insert;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
@SuppressWarnings("deprecation")
public class ContactsListActivity extends ListActivity implements View.OnCreateContextMenuListener,
        View.OnClickListener, View.OnKeyListener, TextWatcher, TextView.OnEditorActionListener,
        OnFocusChangeListener, OnTouchListener {

    public static class ContactsSearchActivity extends ContactsListActivity {

    }

    private static final String TAG = "ContactsListActivity";

    private static final boolean ENABLE_ACTION_ICON_OVERLAYS = true;

    private static final String LIST_STATE_KEY = "liststate";
    private static final String SHORTCUT_ACTION_KEY = "shortcutAction";

    static final int MENU_ITEM_VIEW_CONTACT = 1;
    static final int MENU_ITEM_CALL = 2;
    static final int MENU_ITEM_EDIT_BEFORE_CALL = 3;
    static final int MENU_ITEM_SEND_SMS = 4;
    static final int MENU_ITEM_SEND_IM = 5;
    static final int MENU_ITEM_EDIT = 6;
    static final int MENU_ITEM_DELETE = 7;
    static final int MENU_ITEM_TOGGLE_STAR = 8;

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;
    private static final int SUBACTIVITY_FILTER = 5;

    private static final int TEXT_HIGHLIGHTING_ANIMATION_DURATION = 350;

    public static final String AUTHORITIES_FILTER_KEY = "authorities";

    private static final Uri CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS =
            buildSectionIndexerUri(Contacts.CONTENT_URI);

    /** Mask for picker mode */
    public static final int MODE_MASK_PICKER = 0x80000000;
    /** Mask for no presence mode */
    public static final int MODE_MASK_NO_PRESENCE = 0x40000000;
    /** Mask for enabling list filtering */
    public static final int MODE_MASK_NO_FILTER = 0x20000000;
    /** Mask for having a "create new contact" header in the list */
    public static final int MODE_MASK_CREATE_NEW = 0x10000000;
    /** Mask for showing photos in the list */
    public static final int MODE_MASK_SHOW_PHOTOS = 0x08000000;
    /** Mask for hiding additional information e.g. primary phone number in the list */
    public static final int MODE_MASK_NO_DATA = 0x04000000;
    /** Mask for showing a call button in the list */
    public static final int MODE_MASK_SHOW_CALL_BUTTON = 0x02000000;
    /** Mask to disable quickcontact (images will show as normal images) */
    public static final int MODE_MASK_DISABLE_QUIKCCONTACT = 0x01000000;
    /** Mask to show the total number of contacts at the top */
    public static final int MODE_MASK_SHOW_NUMBER_OF_CONTACTS = 0x00800000;

    /** Unknown mode */
    public static final int MODE_UNKNOWN = 0;
    /** Default mode */
    public static final int MODE_DEFAULT = 4 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Custom mode */
    public static final int MODE_CUSTOM = 8;
    /** Show all starred contacts */
    public static final int MODE_STARRED = 20 | MODE_MASK_SHOW_PHOTOS;
    /** Show frequently contacted contacts */
    public static final int MODE_FREQUENT = 30 | MODE_MASK_SHOW_PHOTOS;
    /** Show starred and the frequent */
    public static final int MODE_STREQUENT = 35 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_CALL_BUTTON;
    /** Show all contacts and pick them when clicking */
    public static final int MODE_PICK_CONTACT = 40 | MODE_MASK_PICKER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts as well as the option to create a new one */
    public static final int MODE_PICK_OR_CREATE_CONTACT = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider and pick them when clicking */
    public static final int MODE_LEGACY_PICK_PERSON = 43 | MODE_MASK_PICKER
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider as well as the option to create a new one */
    public static final int MODE_LEGACY_PICK_OR_CREATE_PERSON = 44 | MODE_MASK_PICKER
            | MODE_MASK_CREATE_NEW | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    public static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all phone numbers and pick them when clicking */
    public static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all phone numbers through the legacy provider and pick them when clicking */
    public static final int MODE_LEGACY_PICK_PHONE =
            51 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    public static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    public static final int MODE_LEGACY_PICK_POSTAL =
            56 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    public static final int MODE_GROUP = 57 | MODE_MASK_SHOW_PHOTOS;
    /** Run a search query */
    public static final int MODE_QUERY = 60 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Run a search query in PICK mode, but that still launches to VIEW */
    public static final int MODE_QUERY_PICK_TO_VIEW = 65 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK mode */
    public static final int MODE_QUERY_PICK = 75 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_PICKER | MODE_MASK_DISABLE_QUIKCCONTACT | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK_PHONE mode */
    public static final int MODE_QUERY_PICK_PHONE = 80 | MODE_MASK_NO_FILTER | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in PICK mode, but that still launches to EDIT */
    public static final int MODE_QUERY_PICK_TO_EDIT = 85 | MODE_MASK_NO_FILTER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_PICKER | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /**
     * Show all phone numbers and do multiple pick when clicking. This mode has phone filtering
     * feature, but doesn't support 'search for all contacts'.
     */
    public static final int MODE_PICK_MULTIPLE_PHONES = 80 | MODE_MASK_PICKER
            | MODE_MASK_NO_PRESENCE | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;

    /**
     * An action used to do perform search while in a contact picker.  It is initiated
     * by the ContactListActivity itself.
     */
    protected static final String ACTION_SEARCH_INTERNAL = "com.android.contacts.INTERNAL_SEARCH";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
    };
    static final String[] CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        // email lookup doesn't included HAS_PHONE_NUMBER in projection
    };

    static final String[] CONTACTS_SUMMARY_FILTER_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        SearchSnippetColumns.SNIPPET_MIMETYPE, // 11
        SearchSnippetColumns.SNIPPET_DATA1,     // 12
        SearchSnippetColumns.SNIPPET_DATA4,     // 13
    };

    static final String[] LEGACY_PEOPLE_PROJECTION = new String[] {
        People._ID,                         // 0
        People.DISPLAY_NAME,                // 1
        People.DISPLAY_NAME,                // 2
        People.DISPLAY_NAME,                // 3
        People.STARRED,                     // 4
        PeopleColumns.TIMES_CONTACTED,      // 5
        People.PRESENCE_STATUS,             // 6
    };
    public static final int SUMMARY_ID_COLUMN_INDEX = 0;
    public static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    public static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    static final int SUMMARY_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    public static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 5;
    public static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 6;
    public static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 7;
    public static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    public static final int SUMMARY_PHONETIC_NAME_COLUMN_INDEX = 9;
    public static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;
    public static final int SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX = 11;
    public static final int SUMMARY_SNIPPET_DATA1_COLUMN_INDEX = 12;
    public static final int SUMMARY_SNIPPET_DATA4_COLUMN_INDEX = 13;

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, //0
        Phone.TYPE, //1
        Phone.LABEL, //2
        Phone.NUMBER, //3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
        Contacts.SORT_KEY_PRIMARY, // 6
        Contacts.PHOTO_ID, // 7
    };
    static final String[] LEGACY_PHONES_PROJECTION = new String[] {
        Phones._ID, //0
        Phones.TYPE, //1
        Phones.LABEL, //2
        Phones.NUMBER, //3
        People.DISPLAY_NAME, // 4
    };
    public static final int PHONE_ID_COLUMN_INDEX = 0;
    public static final int PHONE_TYPE_COLUMN_INDEX = 1;
    public static final int PHONE_LABEL_COLUMN_INDEX = 2;
    public static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    public static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    public static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;
    static final int PHONE_SORT_KEY_PRIMARY_COLUMN_INDEX = 6;
    public static final int PHONE_PHOTO_ID_COLUMN_INDEX = 7;

    static final String[] POSTALS_PROJECTION = new String[] {
        StructuredPostal._ID, //0
        StructuredPostal.TYPE, //1
        StructuredPostal.LABEL, //2
        StructuredPostal.DATA, //3
        StructuredPostal.DISPLAY_NAME, // 4
    };
    static final String[] LEGACY_POSTALS_PROJECTION = new String[] {
        ContactMethods._ID, //0
        ContactMethods.TYPE, //1
        ContactMethods.LABEL, //2
        ContactMethods.DATA, //3
        People.DISPLAY_NAME, // 4
    };
    static final String[] RAW_CONTACTS_PROJECTION = new String[] {
        RawContacts._ID, //0
        RawContacts.CONTACT_ID, //1
        RawContacts.ACCOUNT_TYPE, //2
    };

    static final int POSTAL_ID_COLUMN_INDEX = 0;
    public static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    public static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    public static final int POSTAL_ADDRESS_COLUMN_INDEX = 3;
    public static final int POSTAL_DISPLAY_NAME_COLUMN_INDEX = 4;

    private static final int QUERY_TOKEN = 42;

    static final String KEY_PICKER_MODE = "picker_mode";

    private static final String TEL_SCHEME = "tel";
    private static final String CONTENT_SCHEME = "content";

    private ContactItemListAdapter mAdapter;
    public ContactListEmptyView mEmptyView;

    public int mMode = MODE_DEFAULT;
    private boolean mRunQueriesSynchronously;
    private QueryHandler mQueryHandler;
    private boolean mJustCreated;
    private boolean mSyncEnabled;
    Uri mSelectedContactUri;

//    private boolean mDisplayAll;
    public boolean mDisplayOnlyPhones;

    private String mGroupName;

    private ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int  mWritableSourcesCnt;
    private int  mReadOnlySourcesCnt;

    /**
     * Used to keep track of the scroll state of the list.
     */
    private Parcelable mListState = null;

    public String mShortcutAction;

    /**
     * Internal query type when in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    public int mQueryMode = QUERY_MODE_NONE;

    public static final int QUERY_MODE_NONE = -1;
    private static final int QUERY_MODE_MAILTO = 1;
    private static final int QUERY_MODE_TEL = 2;

    public int mProviderStatus = ProviderStatus.STATUS_NORMAL;

    public boolean mSearchMode;
    public boolean mSearchResultsMode;
    public boolean mShowNumberOfContacts;

    public boolean mShowSearchSnippets;
    private boolean mSearchInitiated;

    private String mInitialFilter;

    private static final String CLAUSE_ONLY_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";
    private static final String CLAUSE_ONLY_PHONES = Contacts.HAS_PHONE_NUMBER + "=1";


    // Uri matcher for contact id
    private static final int CONTACTS_ID = 1001;
    private static final UriMatcher sContactsIdMatcher;

    public ContactPhotoLoader mPhotoLoader;

    final String[] sLookupProjection = new String[] {
            Contacts.LOOKUP_KEY
    };

    /**
     * User selected phone number and id in MODE_PICK_MULTIPLE_PHONES mode.
     */
    public UserSelection mUserSelection = new UserSelection(null, null);

    /**
     * The adapter for the phone numbers, used in MODE_PICK_MULTIPLE_PHONES mode.
     */
    public PhoneNumberAdapter mPhoneNumberAdapter = new PhoneNumberAdapter(this, null);

    private static int[] CHIP_COLOR_ARRAY = {
        R.drawable.appointment_indicator_leftside_1,
        R.drawable.appointment_indicator_leftside_2,
        R.drawable.appointment_indicator_leftside_3,
        R.drawable.appointment_indicator_leftside_4,
        R.drawable.appointment_indicator_leftside_5,
        R.drawable.appointment_indicator_leftside_6,
        R.drawable.appointment_indicator_leftside_7,
        R.drawable.appointment_indicator_leftside_8,
        R.drawable.appointment_indicator_leftside_9,
        R.drawable.appointment_indicator_leftside_10,
        R.drawable.appointment_indicator_leftside_11,
        R.drawable.appointment_indicator_leftside_12,
        R.drawable.appointment_indicator_leftside_13,
        R.drawable.appointment_indicator_leftside_14,
        R.drawable.appointment_indicator_leftside_15,
        R.drawable.appointment_indicator_leftside_16,
        R.drawable.appointment_indicator_leftside_17,
        R.drawable.appointment_indicator_leftside_18,
        R.drawable.appointment_indicator_leftside_19,
        R.drawable.appointment_indicator_leftside_20,
        R.drawable.appointment_indicator_leftside_21,
    };

    /**
     * This is the map from contact to color index.
     * A colored chip in MODE_PICK_MULTIPLE_PHONES mode is used to indicate the number of phone
     * numbers belong to one contact
     */
    SparseIntArray mContactColor = new SparseIntArray();

    /**
     * UI control of action panel in MODE_PICK_MULTIPLE_PHONES mode.
     */
    private View mFooterView;

    /**
     * Display only selected recipients or not in MODE_PICK_MULTIPLE_PHONES mode
     */
    public boolean mShowSelectedOnly = false;

    static {
        sContactsIdMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sContactsIdMatcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (mSelectedContactUri != null) {
                getContentResolver().delete(mSelectedContactUri, null, null);
            }
        }
    }

    /**
     * A {@link TextHighlightingAnimation} that redraws just the contact display name in a
     * list item.
     */
    private static class NameHighlightingAnimation extends TextHighlightingAnimation {
        private final ListView mListView;

        private NameHighlightingAnimation(ListView listView, int duration) {
            super(duration);
            this.mListView = listView;
        }

        /**
         * Redraws all visible items of the list corresponding to contacts
         */
        @Override
        protected void invalidate() {
            int childCount = mListView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View itemView = mListView.getChildAt(i);
                if (itemView instanceof ContactListItemView) {
                    final ContactListItemView view = (ContactListItemView)itemView;
                    view.getNameTextView().invalidate();
                }
            }
        }

        @Override
        protected void onAnimationStarted() {
            mListView.setScrollingCacheEnabled(false);
        }

        @Override
        protected void onAnimationEnded() {
            mListView.setScrollingCacheEnabled(true);
        }
    }

    // The size of a home screen shortcut icon.
    private int mIconSize;
    private ContactsPreferences mContactsPrefs;
    public int mDisplayOrder;
    private int mSortOrder;
    public boolean mHighlightWhenScrolling;
    public TextHighlightingAnimation mHighlightingAnimation;
    private SearchEditText mSearchEditText;

    /**
     * An approximation of the background color of the pinned header. This color
     * is used when the pinned header is being pushed up.  At that point the header
     * "fades away".  Rather than computing a faded bitmap based on the 9-patch
     * normally used for the background, we will use a solid color, which will
     * provide better performance and reduced complexity.
     */
    public int mPinnedHeaderBackgroundColor;

    private ContentObserver mProviderStatusObserver = new ContentObserver(new Handler()) {

        @Override
        public void onChange(boolean selfChange) {
            checkProviderState(true);
        }
    };

    public OnClickListener mCheckBoxClickerListener = new OnClickListener () {
        public void onClick(View v) {
            final ContactListItemCache cache = (ContactListItemCache) v.getTag();
            if (cache.phoneId != PhoneNumberAdapter.INVALID_PHONE_ID) {
                mUserSelection.setPhoneSelected(cache.phoneId, ((CheckBox) v).isChecked());
            } else {
                mUserSelection.setPhoneSelected(cache.phoneNumber,
                        ((CheckBox) v).isChecked());
            }
            updateWidgets(true);
        }
    };

    private ContactListConfiguration mConfig;

    public ContactsListActivity() {
        mConfig = new ContactListConfiguration(this);
    }

    /**
     * Visible for testing: makes queries run on the UI thread.
     */
    /* package */ void runQueriesSynchronously() {
        mRunQueriesSynchronously = true;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        mContactsPrefs = new ContactsPreferences(this);
        mPhotoLoader = new ContactPhotoLoader(this, R.drawable.ic_contact_list_picture);

        mQueryHandler = new QueryHandler(this);
        mJustCreated = true;
        mSyncEnabled = true;

        // Resolve the intent
        final Intent intent = getIntent();

        resolveIntent(intent);
        initContentView();
        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            initMultiPicker(intent);
        }
    }

    protected void resolveIntent(final Intent intent) {
        mConfig.setIntent(intent);

        if (!mConfig.isValid()) {           // Invalid intent
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent redirect = mConfig.getRedirectIntent();
        if (redirect != null) {             // Need to start a different activity
            startActivity(redirect);
            finish();
            return;
        }

        setTitle(mConfig.getActivityTitle());

        // This is strictly temporary. Its purpose is to allow us to refactor this class in
        // small increments.  We should expect all of these modes to go away.
        mMode = mConfig.mMode;
        mGroupName = mConfig.mGroupName;
        mQueryMode = mConfig.mQueryMode;
        mSearchMode = mConfig.mSearchMode;
        mShowSearchSnippets = mConfig.mShowSearchSnippets;
        mInitialFilter = mConfig.mInitialFilter;
        mDisplayOnlyPhones = mConfig.mDisplayOnlyPhones;
        mShortcutAction = mConfig.mShortcutAction;
        mSearchResultsMode = mConfig.mSearchResultsMode;
        mShowNumberOfContacts = mConfig.mShowNumberOfContacts;
        mGroupName = mConfig.mGroupName;
    }

    public void initContentView() {
        if (mSearchMode) {
            setContentView(R.layout.contacts_search_content);
        } else if (mSearchResultsMode) {
            setContentView(R.layout.contacts_list_search_results);
            TextView titleText = (TextView)findViewById(R.id.search_results_for);
            titleText.setText(Html.fromHtml(getString(R.string.search_results_for,
                    "<b>" + mInitialFilter + "</b>")));
        } else {
            setContentView(R.layout.contacts_list_content);
        }

        setupListView(new ContactItemListAdapter(this));
        if (mSearchMode) {
            setupSearchView();
        }

        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            ViewStub stub = (ViewStub)findViewById(R.id.footer_stub);
            if (stub != null) {
                View stubView = stub.inflate();
                mFooterView = stubView.findViewById(R.id.footer);
                mFooterView.setVisibility(View.GONE);
                Button doneButton = (Button) stubView.findViewById(R.id.done);
                doneButton.setOnClickListener(this);
                Button revertButton = (Button) stubView.findViewById(R.id.revert);
                revertButton.setOnClickListener(this);
            }
        }

        View emptyView = mList.getEmptyView();
        if (emptyView instanceof ContactListEmptyView) {
            mEmptyView = (ContactListEmptyView)emptyView;
        }
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void registerProviderStatusObserver() {
        getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI,
                false, mProviderStatusObserver);
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void unregisterProviderStatusObserver() {
        getContentResolver().unregisterContentObserver(mProviderStatusObserver);
    }

    protected void setupListView(ContactItemListAdapter adapter) {
        final ListView list = getListView();
        final LayoutInflater inflater = getLayoutInflater();

        mHighlightingAnimation =
                new NameHighlightingAnimation(list, TEXT_HIGHLIGHTING_ANIMATION_DURATION);

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        list.setDividerHeight(0);
        list.setOnCreateContextMenuListener(this);

        mAdapter = adapter;
        setListAdapter(mAdapter);

        if (list instanceof PinnedHeaderListView && mAdapter.getDisplaySectionHeadersEnabled()) {
            mPinnedHeaderBackgroundColor =
                    getResources().getColor(R.color.pinned_header_background);
            PinnedHeaderListView pinnedHeaderList = (PinnedHeaderListView)list;
            View pinnedHeader = inflater.inflate(R.layout.list_section, list, false);
            pinnedHeaderList.setPinnedHeaderView(pinnedHeader);
        }

        list.setOnScrollListener(mAdapter);
        list.setOnKeyListener(this);
        list.setOnFocusChangeListener(this);
        list.setOnTouchListener(this);

        // We manually save/restore the listview state
        list.setSaveEnabled(false);
    }

    /**
     * Configures search UI.
     */
    private void setupSearchView() {
        mSearchEditText = (SearchEditText)findViewById(R.id.search_src_text);
        mSearchEditText.addTextChangedListener(this);
        mSearchEditText.setOnEditorActionListener(this);
        mSearchEditText.setText(mInitialFilter);
    }
    public int getSummaryDisplayNameColumnIndex() {
        if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        } else {
            return SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        }
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            // TODO a better way of identifying the button
            case android.R.id.button1: {
                final int position = (Integer)v.getTag();
                Cursor c = mAdapter.getCursor();
                if (c != null) {
                    c.moveToPosition(position);
                    callContact(c);
                }
                break;
            }
            case R.id.done:
                setMultiPickerResult();
                finish();
                break;
            case R.id.revert:
                finish();
                break;
        }
    }

    /**
     * Sets the mode when the request is for "default"
     */
    private void setDefaultMode() {
        // Load the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDisplayOnlyPhones = prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPhotoLoader.stop();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mContactsPrefs.registerChangeListener(mPreferencesChangeListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterProviderStatusObserver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerProviderStatusObserver();
        mPhotoLoader.resume();

        Activity parent = getParent();

        // Do this before setting the filter. The filter thread relies
        // on some state that is initialized in setDefaultMode
        if (mMode == MODE_DEFAULT) {
            // If we're in default mode we need to possibly reset the mode due to a change
            // in the preferences activity while we weren't running
            setDefaultMode();
        }

        // See if we were invoked with a filter
        if (mSearchMode) {
            mSearchEditText.requestFocus();
        }

        if (!mSearchMode && !checkProviderState(mJustCreated)) {
            return;
        }

        if (mJustCreated) {
            // We need to start a query here the first time the activity is launched, as long
            // as we aren't doing a filter.
            startQuery();
        }
        mJustCreated = false;
        mSearchInitiated = false;
    }

    /**
     * Obtains the contacts provider status and configures the UI accordingly.
     *
     * @param loadData true if the method needs to start a query when the
     *            provider is in the normal state
     * @return true if the provider status is normal
     */
    private boolean checkProviderState(boolean loadData) {
        View importFailureView = findViewById(R.id.import_failure);
        if (importFailureView == null) {
            return true;
        }

        TextView messageView = (TextView) findViewById(R.id.emptyText);

        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = getContentResolver().query(ProviderStatus.CONTENT_URI,
                new String[] { ProviderStatus.STATUS, ProviderStatus.DATA1 }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(0);
                    if (status != mProviderStatus) {
                        mProviderStatus = status;
                        switch (status) {
                            case ProviderStatus.STATUS_NORMAL:
                                mAdapter.notifyDataSetInvalidated();
                                if (loadData) {
                                    startQuery();
                                }
                                break;

                            case ProviderStatus.STATUS_CHANGING_LOCALE:
                                messageView.setText(R.string.locale_change_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADING:
                                messageView.setText(R.string.upgrade_in_progress);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;

                            case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                                long size = cursor.getLong(1);
                                String message = getResources().getString(
                                        R.string.upgrade_out_of_memory, new Object[] {size});
                                messageView.setText(message);
                                configureImportFailureView(importFailureView);
                                mAdapter.changeCursor(null);
                                mAdapter.notifyDataSetInvalidated();
                                break;
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }

        importFailureView.setVisibility(
                mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
                        ? View.VISIBLE
                        : View.GONE);
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    private void configureImportFailureView(View importFailureView) {

        OnClickListener listener = new OnClickListener(){

            public void onClick(View v) {
                switch(v.getId()) {
                    case R.id.import_failure_uninstall_apps: {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                        break;
                    }
                    case R.id.import_failure_retry_upgrade: {
                        // Send a provider status update, which will trigger a retry
                        ContentValues values = new ContentValues();
                        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
                        getContentResolver().update(ProviderStatus.CONTENT_URI, values, null, null);
                        break;
                    }
                }
            }};

        Button uninstallApps = (Button) findViewById(R.id.import_failure_uninstall_apps);
        uninstallApps.setOnClickListener(listener);

        Button retryUpgrade = (Button) findViewById(R.id.import_failure_retry_upgrade);
        retryUpgrade.setOnClickListener(listener);
    }

    public String getTextFilter() {
        if (mSearchEditText != null) {
            return mSearchEditText.getText().toString();
        }
        return null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (!checkProviderState(false)) {
            return;
        }

        // The cursor was killed off in onStop(), so we need to get a new one here
        // We do not perform the query if a filter is set on the list because the
        // filter will cause the query to happen anyway
        if (TextUtils.isEmpty(getTextFilter())) {
            startQuery();
        } else {
            // Run the filtered query on the adapter
            mAdapter.onContentChanged();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the QueryHandler has run
        if (mList != null) {
            icicle.putParcelable(LIST_STATE_KEY, mList.onSaveInstanceState());
            if (mMode == MODE_PICK_MULTIPLE_PHONES && mUserSelection != null) {
                mUserSelection.saveInstanceState(icicle);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mListState = icicle.getParcelable(LIST_STATE_KEY);
        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            mUserSelection = new UserSelection(icicle);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        mContactsPrefs.unregisterChangeListener();
        mAdapter.changeCursor(null);

        if (mMode == MODE_QUERY) {
            // Make sure the search box is closed
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchManager.stopSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            final MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.pick, menu);
            return true;
        }

        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the options menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return false;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            if (mShowSelectedOnly) {
                menu.findItem(R.id.menu_display_selected).setVisible(false);
                menu.findItem(R.id.menu_display_all).setVisible(true);
                menu.findItem(R.id.menu_select_all).setVisible(false);
                menu.findItem(R.id.menu_select_none).setVisible(false);
                return true;
            }
            menu.findItem(R.id.menu_display_all).setVisible(false);
            menu.findItem(R.id.menu_display_selected).setVisible(true);
            if (mUserSelection.isAllSelected()) {
                menu.findItem(R.id.menu_select_all).setVisible(false);
                menu.findItem(R.id.menu_select_none).setVisible(true);
            } else {
                menu.findItem(R.id.menu_select_all).setVisible(true);
                menu.findItem(R.id.menu_select_none).setVisible(false);
            }
            return true;
        }

        final boolean defaultMode = (mMode == MODE_DEFAULT);
        menu.findItem(R.id.menu_display_groups).setVisible(defaultMode);
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
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
                return true;
            }
            case R.id.menu_select_all: {
                mUserSelection.setAllPhonesSelected(true);
                checkAll(true);
                updateWidgets(true);
                return true;
            }
            case R.id.menu_select_none: {
                mUserSelection.setAllPhonesSelected(false);
                checkAll(false);
                updateWidgets(true);
                return true;
            }
            case R.id.menu_display_selected: {
                mShowSelectedOnly = true;
                startQuery();
                return true;
            }
            case R.id.menu_display_all: {
                mShowSelectedOnly = false;
                startQuery();
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
            return;
        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
                if ((mMode & MODE_MASK_PICKER) != 0) {
                    Bundle extras = null;
                    if (mMode == MODE_PICK_MULTIPLE_PHONES) {
                        extras = getIntent().getExtras();
                        if (extras == null) {
                            extras = new Bundle();
                        }
                        mUserSelection.fillSelectionForSearchMode(extras);
                    }
                    ContactsSearchManager.startSearchForResult(this, initialQuery,
                            SUBACTIVITY_FILTER, extras);
                } else {
                    ContactsSearchManager.startSearch(this, initialQuery);
                }
            }
        }
    }

    /**
     * Performs filtering of the list based on the search query entered in the
     * search text edit.
     */
    protected void onSearchTextChanged() {
        Filter filter = mAdapter.getFilter();
        filter.filter(getTextFilter());
    }

    /**
     * Starts a new activity that will run a search query and display search results.
     */
    protected void doSearch() {
        String query = getTextFilter();
        if (TextUtils.isEmpty(query)) {
            return;
        }

        Intent intent = new Intent(this, SearchResultsActivity.class);
        Intent originalIntent = getIntent();
        Bundle originalExtras = originalIntent.getExtras();
        if (originalExtras != null) {
            intent.putExtras(originalExtras);
        }

        intent.putExtra(SearchManager.QUERY, query);
        if ((mMode & MODE_MASK_PICKER) != 0) {
            intent.setAction(ACTION_SEARCH_INTERNAL);
            intent.putExtra(SHORTCUT_ACTION_KEY, mShortcutAction);
            if (mShortcutAction != null) {
                if (Intent.ACTION_CALL.equals(mShortcutAction)
                        || Intent.ACTION_SENDTO.equals(mShortcutAction)) {
                    intent.putExtra(Insert.PHONE, query);
                }
            } else {
                switch (mQueryMode) {
                    case QUERY_MODE_MAILTO:
                        intent.putExtra(Insert.EMAIL, query);
                        break;
                    case QUERY_MODE_TEL:
                        intent.putExtra(Insert.PHONE, query);
                        break;
                }
            }
            startActivityForResult(intent, SUBACTIVITY_SEARCH);
        } else {
            intent.setAction(Intent.ACTION_SEARCH);
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
                sLookupProjection, getContactSelection(), null, null);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    returnPickerResult(null, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
                            data.getData(), (mMode & MODE_MASK_PICKER) != 0
                            ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0);
                }
                break;

            case SUBACTIVITY_VIEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    mAdapter.notifyDataSetChanged();
                }
                break;

            case SUBACTIVITY_DISPLAY_GROUP:
                // Mark as just created so we re-run the view query
                mJustCreated = true;
                break;

            case SUBACTIVITY_FILTER:
            case SUBACTIVITY_SEARCH:
                // Pass through results of filter or search UI
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                    finish();
                } else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
                    // Finish the activity if the sub activity was canceled as back key is used
                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
                    finish();
                }
                break;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the context menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return;
        }

        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        long id = info.id;
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
        long rawContactId = ContactsUtils.queryForRawContactId(getContentResolver(), id);
        Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);

        // Setup the menu header
        menu.setHeaderTitle(cursor.getString(getSummaryDisplayNameColumnIndex()));

        // View contact details
        final Intent viewContactIntent = new Intent(Intent.ACTION_VIEW, contactUri);
        StickyTabs.setTab(viewContactIntent, getIntent());
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
                .setIntent(viewContactIntent);

        if (cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0) {
            // Calling contact
            menu.add(0, MENU_ITEM_CALL, 0, getString(R.string.menu_call));
            // Send SMS item
            menu.add(0, MENU_ITEM_SEND_SMS, 0, getString(R.string.menu_sendSMS));
        }

        // Star toggling
        int starState = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
        if (starState == 0) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact)
                .setIntent(new Intent(Intent.ACTION_EDIT, rawContactUri));
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        switch (item.getItemId()) {
            case MENU_ITEM_TOGGLE_STAR: {
                // Toggle the star
                ContentValues values = new ContentValues(1);
                values.put(Contacts.STARRED, cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX) == 0 ? 1 : 0);
                final Uri selectedUri = this.getContactUri(info.position);
                getContentResolver().update(selectedUri, values, null, null);
                return true;
            }

            case MENU_ITEM_CALL: {
                callContact(cursor);
                return true;
            }

            case MENU_ITEM_SEND_SMS: {
                smsContact(cursor);
                return true;
            }

            case MENU_ITEM_DELETE: {
                doContactDelete(getContactUri(info.position));
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Event handler for the use case where the user starts typing without
     * bringing up the search UI first.
     */
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0 && !mSearchInitiated) {
            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar != 0) {
                mSearchInitiated = true;
                startSearch(new String(new int[]{unicodeChar}, 0, 1), false, null, false);
                return true;
            }
        }
        return false;
    }

    /**
     * Event handler for search UI.
     */
    public void afterTextChanged(Editable s) {
        onSearchTextChanged();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /**
     * Event handler for search UI.
     */
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            if (TextUtils.isEmpty(getTextFilter())) {
                finish();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (callSelection()) {
                    return true;
                }
                break;
            }

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
        if ((mMode & MODE_MASK_PICKER) != 0) {
            return false;
        }

        final int position = getListView().getSelectedItemPosition();
        if (position != ListView.INVALID_POSITION) {
            Uri contactUri = getContactUri(position);
            if (contactUri != null) {
                doContactDelete(contactUri);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (mMode == MODE_PICK_MULTIPLE_PHONES) {
            setMultiPickerResult();
        }
        super.onBackPressed();
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

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == getListView() && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (mSearchMode && keyCode == KeyEvent.KEYCODE_BACK && TextUtils.isEmpty(getTextFilter())) {
            hideSoftKeyboard();
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();

        onListItemClick(position, id);
    }

    protected void onListItemClick(int position, long id) {
        if (mSearchMode && mAdapter.isSearchAllContactsItemPosition(position)) {
            doSearch();
        } else if (mMode == MODE_INSERT_OR_EDIT_CONTACT || mMode == MODE_QUERY_PICK_TO_EDIT) {
            Intent intent;
            if (position == 0 && !mSearchMode && mMode != MODE_QUERY_PICK_TO_EDIT) {
                intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            } else {
                intent = new Intent(Intent.ACTION_EDIT, getSelectedUri(position));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.putExtra(KEY_PICKER_MODE, (mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER);

            startActivity(intent);
            finish();
        } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW
                && position == 0) {
            Intent newContact = new Intent(Intents.Insert.ACTION, Contacts.CONTENT_URI);
            startActivityForResult(newContact, SUBACTIVITY_NEW_CONTACT);
        } else if (id > 0) {
            final Uri uri = getSelectedUri(position);
            if ((mMode & MODE_MASK_PICKER) == 0) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                StickyTabs.setTab(intent, getIntent());
                startActivityForResult(intent, SUBACTIVITY_VIEW_CONTACT);
            } else if (mMode == MODE_QUERY_PICK_TO_VIEW) {
                // Started with query that should launch to view contact
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                finish();
            } else if (mMode == MODE_PICK_PHONE || mMode == MODE_QUERY_PICK_PHONE) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX), uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if ((mMode & MODE_MASK_PICKER) != 0) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(getSummaryDisplayNameColumnIndex()), uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if (mMode == MODE_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_PHONE) {
                returnPickerResult(null, null, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } else {
            signalError();
        }
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mList.getWindowToken(), 0);
    }

    /**
     * @param selectedUri In most cases, this should be a lookup {@link Uri}, possibly
     *            generated through {@link Contacts#getLookupUri(long, String)}.
     */
    protected void returnPickerResult(Cursor c, String name, Uri selectedUri, int uriPerms) {
        final Intent intent = new Intent();

        if (mShortcutAction != null) {
            Intent shortcutIntent;
            if (Intent.ACTION_VIEW.equals(mShortcutAction)) {
                // This is a simple shortcut to view a contact.
                shortcutIntent = new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT);
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                shortcutIntent.setData(selectedUri);
                shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_MODE,
                        ContactsContract.QuickContact.MODE_LARGE);
                shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_EXCLUDE_MIMES,
                        (String[]) null);

                final Bitmap icon = framePhoto(loadContactPhoto(selectedUri, null));
                if (icon != null) {
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, scaleToAppIconSize(icon));
                } else {
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this,
                                    R.drawable.ic_launcher_shortcut_contact));
                }
            } else {
                // This is a direct dial or sms shortcut.
                String number = c.getString(PHONE_NUMBER_COLUMN_INDEX);
                int type = c.getInt(PHONE_TYPE_COLUMN_INDEX);
                String scheme;
                int resid;
                if (Intent.ACTION_CALL.equals(mShortcutAction)) {
                    scheme = Constants.SCHEME_TEL;
                    resid = R.drawable.badge_action_call;
                } else {
                    scheme = Constants.SCHEME_SMSTO;
                    resid = R.drawable.badge_action_sms;
                }

                // Make the URI a direct tel: URI so that it will always continue to work
                Uri phoneUri = Uri.fromParts(scheme, number, null);
                shortcutIntent = new Intent(mShortcutAction, phoneUri);

                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                        generatePhoneNumberIcon(selectedUri, type, resid));
            }
            shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            setResult(RESULT_OK, intent);
        } else {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            intent.addFlags(uriPerms);
            setResult(RESULT_OK, intent.setData(selectedUri));
        }
        finish();
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.quickcontact_badge);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top,
                width - padding.right, height - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return b;
    }

    /**
     * Generates a phone number shortcut icon. Adds an overlay describing the type of the phone
     * number, and if there is a photo also adds the call action icon.
     *
     * @param lookupUri The person the phone number belongs to
     * @param type The type of the phone number
     * @param actionResId The ID for the action resource
     * @return The bitmap for the icon
     */
    private Bitmap generatePhoneNumberIcon(Uri lookupUri, int type, int actionResId) {
        final Resources r = getResources();
        boolean drawPhoneOverlay = true;
        final float scaleDensity = getResources().getDisplayMetrics().scaledDensity;

        Bitmap photo = loadContactPhoto(lookupUri, null);
        if (photo == null) {
            // If there isn't a photo use the generic phone action icon instead
            Bitmap phoneIcon = getPhoneActionIcon(r, actionResId);
            if (phoneIcon != null) {
                photo = phoneIcon;
                drawPhoneOverlay = false;
            } else {
                return null;
            }
        }

        // Setup the drawing classes
        Bitmap icon = createShortcutBitmap();
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0,0, photo.getWidth(),photo.getHeight());
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        // Create an overlay for the phone number type
        String overlay = null;
        switch (type) {
            case Phone.TYPE_HOME:
                overlay = getString(R.string.type_short_home);
                break;

            case Phone.TYPE_MOBILE:
                overlay = getString(R.string.type_short_mobile);
                break;

            case Phone.TYPE_WORK:
                overlay = getString(R.string.type_short_work);
                break;

            case Phone.TYPE_PAGER:
                overlay = getString(R.string.type_short_pager);
                break;

            case Phone.TYPE_OTHER:
                overlay = getString(R.string.type_short_other);
                break;
        }
        if (overlay != null) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setTextSize(20.0f * scaleDensity);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
            textPaint.setShadowLayer(3f, 1, 1, r.getColor(R.color.textColorIconOverlayShadow));
            canvas.drawText(overlay, 2 * scaleDensity, 16 * scaleDensity, textPaint);
        }

        // Draw the phone action icon as an overlay
        if (ENABLE_ACTION_ICON_OVERLAYS && drawPhoneOverlay) {
            Bitmap phoneIcon = getPhoneActionIcon(r, actionResId);
            if (phoneIcon != null) {
                src.set(0, 0, phoneIcon.getWidth(), phoneIcon.getHeight());
                int iconWidth = icon.getWidth();
                dst.set(iconWidth - ((int) (20 * scaleDensity)), -1,
                        iconWidth, ((int) (19 * scaleDensity)));
                canvas.drawBitmap(phoneIcon, src, dst, photoPaint);
            }
        }

        return icon;
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {
        // Setup the drawing classes
        Bitmap icon = createShortcutBitmap();
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0,0, photo.getWidth(),photo.getHeight());
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }

    private Bitmap createShortcutBitmap() {
        return Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
    }

    /**
     * Returns the icon for the phone call action.
     *
     * @param r The resources to load the icon from
     * @param resId The resource ID to load
     * @return the icon for the phone call action
     */
    private Bitmap getPhoneActionIcon(Resources r, int resId) {
        Drawable phoneIcon = r.getDrawable(resId);
        if (phoneIcon instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) phoneIcon;
            return bd.getBitmap();
        } else {
            return null;
        }
    }

    protected Uri getUriToQuery() {
        switch(mMode) {
            case MODE_FREQUENT:
            case MODE_STARRED:
                return Contacts.CONTENT_URI;

            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:{
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_STREQUENT: {
                return Contacts.CONTENT_STREQUENT_URI;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return People.CONTENT_URI;
            }
            case MODE_PICK_MULTIPLE_PHONES:
            case MODE_PICK_PHONE: {
                return buildSectionIndexerUri(Phone.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return Phones.CONTENT_URI;
            }
            case MODE_PICK_POSTAL: {
                return buildSectionIndexerUri(StructuredPostal.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContactMethods.CONTENT_URI;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return Uri.withAppendedPath(Email.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                }
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return getContactFilterUri(mInitialFilter);
            }
            case MODE_QUERY_PICK_PHONE: {
                return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                        Uri.encode(mInitialFilter));
            }
            case MODE_GROUP: {
                return Uri.withAppendedPath(Contacts.CONTENT_GROUP_URI, mGroupName);
            }
            default: {
                throw new IllegalStateException("Can't generate URI: Unsupported Mode.");
            }
        }
    }

    /**
     * Build the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position, using {@link #mAdapter}.
     */
    private Uri getContactUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor == null) {
            return null;
        }

        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                final long personId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                return ContentUris.withAppendedId(People.CONTENT_URI, personId);
            }

            default: {
                // Build and return soft, lookup reference
                final long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                final String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                return Contacts.getLookupUri(contactId, lookupKey);
            }
        }
    }

    /**
     * Build the {@link Uri} for the given {@link ListView} position, which can
     * be used as result when in {@link #MODE_MASK_PICKER} mode.
     */
    protected Uri getSelectedUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final long id = mAdapter.getItemId(position);
        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return ContentUris.withAppendedId(People.CONTENT_URI, id);
            }
            case MODE_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return ContentUris.withAppendedId(Phones.CONTENT_URI, id);
            }
            case MODE_PICK_POSTAL: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContentUris.withAppendedId(ContactMethods.CONTENT_URI, id);
            }
            default: {
                return getContactUri(position);
            }
        }
    }

    String[] getProjectionForQuery() {
        switch(mMode) {
            case MODE_STREQUENT:
            case MODE_FREQUENT:
            case MODE_STARRED:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_GROUP:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT: {
                return mSearchMode
                        ? CONTACTS_SUMMARY_FILTER_PROJECTION
                        : CONTACTS_SUMMARY_PROJECTION;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return CONTACTS_SUMMARY_FILTER_PROJECTION;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return LEGACY_PEOPLE_PROJECTION ;
            }
            case MODE_QUERY_PICK_PHONE:
            case MODE_PICK_MULTIPLE_PHONES:
            case MODE_PICK_PHONE: {
                return PHONES_PROJECTION;
            }
            case MODE_LEGACY_PICK_PHONE: {
                return LEGACY_PHONES_PROJECTION;
            }
            case MODE_PICK_POSTAL: {
                return POSTALS_PROJECTION;
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return LEGACY_POSTALS_PROJECTION;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL;
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return PHONES_PROJECTION;
                }
                break;
            }
        }

        // Default to normal aggregate projection
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private Bitmap loadContactPhoto(Uri selectedUri, BitmapFactory.Options options) {
        Uri contactUri = null;
        if (Contacts.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(selectedUri))) {
            // TODO we should have a "photo" directory under the lookup URI itself
            contactUri = Contacts.lookupContact(getContentResolver(), selectedUri);
        } else {

            Cursor cursor = getContentResolver().query(selectedUri,
                    new String[] { Data.CONTACT_ID }, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        Cursor cursor = null;
        Bitmap bm = null;

        try {
            Uri photoUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY);
            cursor = getContentResolver().query(photoUri, new String[] {Photo.PHOTO},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                bm = ContactsUtils.loadContactPhoto(cursor, 0, options);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (bm == null) {
            final int[] fallbacks = {
                R.drawable.ic_contact_picture,
                R.drawable.ic_contact_picture_2,
                R.drawable.ic_contact_picture_3
            };
            bm = BitmapFactory.decodeResource(getResources(),
                    fallbacks[new Random().nextInt(fallbacks.length)]);
        }

        return bm;
    }

    /**
     * Return the selection arguments for a default query based on the
     * {@link #mDisplayOnlyPhones} flag.
     */
    private String getContactSelection() {
        if (mDisplayOnlyPhones) {
            return CLAUSE_ONLY_VISIBLE + " AND " + CLAUSE_ONLY_PHONES;
        } else {
            return CLAUSE_ONLY_VISIBLE;
        }
    }

    protected Uri getContactFilterUri(String filter) {
        Uri baseUri;
        if (!TextUtils.isEmpty(filter)) {
            baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }

        if (mAdapter.getDisplaySectionHeadersEnabled()) {
            return buildSectionIndexerUri(baseUri);
        } else {
            return baseUri;
        }
    }

    private Uri getPeopleFilterUri(String filter) {
        if (!TextUtils.isEmpty(filter)) {
            return Uri.withAppendedPath(People.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            return People.CONTENT_URI;
        }
    }

    private static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }


    protected String getSortOrder(String[] projectionType) {
        String sortKey;
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            sortKey = Contacts.SORT_KEY_PRIMARY;
        } else {
            sortKey = Contacts.SORT_KEY_ALTERNATIVE;
        }
        switch (mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON:
                sortKey = Contacts.DISPLAY_NAME;
                break;
            case MODE_LEGACY_PICK_PHONE:
                sortKey = People.DISPLAY_NAME;
                break;
        }
        return sortKey;
    }

    public void startQuery() {
        if (mSearchResultsMode) {
            TextView foundContactsText = (TextView)findViewById(R.id.search_results_found);
            foundContactsText.setText(R.string.search_results_searching);
        }

        if (mEmptyView != null) {
            mEmptyView.hide();
        }

        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        mSortOrder = mContactsPrefs.getSortOrder();
        mDisplayOrder = mContactsPrefs.getDisplayOrder();

        // When sort order and display order contradict each other, we want to
        // highlight the part of the name used for sorting.
        mHighlightWhenScrolling = false;
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            mHighlightWhenScrolling = true;
        } else if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mHighlightWhenScrolling = true;
        }

        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(getTextFilter())) {
            mAdapter.changeCursor(new MatrixCursor(projection));
            return;
        }

        String callingPackage = getCallingPackage();
        Uri uri = getUriToQuery();
        if (!TextUtils.isEmpty(callingPackage)) {
            uri = uri.buildUpon()
                    .appendQueryParameter(ContactsContract.REQUESTING_PACKAGE_PARAM_KEY,
                            callingPackage)
                    .build();
        }

        startQuery(uri, projection);
    }

    protected void startQuery(Uri uri, String[] projection) {
        // Kick off the new query
        switch (mMode) {
            case MODE_GROUP:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, getContactSelection(),
                        null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        People.DISPLAY_NAME);
                break;
            }
            case MODE_PICK_POSTAL:
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_PHONE:
            case MODE_QUERY_PICK_TO_VIEW:
            case MODE_QUERY_PICK_TO_EDIT: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        getSortOrder(projection));
                break;
            }

            case MODE_STARRED:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
                break;

            case MODE_FREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
                break;

            case MODE_STREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null, null);
                break;

            case MODE_PICK_MULTIPLE_PHONES:
                // Filter unknown phone numbers first.
                mPhoneNumberAdapter.doFilter(null, mShowSelectedOnly);
                if (mShowSelectedOnly) {
                    StringBuilder idSetBuilder = new StringBuilder();
                    Iterator<Long> itr = mUserSelection.getSelectedPhonIds();
                    if (itr.hasNext()) {
                        idSetBuilder.append(Long.toString(itr.next()));
                    }
                    while (itr.hasNext()) {
                        idSetBuilder.append(',');
                        idSetBuilder.append(Long.toString(itr.next()));
                    }
                    String whereClause = Phone._ID + " IN (" + idSetBuilder.toString() + ")";
                    mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                            projection, whereClause, null, getSortOrder(projection));
                    break;
                }
                // Fall through For other cases
            case MODE_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, null, null, Phones.DISPLAY_NAME);
                break;

            case MODE_LEGACY_PICK_POSTAL:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        ContactMethods.KIND + "=" + android.provider.Contacts.KIND_POSTAL, null,
                        ContactMethods.DISPLAY_NAME);
                break;
        }
    }

    protected void startQuery(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, selectionArgs,
                sortOrder);
    }

    /**
     * Called from a background thread to do the filter and return the resulting cursor.
     *
     * @param filter the text that was entered to filter on
     * @return a cursor with the results of the filter
     */
    public Cursor doFilter(String filter) {
        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(getTextFilter())) {
            return new MatrixCursor(projection);
        }

        final ContentResolver resolver = getContentResolver();
        switch (mMode) {
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        getContactSelection(), null, getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return resolver.query(getPeopleFilterUri(filter), projection, null, null,
                        People.DISPLAY_NAME);
            }

            case MODE_STARRED: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
            }

            case MODE_FREQUENT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
            }

            case MODE_STREQUENT: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_STREQUENT_FILTER_URI,
                            Uri.encode(filter));
                } else {
                    uri = Contacts.CONTENT_STREQUENT_URI;
                }
                return resolver.query(uri, projection, null, null, null);
            }

            case MODE_PICK_MULTIPLE_PHONES:
                // Filter phone numbers as well.
                mPhoneNumberAdapter.doFilter(filter, mShowSelectedOnly);
                // Fall through
            case MODE_PICK_PHONE: {
                Uri uri = getUriToQuery();
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
                }
                return resolver.query(uri, projection, CLAUSE_ONLY_VISIBLE, null,
                        getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PHONE: {
                //TODO: Support filtering here (bug 2092503)
                break;
            }
        }
        throw new UnsupportedOperationException("filtering not allowed in mode " + mMode);
    }


    /**
     * Calls the currently selected list item.
     * @return true if the call was initiated, false otherwise
     */
    boolean callSelection() {
        ListView list = getListView();
        if (list.hasFocus()) {
            Cursor cursor = (Cursor) list.getSelectedItem();
            return callContact(cursor);
        }
        return false;
    }

    boolean callContact(Cursor cursor) {
        return callOrSmsContact(cursor, false /*call*/);
    }

    boolean smsContact(Cursor cursor) {
        return callOrSmsContact(cursor, true /*sms*/);
    }

    /**
     * Calls the contact which the cursor is point to.
     * @return true if the call was initiated, false otherwise
     */
    boolean callOrSmsContact(Cursor cursor, boolean sendSms) {
        if (cursor == null) {
            return false;
        }

        switch (mMode) {
            case MODE_PICK_PHONE:
            case MODE_LEGACY_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                String phone = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                if (sendSms) {
                    ContactsUtils.initiateSms(this, phone);
                } else {
                    ContactsUtils.initiateCall(this, phone);
                }
                return true;
            }

            case MODE_PICK_POSTAL:
            case MODE_LEGACY_PICK_POSTAL: {
                return false;
            }

            default: {

                boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                if (!hasPhone) {
                    // There is no phone number.
                    signalError();
                    return false;
                }

                String phone = null;
                Cursor phonesCursor = null;
                phonesCursor = queryPhoneNumbers(cursor.getLong(SUMMARY_ID_COLUMN_INDEX));
                if (phonesCursor == null || phonesCursor.getCount() == 0) {
                    // No valid number
                    signalError();
                    return false;
                } else if (phonesCursor.getCount() == 1) {
                    // only one number, call it.
                    phone = phonesCursor.getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                } else {
                    phonesCursor.moveToPosition(-1);
                    while (phonesCursor.moveToNext()) {
                        if (phonesCursor.getInt(phonesCursor.
                                getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                            // Found super primary, call it.
                            phone = phonesCursor.
                            getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                            break;
                        }
                    }
                }

                if (phone == null) {
                    // Display dialog to choose a number to call.
                    PhoneDisambigDialog phoneDialog = new PhoneDisambigDialog(
                            this, phonesCursor, sendSms, StickyTabs.getTab(getIntent()));
                    phoneDialog.show();
                } else {
                    if (sendSms) {
                        ContactsUtils.initiateSms(this, phone);
                    } else {
                        StickyTabs.saveTab(this, getIntent());
                        ContactsUtils.initiateCall(this, phone);
                    }
                }
            }
        }
        return true;
    }

    // TODO: eliminate
    @Deprecated
    private Cursor queryPhoneNumbers(long contactId) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        Cursor c = getContentResolver().query(dataUri,
                new String[] {Phone._ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE, Phone.TYPE, Phone.LABEL},
                Data.MIMETYPE + "=?", new String[] {Phone.CONTENT_ITEM_TYPE}, null);
        if (c != null) {
            if (c.moveToFirst()) {
                return c;
            }
            c.close();
        }
        return null;
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getString(zeroResourceId);
        } else {
            String format = getResources().getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    /**
     * Signal an error to the user.
     */
    void signalError() {
        //TODO play an error beep or something...
    }

    Cursor getItemForView(View view) {
        ListView listView = getListView();
        int index = listView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) listView.getAdapter().getItem(index);
    }

    private void initMultiPicker(final Intent intent) {
        final Handler handler = new Handler();
        // TODO : Shall we still show the progressDialog in search mode.
        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(getText(R.string.adding_recipients));
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);

        final Runnable showProgress = new Runnable() {
            public void run() {
                progressDialog.show();
            }
        };
        handler.postDelayed(showProgress, 1);

        new Thread(new Runnable() {
            public void run() {
                try {
                    loadSelectionFromIntent(intent);
                } finally {
                    handler.removeCallbacks(showProgress);
                    progressDialog.dismiss();
                }
                final Runnable populateWorker = new Runnable() {
                    public void run() {
                        if (mAdapter != null) {
                            mAdapter.notifyDataSetChanged();
                        }
                        updateWidgets(false);
                    }
                };
                handler.post(populateWorker);
            }
        }).start();
    }

    private void getPhoneNumbersOrIdsFromURIs(final Parcelable[] uris,
            final List<String> phoneNumbers, final List<Long> phoneIds) {
        if (uris != null) {
            for (Parcelable paracelable : uris) {
                Uri uri = (Uri) paracelable;
                if (uri == null) continue;
                String scheme = uri.getScheme();
                if (phoneNumbers != null && TEL_SCHEME.equals(scheme)) {
                    phoneNumbers.add(uri.getSchemeSpecificPart());
                } else if (phoneIds != null && CONTENT_SCHEME.equals(scheme)) {
                    phoneIds.add(ContentUris.parseId(uri));
                }
            }
        }
    }

    private void loadSelectionFromIntent(Intent intent) {
        Parcelable[] uris = intent.getParcelableArrayExtra(Intents.EXTRA_PHONE_URIS);
        ArrayList<String> phoneNumbers = new ArrayList<String>();
        ArrayList<Long> phoneIds = new ArrayList<Long>();
        ArrayList<String> selectedPhoneNumbers = null;
        if (mSearchMode) {
            // All selection will be read from EXTRA_SELECTION
            getPhoneNumbersOrIdsFromURIs(uris, phoneNumbers, null);
            uris = intent.getParcelableArrayExtra(UserSelection.EXTRA_SELECTION);
            if (uris != null) {
                selectedPhoneNumbers = new ArrayList<String>();
                getPhoneNumbersOrIdsFromURIs(uris, selectedPhoneNumbers, phoneIds);
            }
        } else {
            getPhoneNumbersOrIdsFromURIs(uris, phoneNumbers, phoneIds);
            selectedPhoneNumbers = phoneNumbers;
        }
        mPhoneNumberAdapter = new PhoneNumberAdapter(this, phoneNumbers);
        mUserSelection = new UserSelection(selectedPhoneNumbers, phoneIds);
    }

    private void setMultiPickerResult() {
        setResult(RESULT_OK, mUserSelection.createSelectionIntent());
    }

    /**
     * Go through the cursor and assign the chip color to contact who has more than one phone
     * numbers.
     * Assume the cursor is sorted by CONTACT_ID.
     */
    public void updateChipColor(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            return;
        }
        mContactColor.clear();
        int backupPos = cursor.getPosition();
        cursor.moveToFirst();
        int color = 0;
        long prevContactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
        while (cursor.moveToNext()) {
            long contactId = cursor.getLong(PHONE_CONTACT_ID_COLUMN_INDEX);
            if (prevContactId == contactId) {
                if (mContactColor.indexOfKey(Long.valueOf(contactId).hashCode()) < 0) {
                    mContactColor.put(Long.valueOf(contactId).hashCode(), CHIP_COLOR_ARRAY[color]);
                    color++;
                    if (color >= CHIP_COLOR_ARRAY.length) {
                        color = 0;
                    }
                }
            }
            prevContactId = contactId;
        }
        cursor.moveToPosition(backupPos);
    }

    /**
     * Get assigned chip color resource id for a given contact, 0 is returned if there is no mapped
     * resource.
     */
    public int getChipColor(long contactId) {
        return mContactColor.get(Long.valueOf(contactId).hashCode());
    }

    private void updateWidgets(boolean changed) {
        int selected = mUserSelection.selectedCount();

        if (selected >= 1) {
            final String format =
                getResources().getQuantityString(R.plurals.multiple_picker_title, selected);
            setTitle(String.format(format, selected));
        } else {
            setTitle(getString(R.string.contactsList));
        }

        if (changed && mFooterView.getVisibility() == View.GONE) {
            mFooterView.setVisibility(View.VISIBLE);
            mFooterView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.footer_appear));
        }
    }

    private void checkAll(boolean checked) {
        final ListView listView = getListView();
        int childCount = listView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final ContactListItemView child = (ContactListItemView)listView.getChildAt(i);
            child.getCheckBoxView().setChecked(checked);
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<ContactsListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<ContactsListActivity>((ContactsListActivity) context);
        }

        @Override
        public void startQuery(int token, Object cookie, Uri uri, String[] projection,
                String selection, String[] selectionArgs, String orderBy) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && activity.mRunQueriesSynchronously) {
                Cursor cursor = getContentResolver().query(uri, projection, selection,
                        selectionArgs, orderBy);
                onQueryComplete(token, cookie, cursor);
            } else {
                super.startQuery(token, cookie, uri, projection, selection, selectionArgs, orderBy);
            }
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.onQueryComplete(cursor);
            } else {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    protected void onQueryComplete(Cursor cursor) {
        mAdapter.changeCursor(cursor);

        // Now that the cursor is populated again, it's possible to restore the list state
        if (mListState != null) {
            mList.onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

    public final static class ContactListItemCache {
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
        public TextWithHighlighting textWithHighlighting;
        public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);
        public long phoneId;
        // phoneNumber only validates when phoneId = INVALID_PHONE_ID
        public String phoneNumber;
    }

    public final static class PinnedHeaderCache {
        public TextView titleView;
        public ColorStateList textColor;
        public Drawable background;
    }

    /**
     * This class is the adapter for the phone numbers which may not be found in the contacts. It is
     * called in ContactItemListAdapter in MODE_PICK_MULTIPLE_PHONES mode and shouldn't be a adapter
     * for any View due to the missing implementation of getItem and getItemId.
     */
    public class PhoneNumberAdapter extends BaseAdapter {
        public static final long INVALID_PHONE_ID = -1;

        /** The initial phone numbers */
        private List<String> mPhoneNumbers;

        /** The phone numbers after the filtering */
        private ArrayList<String> mFilteredPhoneNumbers = new ArrayList<String>();

        private Context mContext;

        /** The position where this Adapter Phone numbers start*/
        private int mStartPos;

        public PhoneNumberAdapter(Context context, final List<String> phoneNumbers) {
            init(context, phoneNumbers);
        }

        private void init(Context context, final List<String> phoneNumbers) {
            mStartPos = (mMode & MODE_MASK_SHOW_NUMBER_OF_CONTACTS) != 0 ? 1 : 0;
            mContext = context;
            if (phoneNumbers != null) {
                mFilteredPhoneNumbers.addAll(phoneNumbers);
                mPhoneNumbers = phoneNumbers;
            } else {
                mPhoneNumbers = new ArrayList<String>();
            }
        }

        public int getCount() {
            int filteredCount = mFilteredPhoneNumbers.size();
            if (filteredCount == 0) {
                return 0;
            }
            // Count on the separator
            return 1 + filteredCount;
        }

        public Object getItem(int position) {
            // This method is not used currently.
            throw new RuntimeException("This method is not implemented");
        }

        public long getItemId(int position) {
            // This method is not used currently.
            throw new RuntimeException("This method is not implemented");
        }

        /**
         * @return the initial phone numbers, the zero length array is returned when there is no
         * initial numbers.
         */
        public final List<String> getPhoneNumbers() {
            return mPhoneNumbers;
        }

        /**
         * @return the filtered phone numbers, the zero size ArrayList is returned when there is no
         * initial numbers.
         */
        public ArrayList<String> getFilteredPhoneNumbers() {
            return mFilteredPhoneNumbers;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            int viewCount = getCount();
            if (viewCount == 0) {
                return null;
            }
            // Separator
            if (position == mStartPos) {
                TextView view;
                if (convertView != null && convertView instanceof TextView) {
                    view = (TextView) convertView;
                } else {
                    LayoutInflater inflater =
                        (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = (TextView) inflater.inflate(R.layout.list_separator, parent, false);
                }
                view.setText(R.string.unknown_contacts_separator);
                return view;
            }
            // PhoneNumbers start from position of startPos + 1
            if (position >= mStartPos + 1 && position < mStartPos + viewCount) {
                View view;
                if (convertView != null && convertView.getTag() != null &&
                        convertView.getTag() instanceof ContactListItemCache) {
                    view = convertView;
                } else {
                    view = mAdapter.newView(mContext, null, parent);
                }
                bindView(view, mFilteredPhoneNumbers.get(position - 1 - mStartPos));
                return view;
            }
            return null;
        }

        @Override
        public int getItemViewType(int position) {
            return position == mStartPos ? IGNORE_ITEM_VIEW_TYPE : super.getItemViewType(position);
        }

        private void bindView(View view, final String label) {
            ContactListItemView itemView = (ContactListItemView) view;
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();
            itemView.getNameTextView().setText(label);
            CheckBox checkBox = itemView.getCheckBoxView();
            checkBox.setChecked(mUserSelection.isSelected(label));
            itemView.getChipView().setBackgroundResource(0);
            cache.phoneId = INVALID_PHONE_ID;
            cache.phoneNumber = label;
            checkBox.setTag(cache);
        }

        public void doFilter(final String constraint, boolean selectedOnly) {
            if (mPhoneNumbers == null) {
                return;
            }
            mFilteredPhoneNumbers.clear();
            for (String number : mPhoneNumbers) {
                if (selectedOnly && !mUserSelection.isSelected(number) ||
                        !TextUtils.isEmpty(constraint) && !number.startsWith(constraint)) {
                    continue;
                }
                mFilteredPhoneNumbers.add(number);
            }
        }
    }

    /**
     * This class is used to keep the user's selection in MODE_PICK_MULTIPLE_PHONES mode.
     */
    public class UserSelection {
        public static final String EXTRA_SELECTION =
            "com.android.contacts.ContactsListActivity.UserSelection.extra.SELECTION";
        private static final String SELECTED_UNKNOWN_PHONES_KEY = "selected_unknown_phones";
        private static final String SELECTED_PHONE_IDS_KEY = "selected_phone_id";

        /** The PHONE_ID of selected number in user contacts*/
        private HashSet<Long> mSelectedPhoneIds = new HashSet<Long>();

        /** The selected phone numbers in the PhoneNumberAdapter */
        private HashSet<String> mSelectedPhoneNumbers = new HashSet<String>();

        /**
         * @param phoneNumbers the phone numbers are selected.
         */
        public UserSelection(final List<String> phoneNumbers, final List<Long> phoneIds) {
            init(phoneNumbers, phoneIds);
        }

        /**
         * Creates from a instance state.
         */
        public UserSelection (Bundle icicle) {
            init(icicle.getStringArray(SELECTED_UNKNOWN_PHONES_KEY),
                    icicle.getLongArray(SELECTED_PHONE_IDS_KEY));
        }

        public void saveInstanceState(Bundle icicle) {
            int selectedUnknownsCount = mSelectedPhoneNumbers.size();
            if (selectedUnknownsCount > 0) {
                String[] selectedUnknows = new String[selectedUnknownsCount];
                icicle.putStringArray(SELECTED_UNKNOWN_PHONES_KEY,
                        mSelectedPhoneNumbers.toArray(selectedUnknows));
            }
            int selectedKnownsCount = mSelectedPhoneIds.size();
            if (selectedKnownsCount > 0) {
                long[] selectedPhoneIds = new long [selectedKnownsCount];
                int index = 0;
                for (Long phoneId : mSelectedPhoneIds) {
                    selectedPhoneIds[index++] = phoneId.longValue();
                }
                icicle.putLongArray(SELECTED_PHONE_IDS_KEY, selectedPhoneIds);

            }
        }

        private void init(final String[] selecedUnknownNumbers, final long[] selectedPhoneIds) {
            if (selecedUnknownNumbers != null) {
                for (String number : selecedUnknownNumbers) {
                    setPhoneSelected(number, true);
                }
            }
            if (selectedPhoneIds != null) {
                for (long id : selectedPhoneIds) {
                    setPhoneSelected(id, true);
                }
            }
        }

        private void init(final List<String> selecedUnknownNumbers,
                final List<Long> selectedPhoneIds) {
            if (selecedUnknownNumbers != null) {
                setPhoneNumbersSelected(selecedUnknownNumbers, true);
            }
            if (selectedPhoneIds != null) {
                setPhoneIdsSelected(selectedPhoneIds, true);
            }
        }

        private void setPhoneNumbersSelected(final List<String> phoneNumbers, boolean selected) {
            if (selected) {
                mSelectedPhoneNumbers.addAll(phoneNumbers);
            } else {
                mSelectedPhoneNumbers.removeAll(phoneNumbers);
            }
        }

        private void setPhoneIdsSelected(final List<Long> phoneIds, boolean selected) {
            if (selected) {
                mSelectedPhoneIds.addAll(phoneIds);
            } else {
                mSelectedPhoneIds.removeAll(phoneIds);
            }
        }

        public void setPhoneSelected(final String phoneNumber, boolean selected) {
            if (!TextUtils.isEmpty(phoneNumber)) {
                if (selected) {
                    mSelectedPhoneNumbers.add(phoneNumber);
                } else {
                    mSelectedPhoneNumbers.remove(phoneNumber);
                }
            }
        }

        public void setPhoneSelected(long phoneId, boolean selected) {
            if (selected) {
                mSelectedPhoneIds.add(phoneId);
            } else {
                mSelectedPhoneIds.remove(phoneId);
            }
        }

        public boolean isSelected(long phoneId) {
            return mSelectedPhoneIds.contains(phoneId);
        }

        public boolean isSelected(final String phoneNumber) {
            return mSelectedPhoneNumbers.contains(phoneNumber);
        }

        public void setAllPhonesSelected(boolean selected) {
            if (selected) {
                Cursor cursor = mAdapter.getCursor();
                if (cursor != null) {
                    int backupPos = cursor.getPosition();
                    cursor.moveToPosition(-1);
                    while (cursor.moveToNext()) {
                        setPhoneSelected(cursor.getLong(PHONE_ID_COLUMN_INDEX), true);
                    }
                    cursor.moveToPosition(backupPos);
                }
                for (String number : mPhoneNumberAdapter.getFilteredPhoneNumbers()) {
                    setPhoneSelected(number, true);
                }
            } else {
                mSelectedPhoneIds.clear();
                mSelectedPhoneNumbers.clear();
            }
        }

        public boolean isAllSelected() {
            return selectedCount() == mPhoneNumberAdapter.getFilteredPhoneNumbers().size()
                    + mAdapter.getCount();
        }

        public int selectedCount() {
            return mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size();
        }

        public Iterator<Long> getSelectedPhonIds() {
            return mSelectedPhoneIds.iterator();
        }

        private int fillSelectedNumbers(Uri[] uris, int from) {
            int count = mSelectedPhoneNumbers.size();
            if (count == 0)
                return from;
            // Below loop keeps phone numbers by initial order.
            List<String> phoneNumbers = mPhoneNumberAdapter.getPhoneNumbers();
            for (String phoneNumber : phoneNumbers) {
                if (isSelected(phoneNumber)) {
                    Uri.Builder ub = new Uri.Builder();
                    ub.scheme(TEL_SCHEME);
                    ub.encodedOpaquePart(phoneNumber);
                    uris[from++] = ub.build();
                }
            }
            return from;
        }

        private int fillSelectedPhoneIds(Uri[] uris, int from) {
            int count = mSelectedPhoneIds.size();
            if (count == 0)
                return from;
            Iterator<Long> it = mSelectedPhoneIds.iterator();
            while (it.hasNext()) {
                uris[from++] = ContentUris.withAppendedId(Phone.CONTENT_URI, it.next());
            }
            return from;
        }

        private Uri[] getSelected() {
            Uri[] uris = new Uri[mSelectedPhoneNumbers.size() + mSelectedPhoneIds.size()];
            int from  = fillSelectedNumbers(uris, 0);
            fillSelectedPhoneIds(uris, from);
            return uris;
        }

        public Intent createSelectionIntent() {
            Intent intent = new Intent();
            intent.putExtra(Intents.EXTRA_PHONE_URIS, getSelected());

            return intent;
        }

        public void fillSelectionForSearchMode(Bundle bundle) {
            bundle.putParcelableArray(EXTRA_SELECTION, getSelected());
        }
    }
}
