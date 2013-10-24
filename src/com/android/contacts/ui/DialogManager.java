package com.android.contacts.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Bundle;
import android.view.View;

/**
 * Manages creation and destruction of Dialogs that are to be shown by Views. Unlike how Dialogs
 * are regularly used, the Dialogs are not recycled but immediately destroyed after dismissal.
 * To be able to do that, two IDs are required which are used consecutively.
 * How to use:<ul>
 * <li>The owning Activity creates on instance of this class, passing itself and two Ids that are
 *    not used by other Dialogs of the Activity.</li>
 * <li>Views owning Dialogs must implement {@link DialogManager.DialogShowingView}</li>
 * <li>After creating the Views, configureManagingViews must be called to configure all views
 *    that implement {@link DialogManager.DialogShowingView}</li>
 * <li>In the implementation of {@link Activity#onCreateDialog}, calls for the
 *    ViewId are forwarded to {@link DialogManager#onCreateDialog(int, Bundle)}</li>
 * </ul>
 * To actually show a Dialog, the View uses {@link DialogManager#showDialogInView(View, Bundle)},
 * passing itself as a first parameter
 */
public class DialogManager {
    private final Activity mActivity;
    private final int mDialogId1;
    private final int mDialogId2;
    private boolean mUseDialogId2 = false;
    public final static String VIEW_ID_KEY = "view_id";

    /**
     * Creates a new instance of this class for the given Activity.
     * @param activity The activity this object is used for
     * @param dialogId1 The first Id that is reserved for use by child-views
     * @param dialogId2 The second Id that is reserved for use by child-views
     */
    public DialogManager(final Activity activity, final int dialogId1, final int dialogId2) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        if (dialogId1 == dialogId2) throw new IllegalArgumentException("Ids must be different");
        mActivity = activity;
        mDialogId1 = dialogId1;
        mDialogId2 = dialogId2;
    }

    /**
     * Called by a View to show a dialog. It has to pass itself and a Bundle with extra information.
     * If the view can show several dialogs, it should distinguish them using an item in the Bundle.
     * The View needs to have a valid and unique Id. This function modifies the bundle by adding a
     * new item named {@link DialogManager#VIEW_ID_KEY}
     */
    public void showDialogInView(final View view, final Bundle bundle) {
        final int viewId = view.getId();
        if (bundle.containsKey(VIEW_ID_KEY)) {
            throw new IllegalArgumentException("Bundle already contains a " + VIEW_ID_KEY);
        }
        if (viewId == View.NO_ID) {
            throw new IllegalArgumentException("View does not have a proper ViewId");
        }
        bundle.putInt(VIEW_ID_KEY, viewId);
        int dialogId = mUseDialogId2 ? mDialogId2 : mDialogId1;
        mActivity.showDialog(dialogId, bundle);
    }

    /**
     * Callback function called by the Activity to handle View-managed Dialogs.
     * This function returns null if the id is not one of the two reserved Ids.
     */
    public Dialog onCreateDialog(final int id, final Bundle bundle) {
        if (id == mDialogId1) {
            mUseDialogId2 = true;
        } else if (id == mDialogId2) {
            mUseDialogId2 = false;
        } else {
            return null;
        }
        if (!bundle.containsKey(VIEW_ID_KEY)) {
            throw new IllegalArgumentException("Bundle does not contain a ViewId");
        }
        final int viewId = bundle.getInt(VIEW_ID_KEY);
        final View view = mActivity.findViewById(viewId);
        if (view == null || !(view instanceof DialogShowingView)) {
            return null;
        }
        final Dialog dialog = ((DialogShowingView)view).createDialog(bundle);

        // As we will never re-use this dialog, we can completely kill it here
        dialog.setOnDismissListener(new OnDismissListener() {
            public void onDismiss(DialogInterface dialogInterface) {
                mActivity.removeDialog(id);
            }
        });
        return dialog;
    }

    /**
     * Interface to implemented by Views that show Dialogs
     */
    public interface DialogShowingView {
        /** Callback function to create a Dialog. Notice that the DialogManager overwrites the
         * OnDismissListener on the returned Dialog, so the View should not use this Listener itself
         */
        Dialog createDialog(Bundle bundle);
    }

    /**
     * Interface to implemented by Activities that host View-showing dialogs
     */
    public interface DialogShowingViewActivity {
        DialogManager getDialogManager();
    }
}
