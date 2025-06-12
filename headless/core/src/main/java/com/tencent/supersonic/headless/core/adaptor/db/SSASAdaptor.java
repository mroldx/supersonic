package com.tencent.supersonic.headless.core.adaptor.db;


import com.tencent.supersonic.common.pojo.ssas.TableColumnInfo;
import com.tencent.supersonic.common.util.SsasXmlaClientUtils;
import com.tencent.supersonic.headless.api.pojo.DBColumn;
import com.tencent.supersonic.headless.api.pojo.enums.FieldType;
import com.tencent.supersonic.headless.core.pojo.ConnectInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapStatement;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server Analytics Service (SSAS) Adaptor
 */
@Slf4j
public class SSASAdaptor extends BaseDbAdaptor {

    @Override
    public String getDateFormat(String dateType, String dateFormat, String column) {
        return null;
    }

    @Override
    public String rewriteSql(String sql) {
        return sql;
    }

    @Override
    public List<String> getDBs(ConnectInfo connectionInfo, String catalog) throws SQLException {
        return new SsasXmlaClientUtils().getCatalogs(connectionInfo.getUrl());
    }

    @Override
    public List<String> getTables(ConnectInfo connectInfo, String catalog, String schemaName)
            throws SQLException {
        return new SsasXmlaClientUtils().getTables(connectInfo.getUrl(), catalog);
    }

    @Override
    public List<DBColumn> getColumns(ConnectInfo connectInfo, String catalog, String schemaName,
            String tableName) throws SQLException {
        List<DBColumn> dbColumns = new ArrayList<>();

        List<TableColumnInfo> tableColumnInfos =
                new SsasXmlaClientUtils().getColumns(connectInfo.getUrl(), catalog, tableName);
        for (TableColumnInfo columns : tableColumnInfos) {
            String columnName = columns.getColumnName();
            String dataType = columns.getDataType();
            String remarks = columns.getComment();
            FieldType fieldType = classifyColumnType(dataType);
            dbColumns.add(new DBColumn(columnName, dataType, remarks, fieldType));
        }
        return dbColumns;
    }

    @Override
    public FieldType classifyColumnType(String typeName) {
        switch (typeName.toUpperCase()) {
            case "INT":
            case "INTEGER":
            case "BIGINT":
            case "SMALLINT":
            case "SERIAL":
            case "BIGSERIAL":
            case "SMALLSERIAL":
            case "REAL":
            case "DOUBLE PRECISION":
            case "NUMERIC":
            case "DECIMAL":
                return FieldType.measure;
            case "DATE":
            case "TIME":
            case "TIMESTAMP":
            case "TIMESTAMPTZ":
            case "INTERVAL":
                return FieldType.time;
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "CHARACTER VARYING":
            case "CHARACTER":
            case "UUID":
            default:
                return FieldType.categorical;
        }
    }
}
