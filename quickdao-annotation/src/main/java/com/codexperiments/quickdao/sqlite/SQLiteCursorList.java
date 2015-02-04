package com.codexperiments.quickdao.sqlite;

import android.database.Cursor;
import rx.functions.Func1;

import java.io.Closeable;
import java.util.*;

public class SQLiteCursorList<TElement> implements List<TElement>, Closeable {
    private static final SQLiteCursorList EMPTY = new SQLiteCursorList();

    private final Func1<Cursor, TElement> getElement;
    private final Cursor cursor;
    private final int size;

    public static final <TElement> SQLiteCursorList<TElement> empty() {
        return (SQLiteCursorList<TElement>) EMPTY;
    }

    protected SQLiteCursorList() {
        super();
        this.getElement = null;
        this.size = 0;
        this.cursor = null;
    }

    protected SQLiteCursorList(Cursor cursor, Func1<Cursor, TElement> getElement) {
        super();
        this.getElement = getElement;
        this.cursor = cursor;
        this.size = cursor.getCount();
    }

    public void recycle(TElement element) {

    }

    public void close() {
        if (cursor != null) cursor.close();
    }

    @Override
    public boolean add(TElement element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, TElement element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends TElement> pCollection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int pIndex, Collection<? extends TElement> pCollection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object object) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> pCollection) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public TElement get(int index) {
        if ((index < 0) || (index > size)) throw new IndexOutOfBoundsException();
        cursor.moveToPosition(index);
        return getElement.call(cursor);
    }

    @Override
    public int indexOf(Object object) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Iterator<TElement> iterator() {
        return new IteratorImpl(0);
    }

    @Override
    public int lastIndexOf(Object object) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<TElement> listIterator() {
        return new IteratorImpl(0);
    }

    @Override
    public ListIterator<TElement> listIterator(int index) {
        return new IteratorImpl(index);
    }

    @Override
    public TElement remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TElement set(int index, TElement object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public List<TElement> subList(int start, int end) {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        // TODO
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] pArray) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private class IteratorImpl implements ListIterator<TElement> {
        private int location;

        public IteratorImpl(int index) {
            location = index;
            cursor.moveToPosition(index);
        }

        @Override
        public void add(TElement element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNext() {
            return (location < size);
        }

        @Override
        public boolean hasPrevious() {
            return location == 0;
        }

        @Override
        public TElement next() {
            if (hasNext()) return get(location++);
            else throw new NoSuchElementException();
        }

        @Override
        public int nextIndex() {
            return location + 1;
        }

        @Override
        public TElement previous() {
            if (location > 0) return get(location--);
            else throw new NoSuchElementException();
        }

        @Override
        public int previousIndex() {
            return location - 1;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(TElement element) {
            throw new UnsupportedOperationException();
        }
    }
}
