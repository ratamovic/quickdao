package com.codexperiments.quickdao;

import android.database.Cursor;

public interface ObjectMapper<TType> {
    Class<TType> ofType();

    void initialize(Cursor pCursor);

    TType parseRow(Cursor pCursor);
}
