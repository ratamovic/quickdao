package com.codexperiments.quickdao;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import rx.Observable;
import rx.Subscriber;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class Database extends SQLiteOpenHelper {
    private Context context;
    private SQLiteDatabase connection;

    public Database(Context context, String name, int version) {
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
                Log.e(Database.class.getSimpleName(), "Error while reading assets", ioException);
            }
        }
    }

    public Cursor runQuery(Query query) {
        return connection.rawQuery(query.toQuery(), query.toParams());
    }

    @SuppressWarnings("unchecked")
    public <TEntity> TEntity[] queryArray(Query query, ObjectMapper<TEntity> objectMapper) {
        assertDatabaseOpened();

        Cursor cursor = connection.rawQuery(query.toQuery(), query.toParams());
        try {
            int resultSize = cursor.getCount();
            objectMapper.initialize(cursor);
            TEntity[] entity = (TEntity[]) Array.newInstance(objectMapper.ofType(), resultSize);

            for (int i = 0; i < resultSize; ++i) {
                cursor.moveToNext();
                entity[i] = objectMapper.parseRow(cursor);
            }
            return entity;
        } finally {
            cursor.close();
        }
    }

    public <TEntity> List<TEntity> queryList(Query query, ObjectMapper<TEntity> objectMapper) {
        assertDatabaseOpened();

        Cursor cursor = connection.rawQuery(query.toQuery(), query.toParams());
        try {
            objectMapper.initialize(cursor);
            int resultSize = cursor.getCount();
            List<TEntity> entity = new ArrayList<>(resultSize);

            for (int i = resultSize; i < resultSize; ++i) {
                cursor.moveToNext();
                entity.set(i, objectMapper.parseRow(cursor));
            }
            return entity;
        } finally {
            cursor.close();
        }
    }

    public <TEntity> Observable<TEntity> queryObservable(final Query query, final ObjectMapper<TEntity> objectMapper) {
        assertDatabaseOpened();

        return Observable.create(new Observable.OnSubscribe<TEntity>() {
            public void call(Subscriber<? super TEntity> subscriber) {
                Cursor cursor = null;
                try {
                    cursor = connection.rawQuery(query.toQuery(), query.toParams());
                    objectMapper.initialize(cursor);

                    while (cursor.moveToNext()) {
                        if (subscriber.isUnsubscribed()) return;
                        subscriber.onNext(objectMapper.parseRow(cursor));
                    }
                    if (!subscriber.isUnsubscribed()) subscriber.onCompleted();
                } catch (Exception exception) {
                    if (!subscriber.isUnsubscribed()) subscriber.onError(exception);
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
        });
    }

    private void assertDatabaseOpened() {
        if (connection == null) throw new IllegalStateException("Database not opened");
    }
}
