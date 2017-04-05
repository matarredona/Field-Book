package com.fieldbook.tracker.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Created by matarredona on 22/03/17.
 */

public class SyncHelper {

    private Context context;
    private DataHelper dataBase;
    private String restUrl;
    private String restUser;
    //private String basicAuthCredentials;
    private String restPassword;
    private boolean onlyUnique;
    private boolean onlyActive;
    private String importUniqueName;
    private String scheme;
    private ArrayList<String> API_PATH;
    private CookieManager cookieManager;

    private static final String PARAMETER_NAME_USER = "user";
    private static final String PARAMETER_NAME_PASSWORD = "pass";
    private static final String PARAMETER_NAME_OPERATION = "op";
    private static final String PARAMETER_NAME_DATA = "data";
    private static final String PARAMETER_NAME_FORMAT = "format";
    private static final String PARAMETER_VALUE_JSON = "json";
    //    private static final String OPERATION_PARAMETER_VALUE_AUTHENTICATION = "auth";
    private static final String OPERATION_PARAMETER_VALUE_UPLOAD = "up";
    private static final String OPERATION_PARAMETER_VALUE_DOWNLOAD = "down";
    private static final String JSON_DATA_KEY = "down";
    private static final String COOKIE_NAME_TOKEN = "csrftoken";
    private static final String COOKIE_NAME_SESSION = "sessionid";

