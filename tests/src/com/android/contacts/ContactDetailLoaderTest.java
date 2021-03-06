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

package com.android.contacts;

import com.android.contacts.tests.mocks.ContactsMockContext;
import com.android.contacts.tests.mocks.MockContentProvider;
import com.android.contacts.views.detail.ContactDetailLoader;

import android.content.ContentUris;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts.Data;
import android.provider.ContactsContract.RawContacts.Entity;
import android.test.AndroidTestCase;
import android.test.AssertionFailedError;
import android.test.suitebuilder.annotation.Suppress;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Runs ContactLoader tests for the the contact-detail view.
 */
public class ContactDetailLoaderTest extends AndroidTestCase {
    private ContactsMockContext mMockContext;
    private MockContentProvider mContactsProvider;

    static {
        // Need to force class loading of AsyncTask on the main thread...
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... args) {return null;}
            @Override
            protected void onPostExecute(Void result) {}
        };
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMockContext = new ContactsMockContext(getContext());
        mContactsProvider = mMockContext.getContactsProvider();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Utility function to ensure that an Exception is thrown during the code
     * TODO: This should go to MoreAsserts at one point
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E assertThrows(
            Class<E> expectedException, Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable exception) {
            Class<? extends Throwable> receivedException = exception.getClass();
            if (expectedException == receivedException) return (E) exception;
            throw new AssertionFailedError("Expected Exception " + expectedException +
                    " but " + receivedException + " was thrown. Details: " + exception);
        }
        throw new AssertionFailedError(
                "Expected Exception " + expectedException + " which was not thrown");
    }

    /**
     * Runs a Loader synchronously and returns the result of the load. The loader will
     * be started, stopped, and destroyed by this method so it cannot be reused.
     *
     * @param loader The loader to run synchronously
     * @return The result from the loader
     */
    private <T> T getLoaderResultSynchronously(final Loader<T> loader) {
        // The test thread blocks on this queue until the loader puts it's result in
        final ArrayBlockingQueue<T> queue = new ArrayBlockingQueue<T>(1);

        // This callback runs on the "main" thread and unblocks the test thread
        // when it puts the result into the blocking queue
        final OnLoadCompleteListener<T> listener = new OnLoadCompleteListener<T>() {
            public void onLoadComplete(Loader<T> completedLoader, T data) {
                // Shut the loader down
                completedLoader.unregisterListener(this);
                completedLoader.stopLoading();
                completedLoader.destroy();

                // Store the result, unblocking the test thread
                queue.add(data);
            }
        };

        // This handler runs on the "main" thread of the process since AsyncTask
        // is documented as needing to run on the main thread and many Loaders use
        // AsyncTask
        final Handler mainThreadHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                loader.registerListener(0, listener);
                loader.startLoading();
            }
        };

        // Ask the main thread to start the loading process
        mainThreadHandler.sendEmptyMessage(0);

        // Block on the queue waiting for the result of the load to be inserted
        T result;
        while (true) {
            try {
                result = queue.take();
                break;
            } catch (InterruptedException e) {
                throw new RuntimeException("waiting thread interrupted", e);
            }
        }

        return result;
    }

    private ContactDetailLoader.Result assertLoadContact(Uri uri) {
        final ContactDetailLoader loader = new ContactDetailLoader(mMockContext, uri);
        return getLoaderResultSynchronously(loader);
    }

    public void testNullUri() {
        ContactDetailLoader.Result result = assertLoadContact(null);
        assertEquals(ContactDetailLoader.Result.ERROR, result);
    }

    public void testEmptyUri() {
        ContactDetailLoader.Result result = assertLoadContact(Uri.EMPTY);
        assertEquals(ContactDetailLoader.Result.ERROR, result);
    }

    public void testInvalidUri() {
        ContactDetailLoader.Result result = assertLoadContact(Uri.parse("content://wtf"));
        assertEquals(ContactDetailLoader.Result.ERROR, result);
    }

    public void testLoadContactWithContactIdUri() {
        // Use content Uris that only contain the ID
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchLookupAndId(baseUri, contactId, encodedLookup);
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(baseUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithOldStyleUri() {
        // Use content Uris that only contain the ID but use the format used in Donut
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final Uri legacyUri = ContentUris.withAppendedId(
                Uri.parse("content://contacts"), rawContactId);
        final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchContactIdAndLookupFromRawContactUri(rawContactUri, contactId, encodedLookup);
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(legacyUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupUri() {
        // Use lookup-style Uris that do not contain the Contact-ID

        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupNoIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup);
        final Uri lookupUri = ContentUris.withAppendedId(lookupNoIdUri, contactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchLookupAndId(lookupNoIdUri, contactId, encodedLookup);
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(lookupNoIdUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupAndIdUri() {
        // Use lookup-style Uris that also contain the Contact-ID
        final long contactId = 1;
        final long rawContactId = 11;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(lookupUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());
        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupWithIncorrectIdUri() {
        // Use lookup-style Uris that contain incorrect Contact-ID
        // (we want to ensure that still the correct contact is chosen)
        // In this test, the incorrect Id references another Contact

        final long contactId = 1;
        final long wrongContactId = 2;
        final long rawContactId = 11;
        final long wrongRawContactId = 12;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final String wrongEncodedLookup = Uri.encode("ab%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri wrongBaseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, wrongContactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri lookupWithWrongIdUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                wrongContactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchHeaderData(wrongBaseUri, wrongRawContactId, wrongEncodedLookup);
        queries.fetchLookupAndId(lookupWithWrongIdUri, contactId, encodedLookup);
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(lookupWithWrongIdUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());

        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupWithIncorrectIdUri2() {
        // Use lookup-style Uris that contain incorrect Contact-ID
        // (we want to ensure that still the correct contact is chosen)
        // In this test, the incorrect Id references no contact

        final long contactId = 1;
        final long wrongContactId = 2;
        final long rawContactId = 11;
        final long wrongRawContactId = 12;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final String wrongEncodedLookup = Uri.encode("ab%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri wrongBaseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, wrongContactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri lookupWithWrongIdUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                wrongContactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchHeaderDataNoResult(wrongBaseUri);
        queries.fetchLookupAndId(lookupWithWrongIdUri, contactId, encodedLookup);
        queries.fetchHeaderData(baseUri, rawContactId, encodedLookup);
        queries.fetchSocial(dataUri, contactId);
        queries.fetchRawContacts(contactId, dataId, rawContactId);

        ContactDetailLoader.Result contact = assertLoadContact(lookupWithWrongIdUri);

        assertEquals(contactId, contact.getId());
        assertEquals(rawContactId, contact.getNameRawContactId());
        assertEquals(DisplayNameSources.STRUCTURED_NAME, contact.getDisplayNameSource());
        assertEquals(encodedLookup, contact.getLookupKey());
        assertEquals(lookupUri, contact.getLookupUri());
        assertEquals(1, contact.getEntities().size());
        assertEquals(1, contact.getStatuses().size());

        mContactsProvider.verify();
    }

    public void testLoadContactWithContactLookupWithIncorrectIdUri3() {
        // Use lookup-style Uris that contain incorrect Contact-ID
        // (we want to ensure that still the correct contact is chosen)
        // In this test, the incorrect Id references no contact and the lookup
        // key can also not be resolved

        final long contactId = 1;
        final long wrongContactId = 2;
        final long rawContactId = 11;
        final long wrongRawContactId = 12;
        final long dataId = 21;

        final String encodedLookup = Uri.encode("aa%12%@!");
        final String wrongEncodedLookup = Uri.encode("ab%12%@!");
        final Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        final Uri wrongBaseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, wrongContactId);
        final Uri lookupUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                contactId);
        final Uri lookupWithWrongIdUri = ContentUris.withAppendedId(
                Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, encodedLookup),
                wrongContactId);
        final Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        ContactQueries queries = new ContactQueries();
        queries.fetchHeaderDataNoResult(wrongBaseUri);
        queries.fetchLookupAndIdNoResult(lookupWithWrongIdUri);

        ContactDetailLoader.Result contact = assertLoadContact(lookupWithWrongIdUri);

        assertEquals(ContactDetailLoader.Result.NOT_FOUND, contact);

        mContactsProvider.verify();
    }

    private class ContactQueries {
        private void fetchRawContacts(final long contactId, final long dataId,
                final long rawContactId) {
            mContactsProvider.expectQuery(RawContactsEntity.CONTENT_URI)
                .withDefaultProjection(new String[] {
                        RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
                        RawContacts.DIRTY, RawContacts.VERSION, RawContacts.SOURCE_ID,
                        RawContacts.SYNC1, RawContacts.SYNC2, RawContacts.SYNC3, RawContacts.SYNC4,
                        RawContacts.DELETED, RawContacts.CONTACT_ID, RawContacts.STARRED,
                        RawContacts.IS_RESTRICTED, RawContacts.NAME_VERIFIED,

                        Entity.DATA_ID, Data.RES_PACKAGE, Data.MIMETYPE, Data.IS_PRIMARY,
                        Data.IS_SUPER_PRIMARY, Data.DATA_VERSION,
                        CommonDataKinds.GroupMembership.GROUP_SOURCE_ID,
                        Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                        Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
                        Data.DATA11, Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
                        Data.SYNC1, Data.SYNC2, Data.SYNC3, Data.SYNC4
                })
                .withSelection(
                        RawContacts.CONTACT_ID + "=?",
                        new String[] { String.valueOf(contactId) } )
                .returnRow(
                        rawContactId, "mockAccountName", "mockAccountType",
                        0, 1, "aa%12%@!",
                        "", "", "", "",
                        0, contactId, 0,
                        0, 1,

                        dataId, "", StructuredName.CONTENT_ITEM_TYPE, 1,
                        1, 1,
                        "mockGroupId",
                        "dat1", "dat2", "dat3", "dat4", "dat5",
                        "dat6", "dat7", "dat8", "dat9", "dat10",
                        "dat11", "dat12", "dat13", "dat14", null,
                        "syn1", "syn2", "syn3", "syn4");
        }

        private void fetchSocial(final Uri dataUri, final long expectedContactId) {
            mContactsProvider.expectQuery(dataUri)
                    .withProjection(
                            Contacts._ID, StatusUpdates.STATUS, StatusUpdates.STATUS_RES_PACKAGE,
                            StatusUpdates.STATUS_ICON, StatusUpdates.STATUS_LABEL,
                            StatusUpdates.STATUS_TIMESTAMP, StatusUpdates.PRESENCE)
                    .withSelection(
                            StatusUpdates.PRESENCE +" IS NOT NULL OR " +
                            StatusUpdates.STATUS + " IS NOT NULL",
                            (String[]) null)
                    .returnRow(
                            expectedContactId, "This is a mock Status update", 0,
                            1, 2,
                            0, StatusUpdates.AVAILABLE);
        }

        private void fetchHeaderData(final Uri uri, final long expectedRawContactId,
                final String expectedEncodedLookup) {
            mContactsProvider.expectQuery(uri)
                    .withProjection(
                            Contacts.NAME_RAW_CONTACT_ID,
                            Contacts.DISPLAY_NAME_SOURCE,
                            Contacts.LOOKUP_KEY,
                            Contacts.DISPLAY_NAME,
                            Contacts.PHONETIC_NAME,
                            Contacts.PHOTO_ID,
                            Contacts.STARRED,
                            Contacts.CONTACT_PRESENCE,
                            Contacts.CONTACT_STATUS,
                            Contacts.CONTACT_STATUS_TIMESTAMP,
                            Contacts.CONTACT_STATUS_RES_PACKAGE,
                            Contacts.CONTACT_STATUS_LABEL)
                    .returnRow(
                            expectedRawContactId,
                            DisplayNameSources.STRUCTURED_NAME,
                            expectedEncodedLookup,
                            "contactDisplayName",
                            "contactPhoneticName",
                            null,
                            0,
                            null,
                            null,
                            null,
                            null,
                            null);
        }

        private void fetchHeaderDataNoResult(final Uri uri) {
            mContactsProvider.expectQuery(uri)
                    .withProjection(
                            Contacts.NAME_RAW_CONTACT_ID,
                            Contacts.DISPLAY_NAME_SOURCE,
                            Contacts.LOOKUP_KEY,
                            Contacts.DISPLAY_NAME,
                            Contacts.PHONETIC_NAME,
                            Contacts.PHOTO_ID,
                            Contacts.STARRED,
                            Contacts.CONTACT_PRESENCE,
                            Contacts.CONTACT_STATUS,
                            Contacts.CONTACT_STATUS_TIMESTAMP,
                            Contacts.CONTACT_STATUS_RES_PACKAGE,
                            Contacts.CONTACT_STATUS_LABEL);
        }

        private void fetchLookupAndId(final Uri sourceUri, final long expectedContactId,
                final String expectedEncodedLookup) {
            mContactsProvider.expectQuery(sourceUri)
                    .withProjection(Contacts.LOOKUP_KEY, Contacts._ID)
                    .returnRow(expectedEncodedLookup, expectedContactId);
        }

        private void fetchLookupAndIdNoResult(final Uri sourceUri) {
            mContactsProvider.expectQuery(sourceUri)
                    .withProjection(Contacts.LOOKUP_KEY, Contacts._ID);
        }

        private void fetchContactIdAndLookupFromRawContactUri(final Uri rawContactUri,
                final long expectedContactId, final String expectedEncodedLookup) {
            // TODO: use a lighter query by joining rawcontacts with contacts in provider
            // (See ContactContracts.java)
            final Uri dataUri = Uri.withAppendedPath(rawContactUri, Data.CONTENT_DIRECTORY);
            mContactsProvider.expectQuery(dataUri)
                    .withProjection(RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY)
                    .returnRow(expectedContactId, expectedEncodedLookup);
        }
    }
}
