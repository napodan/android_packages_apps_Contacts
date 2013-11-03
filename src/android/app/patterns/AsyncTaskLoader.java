/*
 * Copyright (C) 2010 The Android Open Source Project.
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

package android.app.patterns;

import android.content.Context;
import android.os.AsyncTask;

/**
 * Abstract Loader that provides an {@link AsyncTask} to do the work.
 *
 * @param <D> the data type to be loaded.
 */
public abstract class AsyncTaskLoader<D> extends Loader<D> {
    final class LoadListTask extends AsyncTask<Void, Void, D> {
        /* Runs on a worker thread */
        @Override
        protected D doInBackground(Void... params) {
            return AsyncTaskLoader.this.loadInBackground();
        }

        /* Runs on the UI thread */
        @Override
        protected void onPostExecute(D data) {
            AsyncTaskLoader.this.dispatchOnLoadComplete(data);
        }
    }

    private LoadListTask mTask;

    public AsyncTaskLoader(Context context) {
        super(context);
    }

    /**
     * Force an asynchronous load. Unlike {@link #startLoading()} this will ignore a previously
     * loaded data set and load a new one.
     */
    @Override
    public void forceLoad() {
        mTask = new LoadListTask();
        mTask.execute((Void[]) null);
    }

    /**
     * Attempt to cancel the current load task. See {@link AsyncTask#cancel(boolean)}
     * for more info.
     *
     * @return <tt>false</tt> if the task could not be cancelled,
     *         typically because it has already completed normally, or
     *         because {@link startLoading()} hasn't been called, and
     *         <tt>true</tt> otherwise
     */
    public boolean cancelLoad() {
        if (mTask != null) {
            return mTask.cancel(false);
        }
        return false;
    }

    private void dispatchOnLoadComplete(D data) {
        mTask = null;
        onLoadComplete(data);
    }

    /**
     * Called on a worker thread to perform the actual load. Implementions should not deliver the
     * results directly, but should return them from this this method and deliver them from
     * {@link #onPostExecute()}
     *
     * @return the result of the load
     */
    protected abstract D loadInBackground();

    /**
     * Called on the UI thread with the result of the load.
     *
     * @param data the result of the load
     */
    protected abstract void onLoadComplete(D data);
}
