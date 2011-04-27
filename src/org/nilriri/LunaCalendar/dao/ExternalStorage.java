package org.nilriri.LunaCalendar.dao;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class ExternalStorage extends SQLiteOpenHelper implements StorageSelector {
    private Context mContext;
    private CursorFactory mFactory;
    private SQLiteDatabase db;

    public ExternalStorage(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);

        mContext = context;

        Log.d("onCreate", "EXTERNAL_DB_NAME=" + Constants.EXTERNAL_DB_NAME);

        db = SQLiteDatabase.openOrCreateDatabase(Constants.EXTERNAL_DB_NAME, factory);

        Log.d("ExternalStorage", "db.getVersion()=" + db.getVersion());

        if (Constants.EXTERNAL_DB_VERSION != db.getVersion()) {

            switch (db.getVersion()) {
                case 0:
                    onCreate(db);
                    break;
                default:

                    onUpgrade(db, db.getVersion(), Constants.EXTERNAL_DB_VERSION);
                    break;
            }

            db.setVersion(Constants.EXTERNAL_DB_VERSION);
        }

        db = getWritableDatabase();

    }

    public SQLiteDatabase getReadableDatabase() {
        if (db == null) {
            db = SQLiteDatabase.openDatabase(Constants.EXTERNAL_DB_NAME, mFactory, SQLiteDatabase.OPEN_READONLY);
        } else if (!db.isOpen()) {
            db = SQLiteDatabase.openDatabase(Constants.EXTERNAL_DB_NAME, mFactory, SQLiteDatabase.OPEN_READONLY);
        }
        return db;
    }

    public SQLiteDatabase getWritableDatabase() {
        Log.d("��������", "Location=ExternalStorage.getWritableDatabase");
        if (db == null) {
            db = SQLiteDatabase.openDatabase(Constants.EXTERNAL_DB_NAME, mFactory, SQLiteDatabase.OPEN_READWRITE);
        } else if (db.isReadOnly()) {
            close();
            db = SQLiteDatabase.openDatabase(Constants.EXTERNAL_DB_NAME, mFactory, SQLiteDatabase.OPEN_READWRITE);
        }
        return db;
    }

    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        new DaoCreator().onCreate(this.mContext, db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        new DaoCreator().onUpgrade(this.mContext, db, oldVersion, newVersion);

    }

    public Context getContext() {
        return mContext;
    }

    public void onDestroy() {

        if (db != null) {
            db.close();
        }

        super.close();

    }

}
