package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.Models.UserTraitBean;
import com.fieldbook.tracker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Created by matarredona on 22/03/17.
 */

public class SyncHelper {

    private Context context;
    private String restUser;
    private String restPassword;
    private HashMap<String, String> additionalFields;

    private ConnectionHelper connectionHelper;
    private ContentHelper contentHelper;

    private static final String PARAMETER_NAME_FORMAT = "format";
    private static final String PARAMETER_VALUE_JSON = "json";

    private static final List<String> PATH_AUTH_TOKEN = Arrays.asList(
            "api-token-auth"
    );
    private static final List<String> PATH_DATA_SYNC = Arrays.asList(
            "api",
            "fieldbook_observations"
    );
    private static final List<String> PATH_DATA_PRODUCTION_UPLOAD = Arrays.asList(
            "api",
            "plants"
    );

    private static final int MAX_UPLOAD_ATTEMPTS = 2;
    private static final int MAX_SIMULTANEOUS_SERVER_CALLS = 24;
    private static final int UPLOAD_NOTIFICATION_STEP_SIZE = 200;

    public SyncHelper(Context context, SharedPreferences sharedPreferences, DataHelper database) {
        this.context = context;
        this.restUser = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        this.restPassword = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");
        HashSet<String> additionalFieldsSet = (HashSet<String>) sharedPreferences.getStringSet(context.getString(R.string.syncadditionalfieldspreference), null);
        for (String keyValuePair : additionalFieldsSet) {
            String[] nameValue = keyValuePair.split(": ");
            additionalFields.put(nameValue[0], nameValue[1]);
        }

        String restUrl = sharedPreferences.getString(context.getString(R.string.syncurlpreference), "");
        this.connectionHelper = new ConnectionHelper(restUrl);

        String importUniqueName = sharedPreferences.getString(context.getString(R.string.uniquenamepreference), "");
        boolean onlyUnique = sharedPreferences.getBoolean(context.getString(R.string.synconlyuniquepreference), true);
        boolean onlyActive = sharedPreferences.getBoolean(context.getString(R.string.synconlyactivepreference), true);
        this.contentHelper = new ContentHelper(database, importUniqueName, onlyUnique, onlyActive);
    }
/*
    public void performSyncUpload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get()) {
                showToast(context.getString(R.string.restuploading));
                //prepare registers to upload
                Cursor data = contentHelper.getLocalData();
                ArrayList<String> rawRegisters = new ArrayList<String>();
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
                    rawRegisters.add(register);
                }
                data.close();
                //server calls are made in batches
                System.setProperty("http.keepAlive", "true");
                List<List<String>> preparedRegisters = contentHelper.chopList(rawRegisters, MAX_SIMULTANEOUS_SERVER_CALLS);
                int remainingRegistersToUpload = rawRegisters.size();
                int remainingRegistersFromLastNotification = rawRegisters.size();
                int registersFailed = 0;
                int registersCreated = 0;
                int registersPresent = 0;
                for (List<String> registers : preparedRegisters) {
                    int stepSize = remainingRegistersFromLastNotification - remainingRegistersToUpload;
                    if (stepSize > UPLOAD_NOTIFICATION_STEP_SIZE) {
                        showToast(context.getString(R.string.restregistersremaining) + remainingRegistersToUpload);
                        remainingRegistersFromLastNotification -= stepSize;
                    }
                    int attempts = 0;
                    //unsuccessful tasks are relaunched
                    while (!registers.isEmpty() && attempts < MAX_UPLOAD_ATTEMPTS) {
                        attempts += 1;
                        ArrayList<SyncUploadTask> tasks = new ArrayList<SyncUploadTask>();
                        //launch tasks
                        for (String register : registers) {
                            SyncUploadTask task = new SyncUploadTask(register);
                            tasks.add(task);
                            task.execute();
                        }
                        //get tasks responses
                        for (SyncUploadTask task : tasks) {
                            HashMap<String, String> uploadResume = task.get();
                            switch (uploadResume.get("response code")) {
                                case "201":
                                    registersCreated += 1;
                                    tasks.remove(task);
                                    break;
                                case "200":
                                    registersPresent += 1;
                                    tasks.remove(task);
                                    break;
                                default:
                                    Log.d(uploadResume.get("response code"), uploadResume.get("response detail"));
                                    break;
                            }

                        }
                        if (attempts == MAX_UPLOAD_ATTEMPTS) {
                            registersFailed += tasks.size();
                        }
                    }
                    remainingRegistersToUpload -= registers.size();
                }
                showToast(context.getString(R.string.restregisterscreated) + registersCreated + "\n"
                        + context.getString(R.string.restregisterspresent) + registersPresent + "\n"
                        + context.getString(R.string.restregistersfailed) + registersFailed
                );
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
*/
    public void performSyncUpload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get()) {
                //upload connections are time consuming
                //a get call is performed first
                //registers already present in the remote database won't be uploaded
                showToast(context.getString(R.string.restuploading));
                SyncDownloadTask downloadTask = new SyncDownloadTask();
                JSONArray remoteData = downloadTask.execute().get();
                HashSet<UserTraitBean> remoteDataSet = contentHelper.getSet(remoteData);
                //Cursor localData = contentHelper.getLocalData();
                Cursor localData = contentHelper.getDataBase().getAllUserTraits();
                HashSet<UserTraitBean> localDataSet = contentHelper.getSet(localData);
                localData.close();
                localDataSet.removeAll(remoteDataSet);
                for (UserTraitBean register : remoteDataSet) {
                    localDataSet.remove(register);
                }

                ArrayList<String> rawRegisters = new ArrayList<String>();
                for (UserTraitBean register : localDataSet) {
                    JSONObject json = register.toJSON();
                    for (String key : additionalFields.keySet()) {
                        json.put(key, additionalFields.get(key));
                    }
                    String jsonRegister = json.toString();
                    rawRegisters.add(jsonRegister);
                }
                //post calls are made in batches
                System.setProperty("http.keepAlive", "true");
                List<List<String>> preparedRegisters = contentHelper.chopList(rawRegisters, MAX_SIMULTANEOUS_SERVER_CALLS);
                int remainingRegistersToUpload = rawRegisters.size();
                int remainingRegistersFromLastNotification = rawRegisters.size();
                int registersFailed = 0;
                int registersCreated = 0;
                int registersPresent = 0;
                for (List<String> registers : preparedRegisters) {
                    int stepSize = remainingRegistersFromLastNotification - remainingRegistersToUpload;
                    if (stepSize > UPLOAD_NOTIFICATION_STEP_SIZE) {
                        showToast(context.getString(R.string.restregistersremaining) + remainingRegistersToUpload);
                        remainingRegistersFromLastNotification -= stepSize;
                    }
                    int attempts = 0;
                    //unsuccessful tasks are relaunched
                    while (!registers.isEmpty() && attempts < MAX_UPLOAD_ATTEMPTS) {
                        attempts += 1;
                        ArrayList<SyncUploadTask> tasks = new ArrayList<SyncUploadTask>();
                        //launch tasks
                        for (String register : registers) {
                            SyncUploadTask task = new SyncUploadTask(register);
                            tasks.add(task);
                            task.execute();
                        }
                        //get tasks responses
                        for (SyncUploadTask task : tasks) {
                            HashMap<String, String> uploadResume = task.get();
                            switch (uploadResume.get("response code")) {
                                case "201":
                                    registersCreated += 1;
                                    tasks.remove(task);
                                    break;
                                case "200":
                                    registersPresent += 1;
                                    tasks.remove(task);
                                    break;
                                default:
                                    Log.d(uploadResume.get("response code"), uploadResume.get("response detail"));
                                    break;
                            }

                        }
                        if (attempts == MAX_UPLOAD_ATTEMPTS) {
                            registersFailed += tasks.size();
                        }
                    }
                    remainingRegistersToUpload -= registers.size();
                }
                showToast(context.getString(R.string.restregisterscreated) + registersCreated + "\n"
                        + context.getString(R.string.restregisterspresent) + registersPresent + "\n"
                        + context.getString(R.string.restregistersfailed) + registersFailed
                );
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            showToast(context.getString(R.string.restdownloadfailed));
            e.printStackTrace();
        }

    }