    private static final List<String> PATH_LOGIN = Arrays.asList(
            "login"
    );
    private static final List<String> PATH_AUTH = Arrays.asList(
            "api-auth",
            "login"
    );
    private static final List<String> PATH_PLANTS = Arrays.asList(
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

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int DATARETRIEVAL_TIMEOUT = 5000;

    public SyncHelper(Context context, SharedPreferences sharedPreferences, DataHelper database) {
        this.context = context;
        this.dataBase = database;
        setRestPath(sharedPreferences);
        //this.basicAuthCredentials = getCredentialsForBasicAuth(sharedPreferences);
        this.restUser = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        this.restPassword = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");
        this.onlyUnique = sharedPreferences.getBoolean(context.getString(R.string.synconlyuniquepreference), true);
        this.onlyActive = sharedPreferences.getBoolean(context.getString(R.string.synconlyactivepreference), true);
        this.importUniqueName = sharedPreferences.getString(context.getString(R.string.uniquenamepreference), "");
        this.cookieManager = new CookieManager();
//        CookieHandler.setDefault(cookieManager);
    }

    public void performSyncUpload() {
        AuthenticationTask authenticationTask = new AuthenticationTask();
        try {
            if (authenticationTask.execute().get() == 1) {
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
                    URL url = makeURL(PATH_PLANTS, uploadParameters);
                    SyncUploadTask uploadTask = new SyncUploadTask();
                    uploadTask.execute(url);
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
                URL url = makeURL(PATH_PLANTS, downloadParameters);
                SyncDownloadTask downloadTask = new SyncDownloadTask();
                downloadTask.execute(url);
            } else {
                showToast(context.getString(R.string.restautenticationfailed));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private URL makeURL(List<String> path, HashMap<String, String> parameters) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(scheme).authority(restUrl);
        ArrayList<String> fullPath = new ArrayList<String>(API_PATH);
        fullPath.addAll(path);
        if (fullPath != null) {
            for (String p : fullPath) {
                builder.appendPath(p);
            }
        }
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                builder.appendQueryParameter(entry.getKey(), entry.getValue());
            }
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
            connection.setDoInput(true);
            connection.setDoOutput(true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    private void setConnectionParameters(HttpURLConnection connection, HashMap<String, String> parameters) {
        try {
            OutputStreamWriter os = new OutputStreamWriter(connection.getOutputStream());
            BufferedWriter writer = new BufferedWriter(os);
            writer.write(getParametersString(parameters));
            writer.flush();
            writer.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getParametersString(HashMap<String, String> parameters) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            try {
                result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return result.toString();
    }

    private String getConnectionContent(HttpURLConnection connection) {
        String content = "";
        try {
            InputStreamReader in = new InputStreamReader(connection.getInputStream());
            BufferedReader reader = new BufferedReader(in);
            String line = reader.readLine();
            while (line != null) {
                content += line;
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }

    private String getCookie(HttpURLConnection connection, String cookieName) throws IOException {
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
        String cookieValue = "";
        if (cookies != null) {
            for (String cookie : cookies) {
                if (cookie.startsWith(cookieName)) {
                    cookieValue = cookie.split("=")[1].split(";")[0];
                    cookieManager.getCookieStore().add(null, HttpCookie.parse(cookie).get(0));
                    break;
                }
            }
        }
        return cookieValue;
    }

    private void loadCookie(HttpURLConnection connection, String cookieName) {
        connection.setRequestProperty(
                "cookie",
                TextUtils.join(";", cookieManager.getCookieStore().getCookies())
        );
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

    private void setRestPath(SharedPreferences sharedPreferences) {
        String url = sharedPreferences.getString(context.getString(R.string.syncurlpreference), "");
        this.scheme = "https";
        if (url.startsWith("https")) {
            url = url.split("//")[1];
        } else if (url.startsWith("http")) {
            url = url.split("//")[1];
            this.scheme = "http";
        }
        String[] path = url.split("/");
        this.restUrl = path[0];
        this.API_PATH = new ArrayList<String>();
        for (int i = 1; i < path.length; i++) {
            if (!path[i].equals("")) {
                this.API_PATH.add(path[i]);
            }
        }
    }

    private String getCredentialsForBasicAuth(SharedPreferences sharedPreferences) {
        String user = sharedPreferences.getString(context.getString(R.string.syncuserpreference), "");
        String password = sharedPreferences.getString(context.getString(R.string.syncpasspreference), "");
        String credentials = user + ":" + password;
        String basicAuthCredentials = "Basic " + Base64.encodeToString(
                credentials.getBytes(),
                Base64.NO_WRAP
        );
        return basicAuthCredentials;
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

    private class AuthenticationTask extends AsyncTask<URL, Integer, Long> {

        @Override
        protected Long doInBackground(URL... params) {
            URL url = makeURL(PATH_AUTH, null);
            HttpURLConnection tokenConnection = getConnection(url, "POST");
            HttpURLConnection authConnection = getConnection(url, "POST");
            //connection.setRequestProperty("Authorization", basicAuthCredentials);
            try {
                String csrfToken = getCookie(tokenConnection, COOKIE_NAME_TOKEN);
                loadCookie(authConnection, COOKIE_NAME_TOKEN);
                HashMap<String, String> authenticationParameters = new HashMap<>();
                authenticationParameters.put("csrfmiddlewaretoken", csrfToken);
                authenticationParameters.put("username", restUser);
                authenticationParameters.put("password", restPassword);
                setConnectionParameters(authConnection, authenticationParameters);
                getCookie(tokenConnection, COOKIE_NAME_SESSION);
                String content = getConnectionContent(authConnection);
                if (authConnection.getResponseCode() != 200) {
                    authConnection.disconnect();
                    return Long.valueOf(0);
                }
                authConnection.disconnect();
                return Long.valueOf(1);
            } catch (Exception e) {
                authConnection.disconnect();
                return Long.valueOf(0);
            }
        }
    }

    private class SyncUploadTask extends AsyncTask<URL, Integer, Long> {

        @Override
        protected Long doInBackground(URL... params) {
            HttpURLConnection connection = getConnection(params[0], "POST");
            loadCookie(connection, COOKIE_NAME_TOKEN);
            loadCookie(connection, COOKIE_NAME_SESSION);
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
            loadCookie(connection, COOKIE_NAME_TOKEN);
            loadCookie(connection, COOKIE_NAME_SESSION);
            try {
                connection.connect();
                JSONObject response = new JSONObject(getConnectionContent(connection));
                JSONArray data = response.getJSONArray(JSON_DATA_KEY);
                String[] columnNames = getResponseColumnNames(data);
                for (int i = 0; i < data.length(); i++) {
                    JSONObject register = data.getJSONObject(i);
                    String[] registerValues = getRegisterValues(register, columnNames);
                    String rid = registerValues[RID];
                    String trait = registerValues[TRAIT];
                    Cursor localRegister = dataBase.getUserTraitsRegister(rid, trait);
                    //insert register if it's not present in local database
                    if (!localRegister.moveToNext()) {
                        dataBase.insertUserTraitsFromRemoteOrigin(
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
                        dataBase.updateUserTraitsFromRemoteOrigin(
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