package com.fieldbook.tracker.Utilities;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by matarredona on 7/04/17.
 */

public class ConnectionHelper {

    private static String scheme;
    private static String restUrl;
    private static ArrayList<String> apiPath;
    private static CookieManager cookieManager;
    private static HashMap<String, String> defaultHeaders;

    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int DATARETRIEVAL_TIMEOUT = 10000;

    public ConnectionHelper(String url) {
        setRestPath(url);
        this.cookieManager = new CookieManager();
        this.defaultHeaders = new HashMap<>();
        defaultHeaders.put("Content-Type", "application/json");
        defaultHeaders.put("Accept", "application/json, */*");
    }

    private void setRestPath(String url) {
        this.scheme = "https";
        if (url.startsWith("https")) {
            url = url.split("//")[1];
        } else if (url.startsWith("http")) {
            url = url.split("//")[1];
            this.scheme = "http";
        }
        String[] path = url.split("/");
        this.restUrl = path[0];
        this.apiPath = new ArrayList<String>();
        for (int i = 1; i < path.length; i++) {
            if (!path[i].equals("")) {
                this.apiPath.add(path[i]);
            }
        }
    }

    private String getCredentialsForBasicAuth(String user, String password) {
        String credentials = user + ":" + password;
        String basicAuthCredentials = "Basic " + Base64.encodeToString(
                credentials.getBytes(),
                Base64.NO_WRAP
        );
        return basicAuthCredentials;
    }

    public HttpURLConnection getConnection(List<String> path, String body, HashMap<String, String> parameters, HashMap<String, String> headers, String requestMethod) {
        URL url = makeURL(path, parameters, requestMethod);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(DATARETRIEVAL_TIMEOUT);
            connection.setRequestMethod(requestMethod);
            if (headers != null) {
                defaultHeaders.putAll(headers);
            }
            setConnectionHeaders(connection, defaultHeaders);
            if (requestMethod.equals("POST")) {
                connection.setDoOutput(true);
                if (parameters != null) {
                    setConnectionContent(connection, null, parameters);
                }
                if (body != null) {
                    setConnectionContent(connection, body, null);
                }
            } else if (requestMethod.equals("GET")) {
                connection.setDoInput(true);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return connection;
    }

    private URL makeURL(List<String> path, HashMap<String, String> parameters, String requestMethod) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(scheme).authority(restUrl);
        ArrayList<String> fullPath = new ArrayList<String>(apiPath);
        fullPath.addAll(path);
        if (fullPath.size() != 0) {
            for (String p : fullPath) {
                builder.appendPath(p);
            }
        }
        builder.appendPath("");
        if (parameters != null && requestMethod.equals("GET")) {
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

    private void setConnectionHeaders(HttpURLConnection connection, HashMap<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
    }

    private void setConnectionContent(HttpURLConnection connection, String body, HashMap<String, String> parameters) {
        try {
            OutputStreamWriter os = new OutputStreamWriter(connection.getOutputStream(), "UTF-8");
            BufferedWriter writer = new BufferedWriter(os);
            if (parameters != null) {
                writer.write(getParametersStringAsJSON(parameters));
            }
            if (body != null) {
                writer.write(body);
            }
            writer.flush();
            writer.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getParametersStringAsJSON(HashMap<String, String> parameters) {
        String query = new JSONObject(parameters).toString();
        return query;
    }

    private String getParametersStringAsHTTP(HashMap<String, String> parameters) {
        Uri.Builder builder = new Uri.Builder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            builder.appendQueryParameter(entry.getKey(), entry.getValue());
        }
        String query = builder.build().getEncodedQuery();
        return query;
    }

    public void setToken(String token) {
        this.defaultHeaders.put("Authorization", token);
    }

    public String getConnectionContent(HttpURLConnection connection) {
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

    public String getCookie(HttpURLConnection connection, String cookieName) throws IOException {
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

    public void loadCookies(HttpURLConnection connection) {
        connection.setRequestProperty(
                "Cookie",
                TextUtils.join(";", cookieManager.getCookieStore().getCookies())
        );
    }

}