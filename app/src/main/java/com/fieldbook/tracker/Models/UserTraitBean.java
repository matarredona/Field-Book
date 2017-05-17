package com.fieldbook.tracker.Models;

import android.database.Cursor;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by DS Matarredona on 11/05/17.
 */

public class UserTraitBean {

    private String rid;
    private String parent;
    private String trait;
    private String userValue;
    private Date timeTaken;
    private String person;
    private String location;
    private int rep;
    private String notes;
    private String exp_id;

    public UserTraitBean(String rid, String parent, String trait, String userValue, Date timeTaken, String person, String location, int rep, String notes, String exp_id) {
        this.rid = rid;
        this.parent = parent;
        this.trait = trait;
        this.userValue = userValue;
        this.timeTaken = timeTaken;
        this.person = person;
        this.location = location;
        this.rep = rep;
        this.notes = notes;
        this.exp_id = exp_id;
    }

    public UserTraitBean(Cursor cursor) throws ParseException {
        this.rid = cursor.getString(cursor.getColumnIndex("rid"));
        this.parent = cursor.getString(cursor.getColumnIndex("parent"));
        this.trait = cursor.getString(cursor.getColumnIndex("trait"));
        this.userValue = cursor.getString(cursor.getColumnIndex("userValue"));
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        this.timeTaken = dateFormatter.parse(cursor.getString(cursor.getColumnIndex("timeTaken")));
        this.person = cursor.getString(cursor.getColumnIndex("person"));
        this.location = cursor.getString(cursor.getColumnIndex("location"));
        this.rep = Integer.valueOf(cursor.getString(cursor.getColumnIndex("rep")));
        this.notes = cursor.getString(cursor.getColumnIndex("notes"));
        this.exp_id = cursor.getString(cursor.getColumnIndex("exp_id"));
    }

    public UserTraitBean (JSONObject json ) throws JSONException, ParseException {
        this.rid = json.getString("rid");
        this.parent = json.getString("parent");
        this.trait = json.getString("trait");
        this.userValue = json.getString("userValue");
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        this.timeTaken = dateFormatter.parse(json.getString("timeTaken"));
        this.person = json.getString("person");
        this.location = json.getString("location");
        this.rep = Integer.valueOf(json.getString("rep"));
        this.notes = json.getString("notes");
        this.exp_id = json.getString("exp_id");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("rid", rid);
        json.put("parent", parent);
        json.put("trait", trait);
        json.put("userValue", userValue);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        json.put("timeTaken", dateFormatter.format(timeTaken));
        json.put("person", person);
        json.put("rep", rep);
        json.put("notes", notes);
        json.put("exp_id", exp_id);
        return json;
    }

    public boolean equals(UserTraitBean userTraitBean) {
        if (this.getRid() == userTraitBean.getRid() &&
                this.getParent() == userTraitBean.getParent() &&
                this.timeTaken.equals(userTraitBean.getTimeTaken())) {
            return true;
        } else {
            return false;
        }
    }

    public String getRid() {
        return rid;
    }

    public String getParent() {
        return parent;
    }

    public String getTrait() {
        return trait;
    }

    public String getUserValue() {
        return userValue;
    }

    public Date getTimeTaken() {
        return timeTaken;
    }

    public String getPerson() {
        return person;
    }

    public String getLocation() {
        return location;
    }

    public int getRep() {
        return rep;
    }

    public String getNotes() {
        return notes;
    }

    public String getExp_id() {
        return exp_id;
    }
}
