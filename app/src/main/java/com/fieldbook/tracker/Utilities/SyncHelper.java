package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
    private String scheme;

    private final String PARAMETER_NAME_USER = "user";
    private final String PARAMETER_NAME_PASSWORD = "pass";
    private final String PARAMETER_NAME_OPERATION = "op";
    private final String PARAMETER_NAME_DATA = "data";
    private final String OPERATION_PARAMETER_VALUE_AUTHENTICATION = "auth";
    private final String OPERATION_PARAMETER_VALUE_UPLOAD = "up";
    private final String OPERATION_PARAMETER_VALUE_DOWNLOAD = "down";
    private final String JSON_DATA_KEY = "down";

    private final int CONNECTION_TIMEOUT = 5000;
    private final int DATARETRIEVAL_TIMEOUT = 5000;

    public SyncHelper(Context context, SharedPreferences sharedPreferences, DataHelper database) {
        this.context = context;
        this.dataBase = database;
        this.restUrl = getURL(sharedPreferences);
        this.restUser = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        this.restPassword = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");
        this.onlyUnique = sharedPreferences.getBoolean(context.getString(R.string.synconlyuniquepreference), true);
        this.onlyActive = sharedPreferences.getBoolean(context.getString(R.string.synconlyactivepreference), true);
        this.importUniqueName = sharedPreferences.getString(context.getString(R.string.uniquenamepreference), "");
        CookieManager cookieManager = new CookieManager();
        CookieHandler.setDefault(cookieManager);
    }

    public void performSyncUpload() {
        if (authenticate()) {
            Cursor data = getLocalData();
            String[] columnNames = data.getColumnNames();
            HashMap<String, String> uploadParameters = new HashMap<>();
            uploadParameters.put(PARAMETER_NAME_OPERATION, OPERATION_PARAMETER_VALUE_UPLOAD);
            while (data.moveToNext()) {
                JSONObject json = new JSONObject();
                for (String column : columnNames) {
                    try {
                        json.put(column, data.getString(data.getColumnIndex(column)));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
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
            HashMap<String, String> downloadParameters = new HashMap<>();
            downloadParameters.put(PARAMETER_NAME_OPERATION, OPERATION_PARAMETER_VALUE_DOWNLOAD);
            URL url = makeURL(downloadParameters);
            SyncDownloadTask downloadTask = new SyncDownloadTask();
            downloadTask.doInBackground(url);
        } else {
            showToast(context.getString(R.string.restautenticationfailed));
        }
    }

    private boolean authenticate() {
        try {
            HashMap<String, String> authenticationParameters = new HashMap<>();
            authenticationParameters.put(PARAMETER_NAME_OPERATION, OPERATION_PARAMETER_VALUE_AUTHENTICATION);
            authenticationParameters.put(PARAMETER_NAME_USER, restUser);
            authenticationParameters.put(PARAMETER_NAME_PASSWORD, restPassword);
            URL url = makeURL(authenticationParameters);
            HttpURLConnection connection = getConnection(url, "GET");
            connection.connect();
            connection.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private URL makeURL(HashMap<String, String> parameters) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(scheme).authority(restUrl);
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

    private HttpURLConnection getConnection(URL url, String requestMethod) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(DATARETRIEVAL_TIMEOUT);
            connection.setRequestMethod(requestMethod);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    private Cursor getLocalData() {

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

    private String getURL(SharedPreferences sharedPreferences) {
        String url = sharedPreferences.getString(context.getString(R.string.syncurlpreference), "");
        this.scheme = "https";
        if (url.startsWith("https")) {
            url = url.split("//")[1];
        } else if (url.startsWith("http")) {
            url = url.split("//")[1];
            this.scheme = "http";
        }
        return url;
    }

    private String[] getResponseColumnNames(JSONArray data) throws JSONException {
        JSONObject register = data.getJSONObject(0);
        Iterator keys = register.keys();
        String[] columnNames = new String[getIteratorLength(keys)];
        int i = 0;
        while (keys.hasNext()) {
            columnNames[i] = (String) keys.next();
        }
        return columnNames;
    }

    private int getIteratorLength(Iterator iterator) {
        int length = 0;
        while (iterator.hasNext()) {
            length += 1;
        }
        return length;
    }

    private String[] getRegisterValues(JSONObject register, String[] columnNames) throws JSONException {
        String[] registerValues = new String[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            registerValues[i] = (String) register.get(columnNames[i]);
        }
        return registerValues;
    }

    private void showToast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private class SyncUploadTask extends AsyncTask<URL, Integer, Long> {

        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = getConnection(params[0], "POST");
            try {
                connection.connect();
                Log.d(
                        Integer.toString(connection.getResponseCode()),
                        connection.getResponseMessage()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            connection.disconnect();
            return null;
        }
    }

    private class SyncDownloadTask extends AsyncTask<URL, Integer, Long> {
        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = getConnection(params[0], "GET");
            try {
                connection.connect();
                JSONObject response = new JSONObject(connection.getResponseMessage());
                JSONArray data = response.getJSONArray(JSON_DATA_KEY);
                String[] columnNames = getResponseColumnNames(data);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject register = data.getJSONObject(i);
                    String[] registerValues = getRegisterValues(register, columnNames);
                    String rid = registerValues[1];
                    String trait = registerValues[3];
                    Cursor localRegister = dataBase.getUserTraitsRegister(rid, trait);
                    //insert register if it's not present in local database
                    if (localRegister.getCount() == 0) {
                        dataBase.insertUserTraitsFromRemoteOrigin(
                                registerValues[1],
                                registerValues[2],
                                registerValues[3],
                                registerValues[4],
                                registerValues[5],
                                registerValues[6],
                                registerValues[7],
                                registerValues[8],
                                registerValues[9],
                                registerValues[10]
                        );
                    //update register if it's present with a previous date
                    } else if (localRegister.getString(5).compareTo(registerValues[5]) < 0) {
                        dataBase.updateUserTraitsFromRemoteOrigin(
                                registerValues[1],
                                registerValues[2],
                                registerValues[3],
                                registerValues[4],
                                registerValues[5],
                                registerValues[6],
                                registerValues[7],
                                registerValues[8],
                                registerValues[9],
                                registerValues[10]
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            connection.disconnect();
            return null;
        }
    }

}