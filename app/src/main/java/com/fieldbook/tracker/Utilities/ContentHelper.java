package com.fieldbook.tracker.Utilities;

import android.database.Cursor;

import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.Models.UserTraitBean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Created by matarredona on 7/04/17.
 */

public class ContentHelper {

    private String importUniqueName;
    private boolean onlyUnique;
    private boolean onlyActive;
    private DataHelper dataBase;

    public ContentHelper(DataHelper dataBase, String importUniqueName, boolean onlyUnique, boolean onlyActive) {
        this.dataBase = dataBase;
        this.importUniqueName = importUniqueName;
        this.onlyUnique = onlyUnique;
        this.onlyActive = onlyActive;
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

    public <T> List<List<T>> chopList(List<T> list, int partSize) {
        List<List<T>> parts = new ArrayList<List<T>>();
        int listSize = list.size();
        for (int i = 0; i < listSize; i += partSize) {
            parts.add(new ArrayList<T>(
                    list.subList(i, Math.min(listSize, i + partSize)))
            );
        }
        return parts;
    }

    public HashMap<String, String> getMap(JSONObject register) throws JSONException {
        HashMap<String, String> registerMap = new HashMap<>();
        Iterator keys = register.keys();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            String value = register.getString(key);
            registerMap.put(key, value);
        }
        return registerMap;
    }

    public HashSet<UserTraitBean> getSet(JSONArray data) throws JSONException, ParseException {
        HashSet<UserTraitBean> dataSet = new HashSet<UserTraitBean>();
        for (int i = 0; i < data.length(); i++) {
            UserTraitBean register = new UserTraitBean(data.getJSONObject(i));
            dataSet.add(register);
        }
        return dataSet;
    }

    public HashSet<UserTraitBean> getSet(Cursor data) throws ParseException {
        HashSet<UserTraitBean> dataSet = new HashSet<UserTraitBean>();
        while (data.moveToNext()) {
            UserTraitBean register = new UserTraitBean(data);
            dataSet.add(register);
        }
        return dataSet;
    }

    public DataHelper getDataBase() {
        return dataBase;
    }
}