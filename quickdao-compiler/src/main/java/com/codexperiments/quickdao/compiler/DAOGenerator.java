package com.codexperiments.quickdao.compiler;

import com.codexperiments.quickdao.EntityMapper;
import com.codexperiments.quickdao.annotation.Column;
import com.codexperiments.quickdao.annotation.Id;
import com.codexperiments.quickdao.annotation.Table;
import com.squareup.javapoet.*;
import org.reflections.Reflections;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static javax.lang.model.element.Modifier.*;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.MANDATORY_WARNING;
import static org.reflections.ReflectionUtils.getAllFields;
import static org.reflections.ReflectionUtils.withAnnotation;

@SupportedAnnotationTypes({"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions({"inputPackage", "outputPackage"})
public class DAOGenerator extends AbstractProcessor {
    private Messager messager;
    private String inputPackage;
    private String outputPackage;
    private boolean disabled;
    private boolean done = false;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        inputPackage = processingEnv.getOptions().get("inputPackage");
        outputPackage = processingEnv.getOptions().get("outputPackage");
        disabled = parseBoolean(processingEnv.getOptions().get("disableGeneration"));

        if (disabled) messager.printMessage(MANDATORY_WARNING, format("Not generating sources"));
        else {
            messager.printMessage(MANDATORY_WARNING, format("Input package : %s", inputPackage));
            messager.printMessage(MANDATORY_WARNING, format("Output package : %s", outputPackage));
        }
        if (inputPackage == null || inputPackage.isEmpty())
            throw new IllegalArgumentException("inputPackage option missing" + inputPackage + "=" + outputPackage);
        if (outputPackage == null || outputPackage.isEmpty())
            throw new IllegalArgumentException("outputPackage option missing" + inputPackage + "=" + outputPackage);
    }

//    private void findTablesInSources(List<TableInfo> tableInfos, RoundEnvironment roundEnv) {
//        Set<? extends Element> tableElementSet = roundEnv.getElementsAnnotatedWith(Table.class);
//        Set<? extends Element> idElementSet = roundEnv.getElementsAnnotatedWith(Id.class);
//        Set<? extends Element> columnElementSet = roundEnv.getElementsAnnotatedWith(Column.class);
//        for (Element rawTableElement : tableElementSet) {
//            TypeElement tableElement = (TypeElement) rawTableElement;
//            TableInfo tableInfo = new TableInfo(processingEnv, tableElement);
//            messager.printMessage(MANDATORY_WARNING, format("XXXXXXX %s", tableInfo.javaQualifiedName));
//
//            for (Element element : rawTableElement.getEnclosedElements()) {
//                if (columnElementSet.contains(element)) {
//                    ColumnInfo columnInfo = new ColumnInfo(element);
//                    tableInfo.addColumn(columnInfo);
//
//                    if (idElementSet.contains(element)) tableInfo.setId(columnInfo);
//                }
//            }
//            tableInfos.add(tableInfo);
//
//            // Check annotations.
//            if (tableInfo.id == null) throw new IllegalStateException(format("Id missing on table %s", tableInfo.javaClass));
//        }
//    }

    private void findTablesInDependencies(List<TableInfo> tableInfos) {
//        try {
//            Class.forName("com.codexperiments.newsroot.core.domain.entities.Tweet");
//        } catch (ClassNotFoundException e) {
//            throw new RuntimeException(e);
//        }
        Reflections reflections = new Reflections("com.codexperiments.newsroot");
        Set<Class<?>> tableSet = reflections.getTypesAnnotatedWith(Table.class);
        for (Class<?> table : tableSet) {
            TableInfo tableInfo = new TableInfo(processingEnv, table);
            Class<?> tableClass;
            try {
                tableClass = Class.forName(tableInfo.javaQualifiedName);
            } catch (ClassNotFoundException classNotFoundException) {
                throw new RuntimeException(classNotFoundException);
            }

            Set<Field> columnClassSet = getAllFields(tableClass, withAnnotation(Column.class));
            for (Field columnField : columnClassSet) {
                ColumnInfo columnInfo = new ColumnInfo(columnField);
                tableInfo.addColumn(columnInfo);

                if (columnField.getAnnotation(Id.class) != null) tableInfo.setId(columnInfo);
            }
            tableInfos.add(tableInfo);

            // Check annotations.
            if (tableInfo.id == null) throw new IllegalStateException(format("Id missing on table %s", tableInfo.javaClass));
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Check if processor has already been run.
        if (done || disabled) return false;

        List<TableInfo> tableInfos = new ArrayList<>();
        findTablesInDependencies(tableInfos);

        // Add links between tables
        for (TableInfo tableInfo : tableInfos) {
            messager.printMessage(MANDATORY_WARNING, format("Found table %s", tableInfo.javaClass));
            for (ColumnInfo columnInfo : tableInfo.columnInfos) {
                if (!columnInfo.isJavaPrimitive && !columnInfo.isBoxedPrimitive) {
                    for (TableInfo linkedTableInfo : tableInfos) {
                        if (columnInfo.javaClass.equals(linkedTableInfo.javaQualifiedName)) {
                            columnInfo.javaLink = linkedTableInfo;
                            break;
                        }
                    }
                    if (columnInfo.javaLink == null) {
                        throw new RuntimeException(format("Link from field %s.%s not found", tableInfo.javaQualifiedName, columnInfo.javaName));
                    }
                }
            }
        }

        for (final TableInfo tableInfo : tableInfos) {
            generateFile(daoClassFQJavaName(tableInfo), new ClassGenerator() {
                public JavaFile generate() throws Exception {
                    return generateTableClass(tableInfo);
                }
            });
            generateFile(handlerClassFQJavaName(tableInfo), new ClassGenerator() {
                public JavaFile generate() throws Exception {
                    return generateMapperClass(tableInfo);
                }
            });
//            generateFile(handlerClassFQJavaName(tableInfo), new ClassGenerator() {
//                public JavaFile generate() throws Exception {
//                    return generateMapperClass(tableInfo);
//                }
//            });
//            generateFile(listClassFQJavaName(tableInfo), new ClassGenerator() {
//                public void generate(JavaWriter writer) throws Exception {
//                    generateListClass(writer, tableInfo);
//                }
//            });
        }
        done = true;
        return false;
    }

    private void generateFile(String fqJavaName, ClassGenerator generator) {
        Writer writer = null;
        try {
            Filer filer = processingEnv.getFiler();
            JavaFileObject daoFileObject = filer.createSourceFile(fqJavaName);
            writer = daoFileObject.openWriter();
            generator.generate().writeTo(writer);
        } catch (Exception exception) {
            exception.printStackTrace();
            messager.printMessage(ERROR, format("Error generating %s", fqJavaName));
        } finally {
            try {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public interface ClassGenerator {
        public JavaFile generate() throws Exception;
    }


    private JavaFile generateTableClass(TableInfo tableInfo) throws IOException {
        TypeSpec.Builder daoBuilder = TypeSpec.classBuilder(daoClassJavaName(tableInfo))
                                              .addModifiers(Modifier.PUBLIC);
        daoBuilder.addField(
                FieldSpec.builder(String.class, tableInfo.sqlName, PUBLIC, STATIC, FINAL)
                         .initializer("$S", tableInfo.sqlName)
                         .build());

        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            daoBuilder.addField(
                    FieldSpec.builder(String.class, daoFieldJavaName(columnInfo), PUBLIC, STATIC, FINAL)
                             .initializer("$S", daoFieldJavaValue(columnInfo))
                             .build());
        }

        String columnsFieldValue = " { ";
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            if (i > 0) columnsFieldValue += " , ";
            columnsFieldValue += daoFieldJavaValue(tableInfo.columnInfos.get(i));
        }
        columnsFieldValue += " } ";
        FieldSpec.Builder colunnsField = FieldSpec
                .builder(ArrayTypeName.of(String.class), tableInfo.sqlName + "_COLUMNS", PUBLIC, STATIC, FINAL);
        colunnsField.initializer(columnsFieldValue);
        daoBuilder.addField(colunnsField.build());

        return JavaFile.builder(daoClassPackage(tableInfo), daoBuilder.build()).build();
    }

    private void generateConstructor(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
        ClassName SQLiteDatabaseClass = ClassName.get("android.database.sqlite", "SQLiteDatabase");

        daoBuilder.addField(FieldSpec.builder(SQLiteDatabaseClass, "connection", PRIVATE).build());

        daoBuilder.addMethod(
                MethodSpec.constructorBuilder()
                          .addModifiers(PUBLIC)
                          .addParameter(ParameterSpec.builder(SQLiteDatabaseClass, "connection").build())
                          .addStatement("this.connection = connection")
                          .build());
//        writer.emitEmptyLine();
//        writer.emitField("SQLiteDatabase", "connection", of(PRIVATE));
//        writer.emitEmptyLine();
//        writer.beginConstructor(of(PUBLIC), "SQLiteDatabase", "connection")
//              .emitStatement("this.connection = connection")
//              .endConstructor();
    }

    private void generateInsert(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
        StringBuilder insert = new StringBuilder("insert into ").append(tableInfo.sqlName);
        // Insert clause.
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);
            insert.append(i == 0 ? "(" : ", ").append(columnInfo.sqlName);
        }
        // Value clause.
        insert.append(") values ");
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            insert.append(i == 0 ? "(" : ", ").append("?");
        }
        insert.append(");");

//        writer.emitEmptyLine();
        ClassName ParameterClass = ClassName.get(tableInfo.javaPackage, tableInfo.javaClass);
        MethodSpec.Builder methodBuilder = MethodSpec
                .methodBuilder("create")
                .addModifiers(PUBLIC)
                .returns(TypeName.VOID)
                .addParameter(ParameterSpec.builder(ParameterClass, "object").build())
                .addStatement("final String insert = \"$S\"", insert.toString())
                .addStatement("SQLiteStatement statement = connection.compileStatement(insert)");
//        writer.beginMethod("void", "create", of(PUBLIC), tableInfo.javaQualifiedName, "object");
//        writer.emitStatement("final String insert = \"%s\"", insert.toString());
//        writer.emitEmptyLine();
//        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(insert)");
        // Effective values.
        int index = 1;
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);

            if (columnInfo.isJavaPrimitive) {
                methodBuilder.addStatement("statement.bindString($S, String.valueOf(object.$S))", index++, columnInfo.javaName);
//                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", index++, columnInfo.javaName);
            } else {
                methodBuilder
                        .addStatement("statement.bindString($S, String.valueOf(object.$S.$S))", index++, columnInfo.javaName, "WWW"/*columnInfo.javaLink.id.javaName*/);
//                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s.%s))", index++, columnInfo.javaName, columnInfo.javaLink.id.javaName);
            }
        }
        // TODO Try catch and check rowId
        methodBuilder.addStatement("object.$S = statement.executeInsert()", tableInfo.id.javaName);
