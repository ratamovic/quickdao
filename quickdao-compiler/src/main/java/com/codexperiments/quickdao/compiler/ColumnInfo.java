package com.codexperiments.quickdao.compiler;

import com.codexperiments.quickdao.annotation.Column;

import javax.lang.model.element.Element;

public class ColumnInfo {
    public Element element;
    public String sqlName;
    public String javaName;
    public String javaClass;
    public TableInfo javaLink;
    public boolean isJavaPrimitive;

    public ColumnInfo(Element element) {
        this.element = element;
        this.javaName = element.getSimpleName().toString();
        this.javaClass = element.asType().toString();
        this.isJavaPrimitive = element.asType().getKind().isPrimitive() || javaClass.equals("java.lang.String");
        this.sqlName = element.getAnnotation(Column.class).value();
    }
}
