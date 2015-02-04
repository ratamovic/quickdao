package com.codexperiments.quickdao.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public abstract class SQLiteDataSource extends SQLiteOpenHelper {
    private Context context;
    private SQLiteDatabase connection;

    public SQLiteDataSource(Context context, String name, int version) {
        super(context, name, null, version);
        this.context = context;
        this.connection = null;
        super.getWritableDatabase();
    }

    public SQLiteDatabase getConnection() {
        return connection;
    }

    @Override
    public void onOpen(SQLiteDatabase database) {
        connection = database;
        connection.execSQL("PRAGMA foreign_keys = ON");
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        connection = database;
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        connection = database;
    }

    @Override
    public void onDowngrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        connection = database;
    }

    public void beginTransaction() {
        connection.beginTransaction();
    }

    public void commit() {
        connection.setTransactionSuccessful();
        connection.endTransaction();
    }

    public void rollback() {
        connection.endTransaction();
    }

    public void executeScriptFromAssets(String filePath) throws IOException {
        executeScriptFromAssets(filePath, context);
    }

    public void executeScriptFromAssets(String filePath, Context context) throws IOException {
        assertDatabaseOpened();

        InputStream assetStream = null;
        connection.beginTransaction();
        try {
            assetStream = context.getAssets().open(filePath);
            byte[] script = new byte[assetStream.available()];
            assetStream.read(script);

            int previousIndex = 0;
            byte previousChar = '\0';
            byte currentChar;
            boolean ignore = false;
            int scriptSize = script.length;
            for (int i = 0; i < scriptSize; ++i) {
                currentChar = script[i];
                if ((currentChar == '\n' || currentChar == '\r') && (previousChar == ';')) {
                    String statement = new String(script, previousIndex, (i - previousIndex) + 1, "UTF-8");
                    if (!ignore && statement != null && !statement.isEmpty()) {
                        connection.execSQL(statement);
                    }

                    previousIndex = i + 1;
                    ignore = false;
                } else if ((currentChar == '-') && (previousChar == '-')) {
                    ignore = true;
                }

                previousChar = currentChar;
            }
            connection.setTransactionSuccessful();
        } finally {
            connection.endTransaction();
            try {
                if (assetStream != null) assetStream.close();
            } catch (IOException ioException) {
                Log.e(SQLiteDataSource.class.getSimpleName(), "Error while reading assets", ioException);
            }
        }
    }

    private void assertDatabaseOpened() {
        if (connection == null) throw new IllegalStateException("Database not opened");
    }
}