//        writer.emitEmptyLine()
//              .emitStatement("object.%s = statement.executeInsert()", tableInfo.id.javaName);

//        writer.endMethod();
        daoBuilder.addMethod(methodBuilder.build());
    }

    private void generateUpdate(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
        StringBuilder update = new StringBuilder("update ").append(tableInfo.sqlName);
        // Update clause.
        update.append(" set");
        for (int i = 0, index = 1; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);
            // The id must not be updated.
            if (columnInfo == tableInfo.id) continue;

            update.append(index++ == 1 ? " " : ", ").append(columnInfo.sqlName).append(" = ?");
        }
        update.append(" where ").append(tableInfo.id.sqlName).append(" = ?;");

        ClassName ParameterClass = ClassName.get(tableInfo.javaPackage, tableInfo.javaClass);
        MethodSpec.Builder methodBuilder = MethodSpec
                .methodBuilder("update")
                .addModifiers(PUBLIC)
                .addParameter(ParameterSpec.builder(ParameterClass, "object").build())
                .addStatement("final String update = \"$S\"", update.toString())
                .addStatement("SQLiteStatement statement = connection.compileStatement(update)")
                .returns(TypeName.VOID);
//        writer.emitEmptyLine();
//        writer.beginMethod("void", "update", of(PUBLIC), tableInfo.javaQualifiedName, "object");
//        writer.emitStatement("final String update = \"%s\"", update.toString());
//        writer.emitEmptyLine();
//        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(update)");
        // Effective values.
        int index = 1;
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);
            // Ignore the id again.
            if (columnInfo == tableInfo.id) continue;

            if (columnInfo.isJavaPrimitive) {
                methodBuilder.addStatement("statement.bindString($S, String.valueOf(object.$S))", index++, columnInfo.javaName);
//                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", index++, columnInfo.javaName);
            } else {
                methodBuilder
                        .addStatement("statement.bindString($S, String.valueOf(object.$S.$S))", index++, columnInfo.javaName, "XXX"/*columnInfo.javaLink.id.javaName*/);
//                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s.%s))", index++, columnInfo.javaName, columnInfo.javaLink.id.javaName);
            }
        }
        methodBuilder.addStatement("statement.bindString($S, String.valueOf(object.$S))", index, tableInfo.id.javaName);
