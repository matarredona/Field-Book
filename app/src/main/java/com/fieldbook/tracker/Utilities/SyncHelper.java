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
import java.util.HashMap;
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

    private static final int MAX_UPLOAD_ATTEMPTS = 2;
    private static final int MAX_SIMULTANEOUS_SERVER_CALLS = 24;

    private static final int REGISTER_FAILED = 0;
    private static final int REGISTER_INTEGRATED = 1;
    private static final int REGISTER_PRESENT = 2;
    private static final int REGISTER_UPDATED = 3;

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
            if (authenticationTask.execute().get()) {
                showToast(context.getString(R.string.restuploading));
                //prepare registers to upload
                Cursor data = contentHelper.getLocalData();
                ArrayList<String> rawRegisters = new ArrayList<String>();
                String[] columnNames = data.getColumnNames();
                Log.d("", "preparing tasks");
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
                    rawRegisters.add(register);
                }
                data.close();
                //server calls are made in batches
                System.setProperty("http.keepAlive", "true");
                List<List<String>> preparedRegisters = contentHelper.chopList(rawRegisters, MAX_SIMULTANEOUS_SERVER_CALLS);
                int registersFailed = 0;
                int registersIntegrated = 0;
                int registersPresent = 0;
                Log.d("", "launching tasks");
                for (List<String> registers : preparedRegisters) {
                    int attempts = 0;
                    while (!registers.isEmpty() && attempts < MAX_UPLOAD_ATTEMPTS) {
                        attempts += 1;
                        ArrayList<SyncUploadTask> tasks = new ArrayList<SyncUploadTask>();
                        //launch tasks
                        for (String register : registers) {
                            Log.d("", "launching task");
                            SyncUploadTask task = new SyncUploadTask(register);
                            tasks.add(task);
                            task.execute();
                        }
                        //get tasks responses
                        for (SyncUploadTask task : tasks) {
                            Log.d("", "getting task response");
                            switch (task.get()) {
                                case 201:
                                    registersIntegrated += 1;
                                    tasks.remove(task);
                                    break;
                                case 202:
                                    registersPresent += 1;
                                    tasks.remove(task);
                                    break;
                            }
                            Log.d("", "done, bad endpoint");
                        }
                        Log.d("", "attempt finished");
                    }
                    registersFailed += registers.size();
                    Log.d("", "list fragment finished");
                }
                Log.d("", "upload finished");
                showToast(context.getString(R.string.restuploadregistersfailed) + registersFailed);
                showToast(context.getString(R.string.restregistersintegrated) + registersIntegrated);
                showToast(context.getString(R.string.restregisterspresent) + registersPresent);

/*
                SyncSingleUploadTask uploadTask = new SyncSingleUploadTask();
                Integer[] uploadResume = uploadTask.execute().get();
                showToast(context.getString(R.string.restuploadregistersfailed) + uploadResume[REGISTER_FAILED]);
                showToast(context.getString(R.string.restregistersintegrated) + uploadResume[REGISTER_INTEGRATED]);
                showToast(context.getString(R.string.restregisterspresent) + uploadResume[REGISTER_PRESENT]);
*/
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
            if (authenticationTask.execute().get()) {
                showToast(context.getString(R.string.restdownloading));
                SyncDownloadTask downloadTask = new SyncDownloadTask();
                Integer[] downloadResume = downloadTask.execute().get();
                if (downloadResume[REGISTER_FAILED] > 0) {
                    showToast(context.getString(R.string.restdownloadfailed));
                }
                showToast(context.getString(R.string.restregistersintegrated) + downloadResume[REGISTER_INTEGRATED]);
                showToast(context.getString(R.string.restregistersupdated) + downloadResume[REGISTER_UPDATED]);
                showToast(context.getString(R.string.restregisterspresent) + downloadResume[REGISTER_PRESENT]);
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

    private class AuthenticationTask extends AsyncTask<URL, Integer, Boolean> {

        @Override
        protected Boolean doInBackground(URL... params) {
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
                    return false;
                }
                return true;
            } catch (Exception e) {
                connection.disconnect();
                return false;
            }
        }
    }

    private class SyncSingleUploadTask extends AsyncTask<URL, Integer, Integer[]> {

        @Override
        protected Integer[] doInBackground(URL... params) {
            Log.d("", "thread started");

            Integer[] resume = new Integer[3];
            Arrays.fill(resume, 0);
            //prepare upload registers
            Cursor data = contentHelper.getLocalData();
            ArrayList<String> registers = new ArrayList<String>();
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
                registers.add(register);
            }
            data.close();
            Log.d("", "registers prepared");
            //launch connections
            int attempts = 0;
            while (!registers.isEmpty() && attempts < MAX_UPLOAD_ATTEMPTS) {
                attempts += 1;
                for (String register : registers) {
                    try {
                        Log.d("", "launching connection");
                        HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_UPLOAD, register, null, null, "POST");
                        connection.connect();
                        switch (connection.getResponseCode()) {
                            case 201:
                                resume[REGISTER_INTEGRATED] += 1;
                                //connection.disconnect();
                                registers.remove(register);
                                break;
                            case 202:
                                resume[REGISTER_PRESENT] += 1;
                                //connection.disconnect();
                                registers.remove(register);
                                break;
                            default:
                                Log.d("", "closing connection");
                                //connection.disconnect();
                                break;
                        }
                    } catch (IOException e) {
                        Log.d("", "connection exception");
                        e.printStackTrace();
                    }
                }
            }
            Log.d("", "thread finishing");
            resume[REGISTER_FAILED] = registers.size();
            return resume;
        }
    }

    private class SyncUploadTask extends AsyncTask<URL, Integer, Integer> {

        private String body;

        public SyncUploadTask(String body) {
            this.body = body;
        }

        @Override
        protected Integer doInBackground(URL... params) {
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_UPLOAD, body, null, null, "POST");
            try {
                connection.connect();
                int responseCode = connection.getResponseCode();
                //connection.disconnect();
                return responseCode;
            } catch (Exception e) {
                //connection.disconnect();
                return REGISTER_FAILED;
            }
        }
    }

    private class SyncDownloadTask extends AsyncTask<URL, Integer, Integer[]> {

        @Override
        protected Integer[] doInBackground(URL... params) {
            Integer[] resume = new Integer[4];
            Arrays.fill(resume, 0);

            HashMap<String, String> downloadParameters = new HashMap<>();
            downloadParameters.put(PARAMETER_NAME_FORMAT, PARAMETER_VALUE_JSON);
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
                        resume[REGISTER_INTEGRATED] += 1;
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
                        resume[REGISTER_UPDATED] += 1;
                    } else {
                        resume[REGISTER_PRESENT] += 1;
                    }
                }
            } catch (Exception e) {
                resume[REGISTER_FAILED] += 1;
                connection.disconnect();
                return resume;
            }
            connection.disconnect();
            return resume;
        }
    }

}