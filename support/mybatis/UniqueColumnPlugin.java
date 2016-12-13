package support.mybatis;

import org.apache.commons.lang.StringUtils;
import org.mybatis.generator.api.FullyQualifiedTable;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.Attribute;
import org.mybatis.generator.api.dom.xml.Document;
import org.mybatis.generator.api.dom.xml.TextElement;
import org.mybatis.generator.api.dom.xml.XmlElement;
import org.mybatis.generator.codegen.mybatis3.MyBatis3FormattingUtilities;
import org.mybatis.generator.internal.util.JavaBeansUtil;

import java.util.*;

import static org.mybatis.generator.internal.util.StringUtility.stringHasValue;

/**
 <table tableName="test" domainObjectName="Test">
    <property name="uniqueColumns" value="type,status|name"/>
 </table>
 */
public class UniqueColumnPlugin extends PluginAdapter {

    private Map<FullyQualifiedTable, List<XmlElement>> elementsToAdd;

    public UniqueColumnPlugin() {
        elementsToAdd = new HashMap<>();
    }

    @Override
    public boolean validate(List<String> warnings) {
        return true;
    }

    @Override
    public boolean sqlMapDocumentGenerated(Document document,
                                           IntrospectedTable introspectedTable) {
        List<XmlElement> elements = elementsToAdd.get(introspectedTable.getFullyQualifiedTable());
        if (elements != null) {
            for (XmlElement element : elements) {
                document.getRootElement().addElement(element);
            }
        }

        return true;
    }

    @Override
    public boolean clientSelectByPrimaryKeyMethodGenerated(Method method, Interface interfaze, IntrospectedTable introspectedTable) {
        String uniqueColumns = introspectedTable.getTableConfiguration().getProperty("uniqueColumns"); //$NON-NLS-1$
        if (StringUtils.isNotBlank(uniqueColumns) && introspectedTable.getTargetRuntime() == IntrospectedTable.TargetRuntime.MYBATIS3) {
            for(String columns : uniqueColumns.split("\\|")) {
                if(StringUtils.isNotBlank(columns)) {
                    addSelectByUniqueColumnMethod(introspectedTable, interfaze, getColumns(introspectedTable, columns.trim()));
                }
            }
        }
        return true;
    }

    @Override
    public boolean sqlMapSelectByPrimaryKeyElementGenerated(
            XmlElement element, IntrospectedTable introspectedTable) {
        String uniqueColumns = introspectedTable.getTableConfiguration().getProperty("uniqueColumns");
        if (StringUtils.isNotBlank(uniqueColumns) && introspectedTable.getTargetRuntime() == IntrospectedTable.TargetRuntime.MYBATIS3) {
            for(String columns : uniqueColumns.split("\\|")) {
                if(StringUtils.isNotBlank(columns)) {
                    addSelectByUniqueColumnElement(introspectedTable, getColumns(introspectedTable, columns.trim()));
                }
            }
        }
        return true;
    }

    private List<IntrospectedColumn> getColumns(IntrospectedTable introspectedTable, String columnNames) {
        List<String> columnList = Arrays.asList(columnNames.split(","));
        List<IntrospectedColumn> list = new ArrayList<>();
        List<IntrospectedColumn> introspectedColumns = introspectedTable.getAllColumns();
        for (IntrospectedColumn introspectedColumn : introspectedColumns) {
            if (columnList.contains(introspectedColumn.getActualColumnName())) {
                list.add(introspectedColumn);
            }
        }
        return list;
    }


    private void addSelectByUniqueColumnMethod(IntrospectedTable introspectedTable, Interface interfaze, List<IntrospectedColumn> columns) {
        if(columns.size() == 0) return;
        Method method = new Method();
        method.setVisibility(JavaVisibility.PUBLIC);

        Set<FullyQualifiedJavaType> importedTypes = new TreeSet<>();
        FullyQualifiedJavaType returnType = introspectedTable.getRules().calculateAllFieldsClass();
        method.setReturnType(returnType);
        importedTypes.add(returnType);

        method.setName(getSelectByColumnId(columns));

        boolean annotate = columns.size() > 1;
        if (annotate) {
            importedTypes.add(new FullyQualifiedJavaType("org.apache.ibatis.annotations.Param"));
        }
        StringBuilder sb = new StringBuilder();
        for (IntrospectedColumn introspectedColumn : columns) {
            FullyQualifiedJavaType type = introspectedColumn.getFullyQualifiedJavaType();
            importedTypes.add(type);
            Parameter parameter = new Parameter(type, introspectedColumn.getJavaProperty());
            if (annotate) {
                sb.setLength(0);
                sb.append("@Param(\"");
                sb.append(introspectedColumn.getJavaProperty());
                sb.append("\")");
                parameter.addAnnotation(sb.toString());
            }
            method.addParameter(parameter);
        }

        interfaze.addMethod(method);
        interfaze.addImportedTypes(importedTypes);
    }