//        writer.emitStatement("statement.bindString($S, String.valueOf(object.$S))", index, tableInfo.id.javaName);
        // TODO Try catch and check updatedRows == 1
        methodBuilder.addStatement("int updatedRows = statement.executeUpdateDelete()");
        methodBuilder.addStatement("if (updatedRows != 1) throw new RuntimeException(\"Object has not been updated\")");
//        writer.emitEmptyLine()
//              .emitStatement("int updatedRows = statement.executeUpdateDelete()", tableInfo.id.javaName)
//              .emitStatement("if (updatedRows != 1) throw new RuntimeException(\"Object has not been updated\")");
//
//        writer.endMethod();
    }

    private void generateDelete(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
        StringBuilder delete = new StringBuilder("delete from ").append(tableInfo.sqlName);
        delete.append(" where ").append(tableInfo.id.sqlName).append(" = ?;");

        ClassName ParameterClass = ClassName.get(tableInfo.javaPackage, tableInfo.javaClass);
        MethodSpec.Builder methodBuilder = MethodSpec
                .methodBuilder("delete")
                .addModifiers(PUBLIC)
                .addParameter(ParameterSpec.builder(ParameterClass, "object").build())
                .addStatement("final String delete = \"$S\"", delete.toString())
                .addStatement("SQLiteStatement statement = connection.compileStatement(delete)")
                .addStatement("")
                .addStatement("statement.bindString($S, String.valueOf(object.$S))", 1, tableInfo.id.javaName)
                .addStatement("")
                .addStatement("int deletedRows = statement.executeUpdateDelete()")
                .addStatement("if (deletedRows != 1) throw new RuntimeException(\"Object has not been deleted\")")
                .returns(TypeName.VOID);


//        writer.emitEmptyLine();
//        writer.beginMethod("void", "delete", of(PUBLIC), tableInfo.javaQualifiedName, "object");
//        writer.emitStatement("final String delete = \"%s\"", delete.toString());
//        writer.emitEmptyLine();

//        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(delete)");
//        writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", 1, tableInfo.id.javaName);
        // TODO Try catch and check updatedRows == 1
//        writer.emitEmptyLine()
//              .emitStatement("int deletedRows = statement.executeUpdateDelete()", tableInfo.id.javaName)
//              .emitStatement("if (deletedRows != 1) throw new RuntimeException(\"Object has not been deleted\")");
//
//        writer.endMethod();
        daoBuilder.addMethod(methodBuilder.build());
    }

    private void generateUtils(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
        StringBuilder select = new StringBuilder("select from ").append(tableInfo.sqlName);
        select.append(" where ").append(tableInfo.id.sqlName).append(" = ?;");

        daoBuilder.addMethod(
                MethodSpec.methodBuilder("columns")
                          .addModifiers(PUBLIC, STATIC)
                          .returns(ClassName.get(String.class))
                          .addStatement("return \"$S\"", tableInfo.sqlName)
                          .build());
//        writer.emitEmptyLine();
//        writer.beginMethod(String.class.getName(), "name", of(PUBLIC, STATIC));
//        writer.emitStatement("return \"%s\"", tableInfo.sqlName);
//        writer.endMethod();
//
        daoBuilder.addMethod(
                MethodSpec.methodBuilder("columns")
                          .addModifiers(PUBLIC, STATIC)
                          .returns(ArrayTypeName.of(String.class))
                          .addStatement("return COLUMNS")
                          .build());
//        writer.emitEmptyLine();
//        writer.beginMethod(arrayOfType(String.class.getName()), "columns", of(PUBLIC, STATIC));
//        writer.emitStatement("return COLUMNS");
//        writer.endMethod();

//        writer.emitEmptyLine();
//        writer.beginType("Table", "class", of(PUBLIC, STATIC), null, TableRef.class.getName());
//        writer.emitEmptyLine();
//        writer.beginMethod(String.class.getName(), "name", of(PUBLIC));
//        writer.emitStatement("return \"%s\"", tableInfo.sqlName);
//        writer.endMethod();

        daoBuilder.addMethod(
                MethodSpec.methodBuilder("columns")
                          .addModifiers(PUBLIC)
                          .returns(ArrayTypeName.of(String.class))
                          .addStatement("return $S.COLUMNS", daoClassJavaName(tableInfo))
                          .build());
//        writer.emitEmptyLine();
//        writer.beginMethod(arrayOfType(String.class.getName()), "columns", of(PUBLIC));
//        writer.emitStatement("return %s.COLUMNS", daoClassJavaName(tableInfo));
//        writer.endMethod();
//        writer.endType();
//
//        writer.emitEmptyLine();
        daoBuilder.addField(
                FieldSpec.builder(ClassName.get(tableInfo.javaQualifiedName, "Table"), tableInfo.sqlName, PUBLIC, STATIC)
                         .initializer("new Table()")
                         .build());
//        writer.emitField("Table", tableInfo.sqlName, of(PUBLIC, STATIC), "new Table()");
    }

    private String daoClassJavaName(TableInfo tableInfo) {
        return format("%sTable", tableInfo.javaClass);
    }

    private String daoClassFQJavaName(TableInfo tableInfo) {
        return daoClassPackage(tableInfo) + "." + daoClassJavaName(tableInfo);
    }

    private String daoClassPackage(TableInfo tableInfo) {
        return tableInfo.javaPackage.replace(inputPackage, outputPackage);
    }

    private String daoFieldJavaName(ColumnInfo columnInfo) {
        return columnInfo.sqlName.toUpperCase();
    }

    private String daoFieldJavaValue(ColumnInfo columnInfo) {
        return columnInfo.sqlName.toUpperCase();
    }

    private String arrayOfType(String type) {
        return type + "[]";
    }


    private JavaFile generateMapperClass(TableInfo tableInfo) throws IOException {
        ClassName dtoClass = ClassName.get(tableInfo.javaPackage, tableInfo.javaClass);
        ParameterizedTypeName dtoClassClass = ParameterizedTypeName.get(ClassName.get(Class.class), dtoClass);

        TypeSpec.Builder mapperBuilder =
                TypeSpec.classBuilder(handlerClassJavaName(tableInfo))
                        .addSuperinterface(ParameterizedTypeName.get(ClassName.get(EntityMapper.class), dtoClass));

        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);
            mapperBuilder.addField(
                    FieldSpec.builder(int.class, columnInfo.javaName + "Index").build());
        }


        mapperBuilder.addMethod(
                MethodSpec.methodBuilder("ofType")
                          .addModifiers(PUBLIC)
                          .returns(dtoClassClass)
                          .addStatement("return " + ClassName.get(tableInfo.javaPackage, tableInfo.javaClass).simpleName() + ".class")
                          .build());

        MethodSpec.Builder initializeMethod =
                MethodSpec.methodBuilder("initialize")
                          .addModifiers(PUBLIC)
                          .addParameter(ClassName.get("android.database", "Cursor"), "cursor");
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            initializeMethod.addStatement(columnInfo.javaName + "Index = cursor.getColumnIndex($S)", columnInfo.sqlName);
        }
        mapperBuilder.addMethod(initializeMethod.build());

        MethodSpec.Builder parseMethod =
                MethodSpec.methodBuilder("parseRow")
                          .addModifiers(PUBLIC)
                          .returns(dtoClass)
                          .addParameter(ClassName.get("android.database", "Cursor"), "cursor");
        String varName = tableInfo.javaClass.toLowerCase();
        parseMethod.addStatement("$L $L = new $L()", tableInfo.javaClass, varName, tableInfo.javaClass);
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            if (columnInfo.isJavaPrimitive) {
                parseMethod.addStatement("if ($L != -1) $L.$L(cursor.$L)",
                                         columnInfo.javaName + "Index", varName, handlerSetterJavaName(columnInfo), parseMethod(columnInfo));
            }
        }
        parseMethod.addStatement("return $L", varName);
        mapperBuilder.addMethod(parseMethod.build());


