package com.codexperiments.quickdao;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import android.database.Cursor;
import rx.functions.Func1;

public class CursorList<TElement> implements List<TElement>, Closeable {
    private Cursor cursor;
    private int size;
    private Func1<Cursor, TElement> getElement;

    public CursorList(Cursor cursor, Func1<Cursor, TElement> getElement) {
        super();
        this.cursor = cursor;
        this.size = cursor.getCount();
        this.getElement = getElement;
        if (size < 0) size = 0;
    }

    @Override
    public void close() throws IOException {
        cursor.close();
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
        private int mLocation;

        public IteratorImpl(int index) {
            mLocation = index;
        }

        @Override
        public void add(TElement element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasNext() {
            return cursor.isAfterLast();
        }

        @Override
        public boolean hasPrevious() {
            return mLocation == 0;
        }

        @Override
        public TElement next() {
            if (mLocation < size) return get(size++);
            else throw new NoSuchElementException();
        }

        @Override
        public int nextIndex() {
            return mLocation + 1;
        }

        @Override
        public TElement previous() {
            if (mLocation > 0) return get(size--);
            else throw new NoSuchElementException();
        }

        @Override
        public int previousIndex() {
            return mLocation - 1;
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