/*
    public void performSyncDownload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get()) {
                showToast(context.getString(R.string.restdownloading));
                SyncDownloadTask downloadTask = new SyncDownloadTask();
                HashMap<String, String> downloadResume = downloadTask.execute().get();
                if (!downloadResume.get("response code").equals("200")) {
                    showToast(context.getString(R.string.restdownloadfailed));
                    showToast(downloadResume.get("response detail"));
                } else {
                    showToast(context.getString(R.string.restregisterscreated) + downloadResume.get("registers created") + "\n"
                            + context.getString(R.string.restregistersupdated) + downloadResume.get("registers updated") + "\n"
                            + context.getString(R.string.restregisterspresent) + downloadResume.get("registers present") + "\n"
                            + context.getString(R.string.restregistersfailed) + downloadResume.get("registers failed")
                    );
                }
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
*/
    public void performSyncDownload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get()) {
                showToast(context.getString(R.string.restdownloading));
                SyncDownloadTask downloadTask = new SyncDownloadTask();
                JSONArray data = downloadTask.execute().get();
                if (data == null) {
                    showToast(context.getString(R.string.restdownloadfailed));
                } else {
                    int registersCreated = 0;
                    int registersUpdated = 0;
                    int registersPresent = 0;
                    int registersFailed = 0;
                    //iterate over retrieved registers
                    for (int i = 0; i < data.length(); i++) {
                        UserTraitBean remoteRegister = new UserTraitBean(data.getJSONObject(i));
                        //try register integration
                        try {
                            Cursor cursor = contentHelper.getDataBase().findUserTraitsRegister(remoteRegister);
                            //insert register if it's not present in local database
                            if (!cursor.moveToNext()) {
                                contentHelper.getDataBase().insertUserTraitsFromRemoteOrigin(remoteRegister);
                                registersCreated += 1;
                            } else {
                                //update register if it's present with a previous date
                                UserTraitBean localRegister = new UserTraitBean(cursor);
                                if (localRegister.getTimeTaken().before(remoteRegister.getTimeTaken())) {
                                    contentHelper.getDataBase().updateUserTraitsFromRemoteOrigin(remoteRegister);
                                    registersUpdated += 1;
                                    //register is already present
                                } else {
                                    registersPresent += 1;
                                }
                            }
                            //register integration failed
                        } catch (Exception e) {
                            registersFailed += 1;
                        }
                    }
                    showToast(context.getString(R.string.restregisterscreated) + registersCreated + "\n"
                            + context.getString(R.string.restregistersupdated) + registersUpdated + "\n"
                            + context.getString(R.string.restregisterspresent) + registersPresent + "\n"
                            + context.getString(R.string.restregistersfailed) + registersFailed
                    );
                }
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
            //try token retrieval
            try {
                connection.connect();
                if (connection.getResponseCode() == 200) {
                    JSONObject tokenJSON = new JSONObject(connectionHelper.getConnectionContent(connection));
                    String tokenValue = "Token " + tokenJSON.getString("token");
                    connectionHelper.setToken(tokenValue);
                    connection.disconnect();
                    return true;
                } else {
                    connection.disconnect();
                    return false;
                }
            } catch (Exception e) {
                connection.disconnect();
                return false;
            }
        }
    }

    private class SyncUploadTask extends AsyncTask<URL, Integer, HashMap<String, String>> {

        private String body;

        public SyncUploadTask(String body) {
            this.body = body;
        }

        @Override
        protected HashMap<String, String> doInBackground(URL... params) {
            HashMap<String, String> resume = new HashMap<>();
            resume.put("response code", "");
            resume.put("response detail", "");
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_PRODUCTION_UPLOAD, body, null, null, "POST");
            //try posting
            try {
                connection.connect();
                resume.put("response code", Integer.toString(connection.getResponseCode()));
                //try response retrieval
                try {
                    JSONObject response = new JSONObject(connectionHelper.getConnectionContent(connection));
                    resume.put("response detail", response.getString("detail"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return resume;
        }
    }
/*
    private class SyncDownloadTask extends AsyncTask<URL, Integer, HashMap<String, String>> {

        @Override
        protected HashMap<String, String> doInBackground(URL... params) {
            HashMap<String, String> resume = new HashMap<>();
            resume.put("response code", "");
            resume.put("response detail", "");
            HashMap<String, String> downloadParameters = new HashMap<>();
            downloadParameters.put(PARAMETER_NAME_FORMAT, PARAMETER_VALUE_JSON);
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_SYNC, null, downloadParameters, null, "GET");
            //try connecting and retrieving data
            try {
                connection.connect();
                resume.put("response code", Integer.toString(connection.getResponseCode()));
                if (connection.getResponseCode() == 200) {
                    JSONArray data = new JSONArray(connectionHelper.getConnectionContent(connection));
                    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                            Locale.getDefault());
                    int registersCreated = 0;
                    int registersUpdated = 0;
                    int registersPresent = 0;
                    int registersFailed = 0;
                    //iterate over retrieved registers
                    for (int i = 0; i < data.length(); i++) {
                        HashMap<String, String> register = contentHelper.getMap(data.getJSONObject(i));
                        //try register integration
                        try {
                            Cursor localRegister = contentHelper.getDataBase().getUserTraitsRegister(
                                    register.get("rid"),
                                    register.get("trait")
                            );
                            //insert register if it's not present in local database
                            if (!localRegister.moveToNext()) {
                                contentHelper.getDataBase().insertUserTraitsFromRemoteOrigin(
                                        register.get("rid"),
                                        register.get("parent"),
                                        register.get("trait"),
                                        register.get("userValue"),
                                        register.get("timeTaken"),
                                        register.get("person"),
                                        register.get("location"),
                                        register.get("rep"),
                                        register.get("notes"),
                                        register.get("exp_id")
                                );
                                registersCreated += 1;
                            } else {
                                //update register if it's present with a previous date
                                Date localDate = dateFormatter.parse(localRegister.getString(localRegister.getColumnIndex("timeTaken")));
                                Date remoteDate = dateFormatter.parse(register.get("timeTaken"));
                                if (localDate.before(remoteDate)) {
                                    contentHelper.getDataBase().updateUserTraitsFromRemoteOrigin(
                                            register.get("rid"),
                                            register.get("parent"),
                                            register.get("trait"),
                                            register.get("userValue"),
                                            register.get("timeTaken"),
                                            register.get("person"),
                                            register.get("location"),
                                            register.get("rep"),
                                            register.get("notes"),
                                            register.get("exp_id")
                                    );
                                    registersUpdated += 1;
                                //register is already present
                                } else {
                                    registersPresent += 1;
                                }
                            }
                        //register integration failed
                        } catch (Exception e) {
                            registersFailed += 1;
                        }
                    }
                    resume.put("registers created", Integer.toString(registersCreated));
                    resume.put("registers updated", Integer.toString(registersUpdated));
                    resume.put("registers present", Integer.toString(registersPresent));
                    resume.put("registers failed", Integer.toString(registersFailed));
                //response code != 200
                } else {
                    try {
                        JSONObject response = new JSONObject(connectionHelper.getConnectionContent(connection));
                        resume.put("response detail", response.getString("detail"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            //connection or data retrieval failed
            } catch (Exception e) {
                connection.disconnect();
                e.printStackTrace();
            }
            connection.disconnect();
            return resume;
        }
    }
*/
    private class SyncDownloadTask extends AsyncTask<URL, Integer, JSONArray> {

        @Override
        protected JSONArray doInBackground(URL... params) {
            JSONArray data = null;
            HashMap<String, String> downloadParameters = new HashMap<>();
            downloadParameters.put(PARAMETER_NAME_FORMAT, PARAMETER_VALUE_JSON);
            HttpURLConnection connection = connectionHelper.getConnection(PATH_DATA_SYNC, null, downloadParameters, null, "GET");
            //try connecting and retrieving data
            try {
                connection.connect();
                if (connection.getResponseCode() == 200) {
                    data = new JSONArray(connectionHelper.getConnectionContent(connection));
                } else {
                    try {
                        int responseCode = connection.getResponseCode();
                        JSONObject response = new JSONObject(connectionHelper.getConnectionContent(connection));
                        String detail = response.getString("detail");
                        showToast(responseCode + ":" + detail);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return data;
        }
    }

}