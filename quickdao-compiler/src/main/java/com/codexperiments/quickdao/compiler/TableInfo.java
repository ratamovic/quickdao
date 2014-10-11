package com.codexperiments.quickdao.compiler;

import com.codexperiments.quickdao.annotation.Table;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public class TableInfo {
    public String sqlName;
    public String javaClass;
    public String javaQualifiedName;
    public String javaPackage;
    public ColumnInfo id;
    public List<ColumnInfo> columnInfos = new ArrayList<>();

    public TableInfo(ProcessingEnvironment processingEnv, TypeElement element) {
        this.javaClass = element.getSimpleName().toString();
        this.javaQualifiedName = element.getQualifiedName().toString();
        this.javaPackage = processingEnv.getElementUtils().getPackageOf(element).toString();
        this.sqlName = element.getAnnotation(Table.class).value();
    }

    public TableInfo(ProcessingEnvironment processingEnv, Class<?> tableClass) {
        this.javaClass = tableClass.getSimpleName();
        this.javaQualifiedName = tableClass.getName();
        this.javaPackage = tableClass.getPackage().getName();
        this.sqlName = tableClass.getAnnotation(Table.class).value();
    }

    public void setId(ColumnInfo columnInfo) {
        id = columnInfo;
    }

    public void addColumn(ColumnInfo columnInfo) {
        columnInfos.add(columnInfo);
    }
}