//        writer.emitPackage(handlerClassPackage(tableInfo))
//              .emitImports("android.database.*")
//              .emitImports(tableInfo.javaQualifiedName)
//              .emitImports(handlerGenericClassJavaFQJavaName())
//              .emitEmptyLine();
//
//        writer.beginType(handlerClassJavaName(tableInfo), "class", of(PUBLIC), null, handlerGenericClassJavaName(tableInfo));
//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitField("int", handlerFieldJavaName(columnInfo), of(PROTECTED));
//            }
//        }
//        writer.emitEmptyLine()
//              .beginMethod(classJavaName(tableInfo), "ofType", of(PUBLIC))
//              .emitStatement("return %s.class", tableInfo.javaClass)
//              .endMethod();
//
//        writer.emitEmptyLine()
//              .beginMethod("void", "initialize", of(PUBLIC), "Cursor", "cursor");
//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitStatement("%s = cursor.getColumnIndex(%s.%s)",
//                                     handlerFieldJavaName(columnInfo), daoClassJavaName(tableInfo), columnInfo.sqlName);
//            }
//        }
//        writer.endMethod();
//
//        writer.emitEmptyLine()
//              .beginMethod(tableInfo.javaClass, "parseRow", of(PUBLIC), "Cursor", "cursor")
//              .emitStatement("%1$s object = new %1$s()", tableInfo.javaClass);
//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitStatement("if (%s != -1) object.%s = cursor.%s",
//                                     handlerFieldJavaName(columnInfo), columnInfo.javaName, parseMethod(columnInfo));
//            }
//        }
//        writer.emitStatement("return object")
//              .endMethod();
//
//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitEmptyLine()
//                      .beginMethod(columnInfo.javaClass, listMethodJavaName(columnInfo), of(PUBLIC), "Cursor", "cursor")
//                      .emitStatement("return cursor.%s", parseMethod(columnInfo))
//                      .endMethod();
//            }
//        }
//
////        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
////            if (columnInfo.isJavaPrimitive) {
////                writer.emitEmptyLine()
////                      .beginMethod(columnInfo.javaClass, handlerGetterJavaName(columnInfo), of(PUBLIC), "Cursor", "cursor")
////                      .emitStatement("return cursor.%s", parseMethod(columnInfo))
////                      .endMethod();
////            }
////        }
//
//        writer.endType();
        return JavaFile.builder(handlerClassPackage(tableInfo), mapperBuilder.build()).build();
    }

    private String handlerClassJavaName(TableInfo tableInfo) {
        return format("%sMapper", tableInfo.javaClass);
    }

    private String handlerClassFQJavaName(TableInfo tableInfo) {
        return handlerClassPackage(tableInfo) + "." + handlerClassJavaName(tableInfo);
    }

    private String handlerClassPackage(TableInfo tableInfo) {
        if (tableInfo.javaPackage.startsWith("com.codexperiments.newsroot.core.domain.entity"))
            return tableInfo.javaPackage.replace("com.codexperiments.newsroot.core.domain.entity", "com.codexperiments.newsroot.data.repository");
        else if (tableInfo.javaPackage.startsWith("com.codexperiments.newsroot.core.application.viewmodel"))
            return tableInfo.javaPackage.replace("com.codexperiments.newsroot.core.application.viewmodel", "com.codexperiments.newsroot.data.provider");
        else
            return tableInfo.javaPackage.replace(inputPackage, outputPackage);
    }

    private String handlerGenericClassJavaName(TableInfo tableInfo) {
        return EntityMapper.class.getSimpleName() + "<" + tableInfo.javaClass + ">";
    }

    private String handlerGenericClassJavaFQJavaName() {
        return EntityMapper.class.getName();
    }

    private String handlerFieldJavaName(ColumnInfo columnInfo) {
        return format("%sIndex", columnInfo.javaName);
    }

    private String parseMethod(ColumnInfo columnInfo) {
        switch (columnInfo.javaClass) {
            case "boolean":
                return format("getInt(%s) > 0", handlerFieldJavaName(columnInfo));
            case "double":
                return format("getDouble(%s)", handlerFieldJavaName(columnInfo));
            case "int":
                return format("getInt(%s)", handlerFieldJavaName(columnInfo));
            case "long":
                return format("getLong(%s)", handlerFieldJavaName(columnInfo));
            case "short":
                return format("getShort(%s)", handlerFieldJavaName(columnInfo));
            case "java.lang.String":
                return format("getString(%s)", handlerFieldJavaName(columnInfo));
            default:
                throw new RuntimeException(format("Type %s not handled yet", columnInfo.javaClass));
        }
    }

    private static String handlerGetterJavaName(ColumnInfo columnInfo) {
        return format("get%s", with1stLetterUpperCase(columnInfo.javaName));
    }

    private static String handlerSetterJavaName(ColumnInfo columnInfo) {
        return format("set%s", with1stLetterUpperCase(columnInfo.javaName));
    }

    private static String classJavaName(TableInfo tableInfo) {
        return "Class<" + tableInfo.javaClass + ">";
    }

    private static String with1stLetterUpperCase(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1, value.length());
    }


    //    private void generateListClass(TypeSpec.Builder daoBuilder, TableInfo tableInfo) throws IOException {
