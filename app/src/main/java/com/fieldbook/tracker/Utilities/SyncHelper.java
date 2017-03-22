package com.fieldbook.tracker.Utilities;

import android.os.AsyncTask;
import android.util.Log;
import com.fieldbook.tracker.DataHelper;
import java.net.URL;

/**
 * Created by matarredona on 22/03/17.
 */

public class SyncHelper {

    public void performSyncUpload(DataHelper dataBase) {
        Log.d("test", "upload");
    }

    public void performSyncDownload(DataHelper dataBase) {
        Log.d("test", "download");
    }

    protected class syncUploadTask extends AsyncTask<URL, Integer, Long> {
        @Override
        protected Long doInBackground(URL... params) {
            return null;
        }
    }

    protected class syncDownloadTask extends AsyncTask<URL, Integer, Long> {
        @Override
        protected Long doInBackground(URL... params) {
            return null;
        }
    }

}
