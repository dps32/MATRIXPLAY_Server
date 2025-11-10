package com.server;

import org.json.JSONObject;

public class GameObject {
    public String id;
    public GameObject(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return this.toJSON().toString();
    }
    
    // Converteix l'objecte a JSON
    public JSONObject toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        return obj;
    }

    // Crea un GameObjects a partir de JSON
    public static GameObject fromJSON(JSONObject obj) {
        GameObject go = new GameObject(
            obj.optString("id", null)
        );
        return go;
    }
}
