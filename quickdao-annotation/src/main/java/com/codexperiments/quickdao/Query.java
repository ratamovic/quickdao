package com.codexperiments.quickdao;

import rx.functions.Func1;

public interface Query<TEntity> {
    <TResult> TResult retrieve(Func1<Query<TEntity>, TResult> retriever);
}
