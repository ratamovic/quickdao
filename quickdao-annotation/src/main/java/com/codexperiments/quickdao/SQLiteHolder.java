package com.codexperiments.quickdao;

import android.database.Cursor;
import rx.Observable;

import java.util.List;

public class SQLiteHolder<TEntity, TMapper extends ObjectMapper<TEntity>> {
    public Database database;
    public TMapper mapper;
    public Query query;

    public SQLiteHolder(Database database, TMapper mapper, Query query) {
        this.database = database;
        this.mapper = mapper;
        this.query = query;
    }

    public Cursor asCursor() {
        return database.asCursor(query);
    }

    public TEntity[] asArray() {
        return database.asArray(query, mapper);
    }

    public List<TEntity> asList() {
        return database.asList(query, mapper);
    }

    public List<TEntity> asCursorList() {
        return database.asCursorList(query, mapper);
    }

    public Observable<TEntity> asObservable() {
        return database.asObservable(query, mapper);
    }
}
