package com.codexperiments.quickdao;

import android.database.Cursor;

public interface RowHandler {
    public abstract void handleRow(Cursor cursor);
}
