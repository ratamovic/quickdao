package com.codexperiments.quickdao;

import android.database.Cursor;
import rx.Observable;

import java.util.List;

public class BaseQuery<TEntity, TMapper extends ObjectMapper<TEntity>> {
    private final Database database;
    protected final TMapper mapper;
    protected final Query query;

    public BaseQuery(Database database, TMapper mapper, Query query) {
        this.database = database;
        this.mapper = mapper;
        this.query = query;
    }

    public Cursor asCursor() {
        return database.runQuery(query);
    }

    public TEntity[] asArray() {
        return database.queryArray(query, mapper);
    }

    public List<TEntity> asList() {
        return database.queryList(query, mapper);
    }

    public Observable<TEntity> asObservable() {
        return database.queryObservable(query, mapper);
    }
}
