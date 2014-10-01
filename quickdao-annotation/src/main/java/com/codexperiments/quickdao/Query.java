package com.codexperiments.quickdao;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class Query {
//    private SQLiteDatabase connection;

    private StringBuilder select;
    private StringBuilder from;
    private StringBuilder where;
    private StringBuilder orderBy;

    private boolean ascending;
    private boolean distinct;
    private int limit;

    private List<String> params;
//    private ResultHandler resultHandler;

    public Query() {
        super();

//        this.connection = connection;

        select = new StringBuilder();
        from = new StringBuilder();
        where = new StringBuilder();
        orderBy = new StringBuilder();

        ascending = true;
        distinct = false;
        limit = 0;

        params = new ArrayList<>();
//        resultHandler = new ResultHandler(tables);
    }

    private void selectAll(String table, String... columns) {
        for (String column : columns) {
            if (select.length() == 0) select.append("select ").append(distinct ? "distinct " : "");
            else select.append(", ");

            select.append(column);
        }
    }

    public Query from(String table, String... columns) {
        // TODO columns.length == 0
        selectAll(table, columns);

        if (from.length() == 0) from.append(" from ");
        else from.append(", ");
        from.append(table);
        return this;
    }

    public Query from(TableRef table) {
        // TODO columns.length == 0
        selectAll(table.name(), table.columns());

        if (from.length() == 0) from.append(" from ");
        else from.append(", ");
        from.append(table.name());
        return this;
    }

    public Query innerJoin(String table, String... columns) {
        // TODO columns.length == 0
        selectAll(table, columns);
        from.append(" inner join ").append(table);
        return this;
    }

    public Query innerJoin(TableRef table) {
        // TODO columns.length == 0
        selectAll(table.name(), table.columns());
        from.append(" inner join ").append(table.name());
        return this;
    }

    public Query on(String leftField, String rightField) {
        from.append(" on ").append(leftField).append(" = ").append(rightField);
        return this;
    }

    public Query limit(int count) {
        limit = count;
        return this;
    }

    private void startWhere() {
        if (where.length() == 0) where.append(" where ");
        else where.append(" and ");
    }

    public Query whereEquals(String column, long value) {
        return whereCondition(column, " = ", Long.toString(value));
    }

    public Query whereGreater(String column, long value) {
        return whereCondition(column, " > ", Long.toString(value));
    }

    public Query whereLower(String column, long value) {
        return whereCondition(column, " < ", Long.toString(value));
    }

    private Query whereCondition(String column, String pOperator, String value) {
        startWhere();
        where.append(column).append(pOperator).append("?");
        params.add(value);
        return this;
    }

    private void startOrderByClause() {
        if (orderBy.length() == 0) {
            orderBy.append(" order by ");
        } else {
            orderBy.append(", ");
        }
    }

    public Query orderBy(String column) {
        startOrderByClause();
        orderBy.append(column);
        orderBy.append(" ");
        return this;
    }

    public Query ascending() {
        this.ascending = true;
        return this;
    }

    public Query descending() {
        this.ascending = false;
        return this;
    }

//    public <TEntity> List<TEntity> asList(ObjectHandler<TEntity> pObjectHandler) {
//        Cursor cursor = connection.rawQuery(toQuery(), toParams());
//        try {
//            int resultSize = cursor.getCount();
//            List<TEntity> lEntity = new ArrayList<>(resultSize);
//            for (int i = resultSize; i < resultSize; ++i) {
//                cursor.moveToNext();
//                lEntity.set(i, pObjectHandler.parseRow(cursor));
//            }
//            return lEntity;
//        } finally {
//            cursor.close();
//        }
//    }

    public String toQuery() {
        StringBuilder query = new StringBuilder().append(select).append(from).append(where);
        if (orderBy.length() > 0) query.append(orderBy).append((ascending)  ? " asc" : " desc");
        if (limit > 0) query.append(" limit ").append(Integer.toString(limit));

        return query.toString();
    }

    public String[] toParams() {
        return params.toArray(new String[params.size()]);
    }
}