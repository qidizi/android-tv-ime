package qidizi.tv_ime;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

// 指南 https://developer.android.com/training/data-storage/sqlite
public class Database extends SQLiteOpenHelper {

    private static Database instance;
    public static final String TABLE_NAME = "t";
    public static final String KEY_FIELD = "k";
    public static final String VALUE_FIELD = "v";
    private static final int DATABASE_VERSION = 3;
    // /data/data/qidizi.js_ime/databases 一般是放在这个目录，低版本android有可能支持自定义位置
    private static final String DATABASE_NAME = "sqlite.db";
    private static final String TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME
            + " (" + KEY_FIELD + " TEXT PRIMARY KEY, "
            + VALUE_FIELD + " TEXT NOT NULL);";

    public static Database getInstance(Context ctx) {
        if (instance == null) {
            instance = new Database(ctx.getApplicationContext());
        }
        return instance;
    }

    private Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }

    public void clear() {
        try {
            SQLiteDatabase database = instance.getWritableDatabase();
            database.delete(TABLE_NAME, null, null);
            database.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String get(String key) {
        if (TextUtils.isEmpty(key)) return null;
        String value = null;

        try {
            SQLiteDatabase database = instance.getReadableDatabase();
            Cursor cursor = database.query(Database.TABLE_NAME,
                    null,
                    Database.KEY_FIELD + " = ?",
                    new String[]{key}, null, null, null);

            if (cursor.moveToFirst()) {
                value = cursor.getString(1);
            }

            cursor.close();
            database.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }

    public void set(String key, String value) {
        try {
            if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value))
                return;

            String old = get(key);
            SQLiteDatabase database = instance.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(KEY_FIELD, key);
            values.put(VALUE_FIELD, value);
            if (old != null) {
                // 更新
                database.update(
                        TABLE_NAME, values,
                        KEY_FIELD + "=?", new String[]{key}
                );
            } else {
                // 插入
                database.insert(TABLE_NAME, null, values);
            }
            database.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void remove(String key) {
        try {
            if (TextUtils.isEmpty(key)) return;
            SQLiteDatabase database = instance.getWritableDatabase();
            database.delete(TABLE_NAME, KEY_FIELD + "='" + key + "'", null);
            database.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
