package abdom.dip.jp.anki;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DBHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "mydata.db";
    private static final int DATABASE_VERSION = 1;

    /*
     * 定数データテーブル
     */
    public static final String CONSTANT_TABLE_NAME = "const";
    public static final String KEY = "key";
    public static final String VALUE = "value";

    /**
     * @param context
     */
    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String query = "create table " + CONSTANT_TABLE_NAME +
                "(" + KEY + " TEXT PRIMARY KEY,"
                + VALUE + " TEXT);";
        db.execSQL(query);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("drop table if exists " + CONSTANT_TABLE_NAME);
        onCreate(db);

    }

    /**
     * 定数テーブルから値を取得する
     *
     * @param key キー文字列
     * @return	値文字列
     */
    public String getConstant(String key) {
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor c = db.query(CONSTANT_TABLE_NAME, null,
                KEY + " = ?", new String[] { key },
                null, null, null);

        if (c.moveToFirst()) {
            db.close();
            return c.getString(1);
        } else {
            db.close();
            return null;
        }
    }

    /**
     * 定数テーブルに値を格納する
     *
     * @param key キー文字列
     * @param value 値文字列
     */
    public void putConstant(String key, String value) {
        String v = getConstant(key);
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();

        Log.i("Anki.Yusuke:", "DBHelper putConst key = "+key);
        Log.i("Anki.Yusuke:", "DBHelper putConst val = "+value);

        values.put(KEY, key);
        values.put(VALUE, value);
        if (v == null) {
            // insert する
            db.insert(CONSTANT_TABLE_NAME, "default", values);
        } else {
            // update する
            db.update(CONSTANT_TABLE_NAME, values, KEY + " = ?", new String[] { key });
        }
        db.close();
    }
}