//        writer.emitPackage(listClassPackage(tableInfo))
//              .emitImports("android.database.*")
//              .emitImports(tableInfo.javaQualifiedName)
//              .emitEmptyLine();
//
//        writer.beginType(listClassJavaName(tableInfo), "class", of(PUBLIC));
//
//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitEmptyLine()
//                      .beginMethod(tableInfo.javaClass, listMethodJavaName(columnInfo), of(PUBLIC), "Cursor", "cursor")
//                      .emitStatement("return cursor.%s", listFieldJavaName(columnInfo), columnInfo.javaName, parseMethod(columnInfo))
//                      .endMethod();
//            }
//        }
//
//        writer.endType();
//    }
//
//    private String listClassJavaName(TableInfo tableInfo) {
//        return format("%sParser", tableInfo.javaClass);
//    }
//
//    private String listClassFQJavaName(TableInfo tableInfo) {
//        return listClassPackage(tableInfo) + "." + listClassJavaName(tableInfo);
//    }
//
//    private String listClassPackage(TableInfo tableInfo) {
//        return tableInfo.javaPackage.replace(inputPackage, outputPackage);
//    }
//
//    private String listFieldJavaName(ColumnInfo columnInfo) {
//        return format("%sIndex", columnInfo.javaName);
//    }
//
    private String listMethodJavaName(ColumnInfo columnInfo) {
        return format("get%s", columnInfo.javaName.substring(0, 1).toUpperCase() + columnInfo.javaName.substring(1, columnInfo.javaName.length()));
    }
}
