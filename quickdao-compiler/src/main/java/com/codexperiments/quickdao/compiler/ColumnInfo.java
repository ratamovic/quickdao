package com.codexperiments.quickdao.compiler;

import com.codexperiments.quickdao.annotation.Column;

import javax.lang.model.element.Element;
import java.lang.reflect.Field;

public class ColumnInfo {
    public String sqlName;
    public String javaName;
    public String javaClass;
    public TableInfo javaLink;
    public boolean isJavaPrimitive;
    public boolean isBoxedPrimitive;

    public ColumnInfo(Element element) {
        this.javaName = element.getSimpleName().toString();
        this.javaClass = element.asType().toString();
        this.isJavaPrimitive = element.asType().getKind().isPrimitive() || javaClass.equals("java.lang.String");
        this.isBoxedPrimitive = element.asType().getKind().toString().equals("java.lang.Long");
        this.sqlName = element.getAnnotation(Column.class).value();
    }

    public ColumnInfo(Field field) {
        this.javaName = field.getName();
        this.javaClass = field.getType().getName();
        this.isJavaPrimitive = field.getType().isPrimitive() || (field.getType() == String.class);
        this.isBoxedPrimitive = field.getType().isAssignableFrom(Long.class);
        this.sqlName = field.getAnnotation(Column.class).value();
    }
}
