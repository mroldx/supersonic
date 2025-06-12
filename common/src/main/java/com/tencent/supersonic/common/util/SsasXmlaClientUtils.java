package com.tencent.supersonic.common.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import cn.hutool.core.text.split.SplitIter;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSONObject;
import com.tencent.supersonic.common.pojo.ssas.TableColumnInfo;
import lombok.extern.slf4j.Slf4j;
import org.olap4j.*;
import org.olap4j.driver.xmla.XmlaOlap4jDriver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SsasXmlaClientUtils {

    public static void main(String[] args) throws SQLException {
        // List<String> catalogs = new
        // SsasXmlaClientUtils().getCatalogs("http://192.168.0.115/olap/msmdpump.dll");
        // System.out.println(catalogs);

        // List<String> tables = new
        // SsasXmlaClientUtils().getTables("http://192.168.0.115/olap/msmdpump.dll",
        // "Financial_Analysis");
        // System.out.println(tables);
        //
        // List<TableColumnInfo> columns = new
        // SsasXmlaClientUtils().getColumns("http://192.168.0.115/olap/msmdpump.dll",
        // "Financial_Analysis", "DW_D_Company");
        // System.out.println(columns);

        Properties props = new Properties();
        // 关键：catalog通过属性"Catalog"传递（注意大小写敏感！）
        props.setProperty("Catalog", "Tabular_Demo");
        // 创建驱动实例并连接
        // 原始HTTP地址
        String httpUrl =
                "powerbi://api.powerbi.cn/v1.0/myorg/04-%E6%B5%8B%E8%AF%95%E5%B7%A5%E4%BD%9C%E5%8C%BA-%E6%82%A6%E7%AD%96;Format=Tabular";

        // 必须转换为JDBC XMLA格式
        String jdbcXmlaUrl = "jdbc:xmla:Server=" + httpUrl;
        Connection connection = new XmlaOlap4jDriver().connect(jdbcXmlaUrl, props);
        // Connection connection= DriverManager.getConnection(jdbcXmlaUrl, props);
        OlapConnection unwrap = connection.unwrap(OlapConnection.class);

        String query01 = "EVALUATE \n" + "SUMMARIZECOLUMNS(\n"
                + "  --  FILTER(VALUES('F_销售数据表'[公司]), 'F_销售数据表'[公司] = \"深圳公司\"),\n"
                + "    FILTER(VALUES('D_日期表'[Date]), 'D_日期表'[Date] = DATE(2023,10,1)),\n"
                + "    \"销售收入\", [销售收入]\n" + ")";

        try (OlapStatement statement = unwrap.createStatement()) {
            CellSet cellSet = statement.executeOlapQuery(query01);
            CellSetMetaData metaData = cellSet.getMetaData();
            // 获取CATALOG_NAME列索引（从0开始）
            // int catalogNameColIndex = cellSet.getMetaData()
            // .getColumnFields()
            // .indexOf("CATALOG_NAME");
            //
            // // 提取数据
            // List<String> catalogs = new ArrayList<>();
            // for (int row = 0; row < cellSet.getAxes().get(1).getPositions().size(); row++) {
            // catalogs.add(
            // cellSet.getCell(row, catalogNameColIndex).getStringValue()
            // );
            // }
        } catch (OlapException e) {
            e.printStackTrace();
            // System.out.println(extractFaultString(e));
        }
    }


    /**
     * 执行 DAX 查询并返回 JSON 结果
     */
    public String executeDax(String xmlaEndpoint, String daxQuery, String catalog) {
        // 构建 XMLA SOAP 请求
        String soapRequest = buildSoapEnvelope(daxQuery, catalog, null, null, null);

        // 发送请求
        HttpResponse response =
                HttpRequest.post(xmlaEndpoint).header("Content-Type", "text/xml; charset=utf-8")
                        .header("SOAPAction", "urn:schemas-microsoft-com:xml-analysis:Execute")
                        .body(soapRequest).timeout(30000).execute();

        if (response.body().contains("faultstring")) {
            parseSoapResponse(response.body());
        }
        return response.body();
    }

    public Map<String, Object> getDaxResult(String xmlaEndpoint, String daxQuery, String catalog) {
        String result = this.executeDax(xmlaEndpoint, daxQuery, catalog);
        return parseFieldsAndValues(result);
    }

    public static Map<String, Object> parseFieldsAndValues(String xml) {
        Map<String, Object> result = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("<_x005B_(.*?)_x005D_>(.*?)</_x005B_\\1_x005D_>");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String fieldName = matcher.group(1); // 如 "应收账款_期末"
            String value = matcher.group(2); // 如 "1.8901117745999996E4"
            // 判断值是否为科学计数法，如果是转换为String的类型并只保留4位小数
            if (value.contains("E")) {
                value = new BigDecimal(value).setScale(4, RoundingMode.HALF_UP).toString();
            }
            result.put(fieldName, value);
        }
        return result;
    }

    public List<String> getCatalogs(String xmlaEndpoint) {
        String result = executeDax(xmlaEndpoint, "SELECT * FROM $SYSTEM.DBSCHEMA_CATALOGS", null);
        // 解析 SOAP 响应
        try {
            return parseBodyByField(result, "CATALOG_NAME");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getTables(String xmlaEndpoint, String catalog) {
        String result = executeDax(xmlaEndpoint, "SELECT * FROM $SYSTEM.TMSCHEMA_TABLES", catalog);
        // 解析 SOAP 响应
        try {
            return parseBodyByField(result, "Name");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<TableColumnInfo> getColumns(String xmlaEndpoint, String catalog, String tableName) {
        String result = executeDax(xmlaEndpoint,
                "SELECT * FROM $SYSTEM.TMSCHEMA_TABLES" + " WHERE [Name] = '" + tableName + "'",
                catalog);
        // 解析 SOAP 响应
        try {
            String id = parseBodyByField(result, "ID").get(0);
            result = executeDax(xmlaEndpoint,
                    "SELECT * FROM $SYSTEM.TMSCHEMA_COLUMNS" + " WHERE [TableID] = '" + id + "'",
                    catalog);
            return parseColumnByResult(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static List<TableColumnInfo> parseColumnByResult(String soapResponse)
            throws ParserConfigurationException, SAXException, IOException {
        List<TableColumnInfo> catalogs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(soapResponse)));

        // 定位到包含 Catalog 信息的节点
        NodeList rows = doc.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            Element columnName = (Element) row.getElementsByTagName("ExplicitName").item(0);
            Element inferredName = (Element) row.getElementsByTagName("InferredName").item(0);
            Element sourceProviderType =
                    (Element) row.getElementsByTagName("SourceProviderType").item(0);
            Element isHidden = (Element) row.getElementsByTagName("IsHidden").item(0);
            if (columnName != null) {
                if (isHidden != null && "1".equals(isHidden.getTextContent())) {
                    continue;
                }
                if (columnName.getTextContent().contains("RowNumber")) {
                    continue;
                }
                TableColumnInfo catalogName = new TableColumnInfo();
                catalogName.setColumnName(columnName.getTextContent());
                catalogName.setDataType(Optional.ofNullable(sourceProviderType)
                        .map(Element::getTextContent).orElse(null));
                catalogName.setComment(Optional.ofNullable(inferredName)
                        .map(Element::getTextContent).orElse(null));
                catalogs.add(catalogName);
            }
        }
        return catalogs;
    }

    public static List<String> parseBodyByField(String soapResponse, String field)
            throws ParserConfigurationException, SAXException, IOException {
        List<String> catalogs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(soapResponse)));

        // 定位到包含 Catalog 信息的节点
        NodeList rows = doc.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            Element catalogElement = (Element) row.getElementsByTagName(field).item(0);
            if (catalogElement != null) {
                String catalogName = catalogElement.getTextContent();
                catalogs.add(catalogName);
            }
        }
        return catalogs;
    }

    /**
     * 构建 XMLA SOAP 请求信封
     */
    private String buildSoapEnvelope(String query, String catalog, String format, String content,
            String axisFormat) {
        StringBuilder soapEnvelope = new StringBuilder();
        soapEnvelope.append(
                "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">\n")
                .append("    <soap:Header>\n")
                .append("        <X-Target-Database></X-Target-Database>\n")
                .append("    </soap:Header>\n").append("    <soap:Body>\n")
                .append("        <Execute xmlns=\"urn:schemas-microsoft-com:xml-analysis\">\n")
                .append("            <Command>\n")
                .append("                <Statement> \n <![CDATA[ ").append(query)
                .append("]]> \n </Statement>\n").append("            </Command>\n")
                .append("            <Properties>\n").append("                <PropertyList>\n");

        // 根据参数是否为空决定是否拼接属性
        if (catalog != null && !catalog.isEmpty()) {
            soapEnvelope.append("                    <Catalog>").append(catalog)
                    .append("</Catalog>\n");
        }
        if (format != null && !format.isEmpty()) {
            soapEnvelope.append("                    <Format>").append(format)
                    .append("</Format>\n");
        }
        if (content != null && !content.isEmpty()) {
            soapEnvelope.append("                    <Content>").append(content)
                    .append("</Content>\n");
        }
        if (axisFormat != null && !axisFormat.isEmpty()) {
            soapEnvelope.append("                    <AxisFormat>").append(axisFormat)
                    .append("</AxisFormat>\n");
        }

        soapEnvelope.append("                </PropertyList>\n")
                .append("            </Properties>\n").append("        </Execute>\n")
                .append("    </soap:Body>\n").append("</soap:Envelope>");

        return soapEnvelope.toString();
    }


    /**
     * 解析 SOAP 响应并提取错误信息
     */
    private JSONObject parseSoapResponse(String soapResponse) {
        try {
            // 提取有效数据部分
            String jsonStr = soapResponse.replaceFirst("(?s).*<return xmlns=\"\">", "")
                    .replaceFirst("(?s)</return>.*", "");

            return JSONObject.parseObject(jsonStr);
        } catch (Exception e) {
            // 尝试提取错误信息
            String faultString = soapResponse.replaceFirst("(?s).*<faultstring>", "")
                    .replaceFirst("(?s)</faultstring>.*", "").trim();

            if (!faultString.isEmpty()) {
                throw new RuntimeException("SSAS 错误: " + faultString);
            }
            throw new RuntimeException("解析 SSAS 响应失败", e);
        }
    }

    /**
     * XML 特殊字符转义
     */
    private String escapeXml(String input) {
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
