package com.codexperiments.quickdao.compiler;

import com.codexperiments.quickdao.ObjectMapper;
import com.codexperiments.quickdao.Query;
import com.codexperiments.quickdao.TableRef;
import com.codexperiments.quickdao.annotation.Column;
import com.codexperiments.quickdao.annotation.Id;
import com.codexperiments.quickdao.annotation.Table;
import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;
import static java.util.EnumSet.of;
import static javax.lang.model.element.Modifier.*;
import static javax.tools.Diagnostic.Kind.ERROR;

@SupportedAnnotationTypes({"com.codexperiments.quickdao.annotation.Table"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class DAOGenerator extends AbstractProcessor {
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
    }

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> tableElementSet = roundEnv.getElementsAnnotatedWith(Table.class);
        Set<? extends Element> idElementSet = roundEnv.getElementsAnnotatedWith(Id.class);
        Set<? extends Element> columnElementSet = roundEnv.getElementsAnnotatedWith(Column.class);
        List<TableInfo> tableInfos = new ArrayList<>();

        for (Element rawTableElement : tableElementSet) {
            TypeElement tableElement = (TypeElement) rawTableElement;
            TableInfo tableInfo = new TableInfo(processingEnv, tableElement);

            for (Element element : rawTableElement.getEnclosedElements()) {
                if (columnElementSet.contains(element)) {
                    ColumnInfo columnInfo = new ColumnInfo(element);
                    tableInfo.addColumn(columnInfo);

                    if (idElementSet.contains(element)) tableInfo.setId(columnInfo);
                }
            }
            tableInfos.add(tableInfo);

            // Check annotations.
            if (tableInfo.id == null) throw new IllegalStateException(format("Id missing on table %s", tableInfo.javaClass));
        }

        // Add links between tables
        for (TableInfo tableInfo : tableInfos) {
            for (ColumnInfo columnInfo : tableInfo.columnInfos) {
                if (!columnInfo.isJavaPrimitive) {
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
            generateFile(daoClassFQJavaName(tableInfo), tableInfo.element, new ClassGenerator() {
                public void generate(JavaWriter writer) throws Exception {
                    generateTableClass(writer, tableInfo);
                }
            });
            generateFile(handlerClassFQJavaName(tableInfo), tableInfo.element, new ClassGenerator() {
                public void generate(JavaWriter writer) throws Exception {
                    generateHandlerClass(writer, tableInfo);
                }
            });
        }
        return true;
    }

    private void generateFile(String fqJavaName, Element element, ClassGenerator generator) {
        Writer writer = null;
        try {
            Filer filer = processingEnv.getFiler();
            JavaFileObject daoFileObject = filer.createSourceFile(fqJavaName, element);
            writer = daoFileObject.openWriter();
            JavaWriter javaWriter = new JavaWriter(writer);
            javaWriter.setIndent("    ");
            generator.generate(javaWriter);
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
        public void generate(JavaWriter writer) throws Exception;
    }


    private void generateTableClass(JavaWriter writer, TableInfo tableInfo) throws IOException {
        writer.emitPackage(tableInfo.javaPackage)
              .emitImports(Query.class)
              .emitImports("android.database.sqlite.*")
              .emitEmptyLine();

        writer.beginType(daoClassJavaName(tableInfo), "class", of(PUBLIC));
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            generateTableField(writer, tableInfo, columnInfo);
        }
        generateTableColumnSet(writer, tableInfo);
        generateConstructor(writer, tableInfo);
        generateInsert(writer, tableInfo);
        generateUpdate(writer, tableInfo);
        generateDelete(writer, tableInfo);
        generateUtils(writer, tableInfo);

        writer.endType();
    }

    private void generateTableField(JavaWriter writer, TableInfo tableInfo, ColumnInfo columnInfo) throws IOException {
        writer.emitField(String.class.getName(),
                         daoFieldJavaName(columnInfo),
                         of(PUBLIC, STATIC, FINAL),
                         daoFieldJavaValue(columnInfo));
    }

    private void generateTableColumnSet(JavaWriter writer, TableInfo tableInfo) throws IOException {
        writer.emitEmptyLine();
        String columns = "";
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            if (!columns.isEmpty()) columns += ", ";
            columns += daoFieldJavaName(columnInfo);
        }

        writer.emitField(arrayOfType(String.class.getName()),
                         "COLUMNS",
                         of(PUBLIC, STATIC, FINAL),
                         "{ " + columns + " }");
    }

    private void generateConstructor(JavaWriter writer, TableInfo tableInfo) throws IOException {
        writer.emitEmptyLine();
        writer.emitField("SQLiteDatabase", "connection", of(PRIVATE));
        writer.emitEmptyLine();
        writer.beginConstructor(of(PUBLIC), "SQLiteDatabase", "connection")
              .emitStatement("this.connection = connection")
              .endConstructor();
    }

    private void generateInsert(JavaWriter writer, TableInfo tableInfo) throws IOException {
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

        writer.emitEmptyLine();
        writer.beginMethod("void", "create", of(PUBLIC), tableInfo.javaQualifiedName, "object");
        writer.emitStatement("final String insert = \"%s\"", insert.toString());
        writer.emitEmptyLine();
        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(insert)");
        // Effective values.
        int index = 1;
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);

            if (columnInfo.isJavaPrimitive) {
                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", index++, columnInfo.javaName);
            } else {
                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s.%s))", index++, columnInfo.javaName, columnInfo.javaLink.id.javaName);
            }
        }
        // TODO Try catch and check rowId
        writer.emitEmptyLine()
              .emitStatement("object.%s = statement.executeInsert()", tableInfo.id.javaName);

        writer.endMethod();
    }

    private void generateUpdate(JavaWriter writer, TableInfo tableInfo) throws IOException {
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

        writer.emitEmptyLine();
        writer.beginMethod("void", "update", of(PUBLIC), tableInfo.javaQualifiedName, "object");
        writer.emitStatement("final String update = \"%s\"", update.toString());
        writer.emitEmptyLine();
        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(update)");
        // Effective values.
        int index = 1;
        for (int i = 0; i < tableInfo.columnInfos.size(); ++i) {
            ColumnInfo columnInfo = tableInfo.columnInfos.get(i);
            // Ignore the id again.
            if (columnInfo == tableInfo.id) continue;

            if (columnInfo.isJavaPrimitive) {
                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", index++, columnInfo.javaName);
            } else {
                writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s.%s))", index++, columnInfo.javaName, columnInfo.javaLink.id.javaName);
            }
        }
        writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", index, tableInfo.id.javaName);
        // TODO Try catch and check updatedRows == 1
        writer.emitEmptyLine()
              .emitStatement("int updatedRows = statement.executeUpdateDelete()", tableInfo.id.javaName)
              .emitStatement("if (updatedRows != 1) throw new RuntimeException(\"Object has not been updated\")");

        writer.endMethod();
    }

    private void generateDelete(JavaWriter writer, TableInfo tableInfo) throws IOException {
        StringBuilder delete = new StringBuilder("delete from ").append(tableInfo.sqlName);
        delete.append(" where ").append(tableInfo.id.sqlName).append(" = ?;");

        writer.emitEmptyLine();
        writer.beginMethod("void", "delete", of(PUBLIC), tableInfo.javaQualifiedName, "object");
        writer.emitStatement("final String delete = \"%s\"", delete.toString());
        writer.emitEmptyLine();

        writer.emitStatement("SQLiteStatement statement = connection.compileStatement(delete)");
        writer.emitStatement("statement.bindString(%s, String.valueOf(object.%s))", 1, tableInfo.id.javaName);
        // TODO Try catch and check updatedRows == 1
        writer.emitEmptyLine()
              .emitStatement("int deletedRows = statement.executeUpdateDelete()", tableInfo.id.javaName)
              .emitStatement("if (deletedRows != 1) throw new RuntimeException(\"Object has not been deleted\")");

        writer.endMethod();
    }

    private void generateUtils(JavaWriter writer, TableInfo tableInfo) throws IOException {
        StringBuilder select = new StringBuilder("select from ").append(tableInfo.sqlName);
        select.append(" where ").append(tableInfo.id.sqlName).append(" = ?;");

        writer.emitEmptyLine();
        writer.beginMethod(String.class.getName(), "name", of(PUBLIC, STATIC));
        writer.emitStatement("return \"%s\"", tableInfo.sqlName);
        writer.endMethod();

        writer.emitEmptyLine();
        writer.beginMethod(arrayOfType(String.class.getName()), "columns", of(PUBLIC, STATIC));
        writer.emitStatement("return COLUMNS");
        writer.endMethod();

        writer.emitEmptyLine();
        writer.beginType("Table", "class", of(PUBLIC, STATIC), null, TableRef.class.getName());
        writer.emitEmptyLine();
        writer.beginMethod(String.class.getName(), "name", of(PUBLIC));
        writer.emitStatement("return \"%s\"", tableInfo.sqlName);
        writer.endMethod();

        writer.emitEmptyLine();
        writer.beginMethod(arrayOfType(String.class.getName()), "columns", of(PUBLIC));
        writer.emitStatement("return %s.COLUMNS", daoClassJavaName(tableInfo));
        writer.endMethod();
        writer.endType();

        writer.emitEmptyLine();
        writer.emitField("Table", tableInfo.sqlName, of(PUBLIC, STATIC), "new Table()");
    }

    private static String daoClassJavaName(TableInfo tableInfo) {
        return format("%sTable", tableInfo.javaClass);
    }

    private static String daoClassFQJavaName(TableInfo tableInfo) {
        return tableInfo.javaPackage + "." + daoClassJavaName(tableInfo);
    }

    private static String daoFieldJavaName(ColumnInfo columnInfo) {
        return columnInfo.sqlName.toUpperCase();
    }

    private static String daoFieldJavaValue(ColumnInfo columnInfo) {
        return "\"" + columnInfo.sqlName.toUpperCase() + "\"";
    }

    private static String arrayOfType(String type) {
        return type + "[]";
    }


    private void generateHandlerClass(JavaWriter writer, TableInfo tableInfo) throws IOException {
        writer.emitPackage(tableInfo.javaPackage)
              .emitImports("android.database.*")
              .emitImports(handlerGenericClassJavaFQJavaName())
              .emitEmptyLine();

        writer.beginType(handlerClassJavaName(tableInfo), "class", of(PUBLIC), null, handlerGenericClassJavaName(tableInfo));
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            if (columnInfo.isJavaPrimitive) {
                writer.emitField("int", handlerFieldJavaName(columnInfo), of(PROTECTED));
            }
        }
        writer.emitEmptyLine()
              .beginMethod(classJavaName(tableInfo), "ofType", of(PUBLIC))
              .emitStatement("return %s.class", tableInfo.javaClass)
              .endMethod();

        writer.emitEmptyLine()
              .beginMethod("void", "initialize", of(PUBLIC), "Cursor", "cursor");
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            if (columnInfo.isJavaPrimitive) {
                writer.emitStatement("%s = cursor.getColumnIndex(%s.%s)",
                                     handlerFieldJavaName(columnInfo), daoClassJavaName(tableInfo), columnInfo.sqlName);
            }
        }
        writer.endMethod();

        writer.emitEmptyLine()
              .beginMethod(tableInfo.javaClass, "parseRow", of(PUBLIC), "Cursor", "cursor")
              .emitStatement("%1$s object = new %1$s()", tableInfo.javaClass);
        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
            if (columnInfo.isJavaPrimitive) {
                writer.emitStatement("object.%s = cursor.%s", columnInfo.javaName, handlerFieldParseMethod(columnInfo));
            }
        }
        writer.emitStatement("return object")
              .endMethod();