    private void addSelectByUniqueColumnElement(IntrospectedTable introspectedTable, List<IntrospectedColumn> columns) {
        if(columns.size() == 0) return;

        FullyQualifiedTable fqt = introspectedTable.getFullyQualifiedTable();

        XmlElement answer = new XmlElement("select");

        answer.addAttribute(new Attribute("id", getSelectByColumnId(columns)));

        if (introspectedTable.getRules().generateResultMapWithBLOBs()) {
            answer.addAttribute(new Attribute("resultMap", introspectedTable.getResultMapWithBLOBsId()));
        } else {
            answer.addAttribute(new Attribute("resultMap", introspectedTable.getBaseResultMapId()));
        }

        String parameterType;
        if (columns.size() > 1) {
            parameterType = "map";
        } else {
            parameterType = columns.get(0).getFullyQualifiedJavaType().toString();
        }

        answer.addAttribute(new Attribute("parameterType", parameterType));

        context.getCommentGenerator().addComment(answer);

        StringBuilder sb = new StringBuilder();
        sb.append("select ");

        if (stringHasValue(introspectedTable.getSelectByPrimaryKeyQueryId())) {
            sb.append('\'');
            sb.append(introspectedTable.getSelectByPrimaryKeyQueryId());
            sb.append("' as QUERYID,");
        }
        answer.addElement(new TextElement(sb.toString()));
        answer.addElement(getBaseColumnListElement(introspectedTable));
        if (introspectedTable.hasBLOBColumns()) {
            answer.addElement(new TextElement(","));
            answer.addElement(getBlobColumnListElement(introspectedTable));
        }

        sb.setLength(0);
        sb.append("from ");
        sb.append(introspectedTable.getAliasedFullyQualifiedTableNameAtRuntime());
        answer.addElement(new TextElement(sb.toString()));

        boolean and = false;
        for (IntrospectedColumn introspectedColumn : columns) {
            sb.setLength(0);
            if (and) {
                sb.append("  and ");
            } else {
                sb.append("where ");
                and = true;
            }

            sb.append(MyBatis3FormattingUtilities.getAliasedEscapedColumnName(introspectedColumn));
            sb.append(" = ");
            sb.append(MyBatis3FormattingUtilities.getParameterClause(introspectedColumn));
            answer.addElement(new TextElement(sb.toString()));
        }

        answer.addElement(new TextElement("limit 1"));

        // save the new element locally.   We'll add it to the document
        // later
        List<XmlElement> elements = elementsToAdd.get(fqt);
        if (elements == null) {
            elements = new ArrayList<XmlElement>();
            elementsToAdd.put(fqt, elements);
        }
        elements.add(answer);
    }

    private String getSelectByColumnId(List<IntrospectedColumn> columns) {
        List<String> columnNames = new ArrayList<>();
        for(IntrospectedColumn column : columns) {
            columnNames.add(JavaBeansUtil.getCamelCaseString(column.getActualColumnName(), true));
        }
        return "selectBy" + StringUtils.join(columnNames, "And");
    }

    private XmlElement getBaseColumnListElement(IntrospectedTable introspectedTable) {
        XmlElement answer = new XmlElement("include");
        answer.addAttribute(new Attribute("refid", introspectedTable.getBaseColumnListId()));
        return answer;
    }

    private XmlElement getBlobColumnListElement(IntrospectedTable introspectedTable) {
        XmlElement answer = new XmlElement("include");
        answer.addAttribute(new Attribute("refid", introspectedTable.getBlobColumnListId()));
        return answer;
    }
}
