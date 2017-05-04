package com.fieldbook.tracker.Utilities;

import android.database.Cursor;

import com.fieldbook.tracker.DataHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

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

    public String[] getResponseColumnNames(JSONArray data) throws JSONException {
        JSONObject register = data.getJSONObject(0);
        Iterator keys = register.keys();
        String[] columnNames = new String[getIteratorLength(register.keys())];
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
            iterator.next();
        }
        return length;
    }

    public String[] getRegisterValues(JSONObject register, String[] columnNames) throws JSONException {
        String[] registerValues = new String[columnNames.length];
        for (int i = 0; i < registerValues.length; i++) {
            registerValues[i] = (String) register.get(columnNames[i]);
        }
        return registerValues;
    }

    public DataHelper getDataBase() {
        return dataBase;
    }
}