package com.codexperiments.quickdao.sqlite;

import com.codexperiments.quickdao.TableRef;

import java.util.ArrayList;
import java.util.List;

public class SQLiteQueryBuilder {
    private StringBuilder select;
    private StringBuilder from;
    private StringBuilder where;
    private StringBuilder orderBy;

    private boolean ascending;
    private boolean distinct;
    private int limit;

    private List<String> params;

    public SQLiteQueryBuilder() {
        super();

        select = new StringBuilder();
        from = new StringBuilder();
        where = new StringBuilder();
        orderBy = new StringBuilder();

        ascending = true;
        distinct = false;
        limit = 0;

        params = new ArrayList<>();
    }

    private void selectAll(String table, String... columns) {
        for (String column : columns) {
            if (select.length() == 0) select.append("select ").append(distinct ? "distinct " : "");
            else select.append(", ");

            select.append(column);
        }
    }

    public SQLiteQueryBuilder from(String table, String... columns) {
        // TODO columns.length == 0
        selectAll(table, columns);

        if (from.length() == 0) from.append(" from ");
        else from.append(", ");
        from.append(table);
        return this;
    }

    public SQLiteQueryBuilder from(TableRef table) {
        // TODO columns.length == 0
        selectAll(table.name(), table.columns());

        if (from.length() == 0) from.append(" from ");
        else from.append(", ");
        from.append(table.name());
        return this;
    }

    public SQLiteQueryBuilder innerJoin(String table, String... columns) {
        // TODO columns.length == 0
        selectAll(table, columns);
        from.append(" inner join ").append(table);
        return this;
    }

    public SQLiteQueryBuilder innerJoin(TableRef table) {
        // TODO columns.length == 0
        selectAll(table.name(), table.columns());
        from.append(" inner join ").append(table.name());
        return this;
    }

    public SQLiteQueryBuilder on(String leftField, String rightField) {
        from.append(" on ").append(leftField).append(" = ").append(rightField);
        return this;
    }

    public SQLiteQueryBuilder limit(int count) {
        limit = count;
        return this;
    }

    private void startWhere() {
        if (where.length() == 0) where.append(" where ");
        else where.append(" and ");
    }

    public SQLiteQueryBuilder whereEquals(String column, long value) {
        return whereCondition(column, " = ", Long.toString(value));
    }

    public SQLiteQueryBuilder whereGreater(String column, long value) {
        return whereCondition(column, " > ", Long.toString(value));
    }

    public SQLiteQueryBuilder whereLower(String column, long value) {
        return whereCondition(column, " < ", Long.toString(value));
    }

    private SQLiteQueryBuilder whereCondition(String column, String pOperator, String value) {
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

    public SQLiteQueryBuilder orderBy(String column) {
        startOrderByClause();
        orderBy.append(column);
        orderBy.append(" ");
        return this;
    }

    public SQLiteQueryBuilder ascending() {
        this.ascending = true;
        return this;
    }

    public SQLiteQueryBuilder descending() {
        this.ascending = false;
        return this;
    }

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