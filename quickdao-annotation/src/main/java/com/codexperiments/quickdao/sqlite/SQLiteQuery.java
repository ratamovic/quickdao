package com.codexperiments.quickdao.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.codexperiments.quickdao.EntityMapper;
import com.codexperiments.quickdao.Query;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class SQLiteQuery<TEntity, TEntityMapper extends EntityMapper<TEntity>> implements Query<TEntity> {
    protected SQLiteDatabase connection;
    public TEntityMapper entityMapper;
    protected SQLiteQueryBuilder queryBuilder;

    public SQLiteQuery(SQLiteDatasource datasource, TEntityMapper entityMapper, SQLiteQueryBuilder queryBuilder) {
        this.connection = datasource.getConnection();
        this.entityMapper = entityMapper;
        this.queryBuilder = queryBuilder;
    }

    public Cursor asCursor() {
        return connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
    }

    public TEntity[] asArray() {
        Cursor cursor = connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
        try {
            int resultSize = cursor.getCount();
            entityMapper.initialize(cursor);
            TEntity[] entity = (TEntity[]) Array.newInstance(entityMapper.ofType(), resultSize);

            for (int i = 0; i < resultSize; ++i) {
                cursor.moveToNext();
                entity[i] = entityMapper.parseRow(cursor);
            }
            return entity;
        } finally {
            cursor.close();
        }
    }

    public List<TEntity> asList() {
        Cursor cursor = connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
        try {
            entityMapper.initialize(cursor);
            int resultSize = cursor.getCount();
            List<TEntity> entity = new ArrayList<>(resultSize);

            for (int i = 0; i < resultSize; ++i) {
                cursor.moveToNext();
                entity.add(entityMapper.parseRow(cursor));
            }
            return entity;
        } finally {
            cursor.close();
        }
    }

    public SQLiteCursorList<TEntity> asCursorList() {
        Cursor cursor = connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
        // TODO Handle exceptions
        entityMapper.initialize(cursor);
        return new SQLiteCursorList<TEntity>(cursor, new Func1<Cursor, TEntity>() {
            @Override
            public TEntity call(Cursor cursor) {
                return entityMapper.parseRow(cursor);
            }
        });
    }

    public Observable<TEntity> asObservable() {
        return Observable.create(new Observable.OnSubscribe<TEntity>() {
            public void call(Subscriber<? super TEntity> subscriber) {
                Cursor cursor = null;
                try {
                    cursor = connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
                    entityMapper.initialize(cursor);

                    while (cursor.moveToNext()) {
                        if (subscriber.isUnsubscribed()) return;
                        subscriber.onNext(entityMapper.parseRow(cursor));
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

    public Observable<SQLiteCursorList<TEntity>> asObservableList() {
        return Observable.create(new Observable.OnSubscribe<SQLiteCursorList<TEntity>>() {
            public void call(Subscriber<? super SQLiteCursorList<TEntity>> subscriber) {
                Cursor cursor = null;
                try {
                    cursor = connection.rawQuery(queryBuilder.toQuery(), queryBuilder.toParams());
                    entityMapper.initialize(cursor);
                    if (!subscriber.isUnsubscribed()) {
                        subscriber.onNext(new SQLiteCursorList<TEntity>(cursor, new Func1<Cursor, TEntity>() {
                            @Override
                            public TEntity call(Cursor cursor) {
                                return entityMapper.parseRow(cursor);
                            }
                        }));
                    }
                    if (!subscriber.isUnsubscribed()) subscriber.onCompleted();
                } catch (Exception exception) {
                    if (!subscriber.isUnsubscribed()) subscriber.onError(exception);
//                } finally {
//                    if (cursor != null) cursor.close();
                }
            }
        });
    }

    @Override
    public <TResult> TResult retrieve(Func1<Query<TEntity>, TResult> retriever) {
        return retriever.call(this);
    }
}
