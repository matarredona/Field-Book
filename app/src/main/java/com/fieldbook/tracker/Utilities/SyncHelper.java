package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.Toast;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by matarredona on 22/03/17.
 */

public class SyncHelper {

    private Context context;
    private DataHelper dataBase;
    private String restUrl;
    private String restUser;
    private String restPassword;
    private boolean onlyUnique;
    private boolean onlyActive;
    private String importUniqueName;

    private final String SCHEME = "https";
    private final String PARAMETER_NAME_USER = "user";
    private final String PARAMETER_NAME_PASSWORD = "pass";
    private final String PARAMETER_NAME_OPERATION = "op";
    private final String PARAMETER_NAME_DATA = "data";
    private final String OPERATION_PARAMETER_VALUE_AUTHENTICATION = "auth";
    private final String OPERATION_PARAMETER_VALUE_UPLOAD = "up";
    private final String OPERATION_PARAMETER_VALUE_DOWNLOAD = "down";

    private final int CONNECTION_TIMEOUT = 10000;
    private final int DATARETRIEVAL_TIMEOUT = 10000;

    public SyncHelper(Context context, SharedPreferences sharedPreferences, DataHelper database) {
        this.context = context;
        this.dataBase = database;
        String url = sharedPreferences.getString(context.getString(R.string.syncurlpreference), "");
        if (restUrl.startsWith("http")) {
            restUrl = url.split("//")[1];
        }
        this.restUser = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        this.restPassword = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");
        this.onlyUnique = sharedPreferences.getBoolean(context.getString(R.string.synconlyuniquepreference), true);
        this.onlyActive = sharedPreferences.getBoolean(context.getString(R.string.synconlyactivepreference), true);
        this.importUniqueName = sharedPreferences.getString(context.getString(R.string.uniquenamepreference), "");
    }

    public void performSyncUpload() {
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
        if (authenticate()) {
            Cursor data = getLocalData();
            String[] columnNames = data.getColumnNames();
            while (data.moveToNext()) {
                JSONObject json = new JSONObject();
                for (String column : columnNames) {
                    try {
                        json.put(column, data.getString(data.getColumnIndex(column)));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                HashMap<String, String> uploadParameters = new HashMap<>();
                uploadParameters.put(PARAMETER_NAME_OPERATION, OPERATION_PARAMETER_VALUE_UPLOAD);
                uploadParameters.put(PARAMETER_NAME_DATA, json.toString());
                URL url = makeURL(uploadParameters);
                SyncUploadTask uploadTask = new SyncUploadTask();
                uploadTask.doInBackground(url);
            }
            data.close();
        } else {
            showToast(context.getString(R.string.restautenticationfailed));
        }
    }

    public void performSyncDownload() {
        if (authenticate()) {

        } else {
            showToast(context.getString(R.string.restautenticationfailed));
        }
    }

    public boolean authenticate() {
        try {
            HashMap<String, String> authenticationParameters = new HashMap<>();
            authenticationParameters.put(PARAMETER_NAME_OPERATION, OPERATION_PARAMETER_VALUE_AUTHENTICATION);
            authenticationParameters.put(PARAMETER_NAME_USER, restUser);
            authenticationParameters.put(PARAMETER_NAME_PASSWORD, restPassword);
            URL url = makeURL(authenticationParameters);
            HttpURLConnection connection = getConnection(url);
            connection.setRequestMethod("GET");
            connection.connect();
            connection.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public URL makeURL(HashMap<String, String> parameters) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(SCHEME).authority(restUrl);
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        URL url = null;
        try {
            url = new URL(builder.build().toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return url;
    }

    public HttpURLConnection getConnection(URL url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(DATARETRIEVAL_TIMEOUT);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public Cursor getLocalData() {

        ArrayList newRange = new ArrayList<>();
        if (onlyUnique) {
            newRange.add(importUniqueName);
        } else {
            String[] columns = dataBase.getRangeColumns();
            Collections.addAll(newRange, columns);
        }

        ArrayList exportTrait = new ArrayList<>();
        if (onlyActive) {
            String[] traits = dataBase.getVisibleTrait();
            Collections.addAll(exportTrait, traits);
        } else {
            String[] traits = dataBase.getAllTraits();
            Collections.addAll(exportTrait, traits);
        }

        String[] newRanges = (String[]) newRange.toArray(new String[newRange.size()]);
        String[] exportTraits = (String[]) exportTrait.toArray(new String[exportTrait.size()]);

        return dataBase.getExportDBData(newRanges, exportTraits);

    }

    public void showToast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    protected class SyncUploadTask extends AsyncTask<URL, Integer, Long> {


        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = getConnection(params[0]);
            try {
                connection.setRequestMethod("GET");
                connection.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            connection.disconnect();
            return null;
        }
    }

    protected class SyncDownloadTask extends AsyncTask<URL, Integer, Long> {
        @Override
        protected Long doInBackground(URL... params) {
            return null;
        }
    }

}
