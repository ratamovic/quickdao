package com.codexperiments.quickdao.sqlite;

import android.database.Cursor;
import com.codexperiments.quickdao.Query;
import rx.Observable;
import rx.functions.Func1;

import java.util.List;

public class SQLiteRetriever {
    public static <TEntity> Func1<Query<TEntity>, Cursor> asCursor(Class<TEntity> entityClass) {
        return new Func1<Query<TEntity>, Cursor>() {
            @Override
            public Cursor call(Query<TEntity> query) {
                SQLiteQuery<TEntity, ?> sqliteQuery = (SQLiteQuery<TEntity, ?>) query;
                return sqliteQuery.asCursor();
            }
        };
    }

    public static <TEntity> Func1<Query<TEntity>, List<TEntity>> asList(Class<TEntity> entityClass) {
        return new Func1<Query<TEntity>, List<TEntity>>() {
            @Override
            public List<TEntity> call(Query<TEntity> query) {
                SQLiteQuery<TEntity, ?> sqliteQuery = (SQLiteQuery<TEntity, ?>) query;
                return sqliteQuery.asList();
            }
        };
    }

    public static <TEntity> Func1<Query<TEntity>, SQLiteCursorList<TEntity>> asCursorList(Class<TEntity> entityClass) {
        return new Func1<Query<TEntity>, SQLiteCursorList<TEntity>>() {
            @Override
            public SQLiteCursorList<TEntity> call(Query<TEntity> query) {
                SQLiteQuery<TEntity, ?> sqliteQuery = (SQLiteQuery<TEntity, ?>) query;
                return sqliteQuery.asCursorList();
            }
        };
    }

    public static <TEntity> Func1<Query<TEntity>, Observable<TEntity>> asObservable(Class<TEntity> entityClass) {
        return new Func1<Query<TEntity>, Observable<TEntity>>() {
            @Override
            public Observable<TEntity> call(Query<TEntity> query) {
                SQLiteQuery<TEntity, ?> sqliteQuery = (SQLiteQuery<TEntity, ?>) query;
                return sqliteQuery.asObservable();
            }
        };
    }

    public static <TEntity> Func1<Query<TEntity>, Observable<SQLiteCursorList<TEntity>>> asObservableList(Class<TEntity> entityClass) {
        return new Func1<Query<TEntity>, Observable<SQLiteCursorList<TEntity>>>() {
            @Override
            public Observable<SQLiteCursorList<TEntity>> call(Query<TEntity> query) {
                SQLiteQuery<TEntity, ?> sqliteQuery = (SQLiteQuery<TEntity, ?>) query;
                return sqliteQuery.asObservableList();
            }
        };
    }
}
