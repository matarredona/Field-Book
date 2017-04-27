package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by matarredona on 22/03/17.
 */

public class SyncHelper {

    private Context context;
    private String restUser;
    private String restPassword;

    private ConnectionHelper connectionHelper;
    private ContentHelper contentHelper;

    private static final String HEADER_NAME_AUTH = "Authorization";
    private static final String PARAMETER_NAME_FORMAT = "format";
    private static final String PARAMETER_VALUE_JSON = "json";

    private static final String PARAMETER_NAME_DATA = "data";
    private static final String JSON_DATA_KEY = "down";
    private static final String COOKIE_NAME_TOKEN = "csrftoken";
    private static final String COOKIE_NAME_SESSION = "sessionid";

    private static final List<String> PATH_AUTH_TOKEN = Arrays.asList(
            "api-token-auth"
    );
    private static final List<String> PATH_DATA_UPLOAD = Arrays.asList(
            "api",
            "plants"
    );
    private static final List<String> PATH_DATA_DOWNLOAD = Arrays.asList(
            "api",
            "plants"
    );

    private static final int RID = 1;
    private static final int PARENT = 2;
    private static final int TRAIT = 3;
    private static final int USERVALUE = 4;
    private static final int TIMETAKEN = 5;
    private static final int PERSON = 6;
    private static final int LOCATION = 7;
    private static final int REP = 8;
    private static final int NOTES = 9;
    private static final int EXP_ID = 10;

    public SyncHelper(Context context, SharedPreferences sharedPreferences, DataHelper database) {
        this.context = context;
        this.restUser = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        this.restPassword = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");

        String restUrl = sharedPreferences.getString(context.getString(R.string.syncurlpreference), "");
        this.connectionHelper = new ConnectionHelper(restUrl);

        String importUniqueName = sharedPreferences.getString(context.getString(R.string.uniquenamepreference), "");
        boolean onlyUnique = sharedPreferences.getBoolean(context.getString(R.string.synconlyuniquepreference), true);
        boolean onlyActive = sharedPreferences.getBoolean(context.getString(R.string.synconlyactivepreference), true);
        this.contentHelper = new ContentHelper(database, importUniqueName, onlyUnique, onlyActive);
    }

    public void performSyncUpload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get() == 1) {
                Cursor data = contentHelper.getLocalData();
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
                    String register = json.toString();
                    SyncUploadTask uploadTask = new SyncUploadTask(register);
                    uploadTask.execute();
                }
                data.close();
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void performSyncDownload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get() == 1) {
                HashMap<String, String> downloadParameters = new HashMap<>();
                downloadParameters.put(PARAMETER_NAME_FORMAT, PARAMETER_VALUE_JSON);
                SyncDownloadTask downloadTask = new SyncDownloadTask(downloadParameters);
                downloadTask.execute();
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private class AuthenticationTask extends AsyncTask<URL, Integer, Long> {

        @Override
        protected Long doInBackground(URL... params) {
            HashMap<String, String> tokenParameters = new HashMap<>();
            tokenParameters.put("username", restUser);
            tokenParameters.put("password", restPassword);
            HttpURLConnection connection = connectionHelper.getConnection(PATH_AUTH_TOKEN, null, tokenParameters, null, "POST");
            try {
                connection.connect();
                JSONObject tokenJSON = new JSONObject(connectionHelper.getConnectionContent(connection));
                String tokenValue = "Token " + tokenJSON.getString("token");
                connectionHelper.setToken(tokenValue);
                connection.disconnect();
                if (connection.getResponseCode() != 200) {
                    return 0L;
                }
                return 1L;
            } catch (Exception e) {
                connection.disconnect();
                return 0L;
            }
        }
    }

    private class SyncUploadTask extends AsyncTask<URL, Integer, Long> {

        private String body;

        public SyncUploadTask(String body) {
            this.body = body;
        }

        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_UPLOAD, body, null, null, "POST");
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

        private HashMap downloadParameters;

        public SyncDownloadTask(HashMap downloadParameters) {
            this.downloadParameters = downloadParameters;
        }

        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_DOWNLOAD, null, downloadParameters, null, "GET");
            try {
                connection.connect();
                JSONArray data = new JSONArray(connectionHelper.getConnectionContent(connection));
                String[] columnNames = contentHelper.getResponseColumnNames(data);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject register = data.getJSONObject(i);
                    String[] registerValues = contentHelper.getRegisterValues(register, columnNames);
                    String rid = registerValues[RID];
                    String trait = registerValues[TRAIT];
                    Cursor localRegister = contentHelper.getDataBase().getUserTraitsRegister(rid, trait);
                    //insert register if it's not present in local database
                    if (!localRegister.moveToNext()) {
                        contentHelper.getDataBase().insertUserTraitsFromRemoteOrigin(
                                registerValues[RID],
                                registerValues[PARENT],
                                registerValues[TRAIT],
                                registerValues[USERVALUE],
                                registerValues[TIMETAKEN],
                                registerValues[PERSON],
                                registerValues[LOCATION],
                                registerValues[REP],
                                registerValues[NOTES],
                                registerValues[EXP_ID]
                        );
                    //update register if it's present with a previous date
                    } else if (localRegister.getString(TIMETAKEN).compareTo(registerValues[TIMETAKEN]) < 0) {
                        contentHelper.getDataBase().updateUserTraitsFromRemoteOrigin(
                                registerValues[RID],
                                registerValues[PARENT],
                                registerValues[TRAIT],
                                registerValues[USERVALUE],
                                registerValues[TIMETAKEN],
                                registerValues[PERSON],
                                registerValues[LOCATION],
                                registerValues[REP],
                                registerValues[NOTES],
                                registerValues[EXP_ID]
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