//        for (ColumnInfo columnInfo : tableInfo.columnInfos) {
//            if (columnInfo.isJavaPrimitive) {
//                writer.emitEmptyLine()
//                      .beginMethod(columnInfo.javaClass, handlerGetterJavaName(columnInfo), of(PUBLIC), "Cursor", "cursor")
//                      .emitStatement("return cursor.%s", handlerFieldParseMethod(columnInfo))
//                      .endMethod();
//            }
//        }

        writer.endType();
    }

    private static String handlerClassJavaName(TableInfo tableInfo) {
        return format("%sMapper", tableInfo.javaClass);
    }

    private static String handlerClassFQJavaName(TableInfo tableInfo) {
        return tableInfo.javaPackage + "." + handlerClassJavaName(tableInfo);
    }

    private static String handlerGenericClassJavaName(TableInfo tableInfo) {
        return ObjectMapper.class.getSimpleName() + "<" + tableInfo.javaClass + ">";
    }

    private static String handlerGenericClassJavaFQJavaName() {
        return ObjectMapper.class.getName();
    }

    private static String handlerFieldJavaName(ColumnInfo columnInfo) {
        return format("%sIndex", columnInfo.javaName);
    }

    private static String handlerFieldParseMethod(ColumnInfo columnInfo) {
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

    private static String classJavaName(TableInfo tableInfo) {
        return "Class<" + tableInfo.javaClass + ">";
    }

    private static String with1stLetterUpperCase(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1, value.length());
    }
}