package com.tencent.supersonic.headless.chat.utils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.tencent.supersonic.common.pojo.ssas.AsConnectInfo;
import com.tencent.supersonic.common.pojo.ssas.DaxResultInfo;
import com.tencent.supersonic.common.util.SsasXmlaClientUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.*;

public class DaxResultParserTest {
    public static void main(String[] args) throws Exception {
        // AsConnectInfo asConnectInfo = new AsConnectInfo();
        // asConnectInfo.setConnectUrl("powerbi://api.powerbi.cn/v1.0/myorg/04-%E6%B5%8B%E8%AF%95%E5%B7%A5%E4%BD%9C%E5%8C%BA-%E6%82%A6%E7%AD%96");
        // asConnectInfo.setUserId("azc_yeacer@yuexiuproperty.partner.onmschina.cn");
        // asConnectInfo.setPassword("Tak53869");
        // asConnectInfo.setDbName("微容-驾驶舱(教学)");
        // asConnectInfo.setQueryString("EVALUATE SUMMARIZECOLUMNS('D_日期表'[年], \"销售数量\", [销售数量])");
        // HttpResponse response = HttpRequest.post(
        // "http://192.168.0.115:5000/TabularQuery/resultByDax")
        // .body(JSON.toJSONString(asConnectInfo), "application/json").execute();
        // JSONObject j = JSONObject.parseObject(response.body());
        // String result = j.getString("data");
        // Map<String, Object> objectMap = SsasXmlaClientUtils.parseFieldsAndValues(result);
        // System.out.println(objectMap);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(
                "<root xmlns=\"urn:schemas-microsoft-com:xml-analysis:rowset\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:msxmla=\"http://schemas.microsoft.com/analysisservices/2003/xmla\">\n"
                        + "    <xsd:schema targetNamespace=\"urn:schemas-microsoft-com:xml-analysis:rowset\" xmlns:sql=\"urn:schemas-microsoft-com:xml-sql\" elementFormDefault=\"qualified\">\n"
                        + "        <xsd:element name=\"root\">\n"
                        + "            <xsd:complexType>\n"
                        + "                <xsd:sequence minOccurs=\"0\" maxOccurs=\"unbounded\">\n"
                        + "                    <xsd:element name=\"row\" type=\"row\" />\n"
                        + "                </xsd:sequence>\n" + "            </xsd:complexType>\n"
                        + "        </xsd:element>\n" + "        <xsd:simpleType name=\"uuid\">\n"
                        + "            <xsd:restriction base=\"xsd:string\">\n"
                        + "                <xsd:pattern value=\"[0-9a-zA-Z]{8}-[0-9a-zA-Z]{4}-[0-9a-zA-Z]{4}-[0-9a-zA-Z]{4}-[0-9a-zA-Z]{12}\" />\n"
                        + "            </xsd:restriction>\n" + "        </xsd:simpleType>\n"
                        + "        <xsd:complexType name=\"xmlDocument\">\n"
                        + "            <xsd:sequence>\n" + "                <xsd:any />\n"
                        + "            </xsd:sequence>\n" + "        </xsd:complexType>\n"
                        + "        <xsd:complexType name=\"row\">\n"
                        + "            <xsd:sequence>\n"
                        + "                <xsd:element sql:field=\"[销售费用]\" name=\"C0\" type=\"xsd:long\" minOccurs=\"0\" />\n"
                        + "            </xsd:sequence>\n" + "        </xsd:complexType>\n"
                        + "    </xsd:schema>\n" + "    <row>\n" + "        <C0>186980071</C0>\n"
                        + "    </row>\n" + "</root>")));
        List<Map<String, Object>> resultList = new ArrayList<>();
        // // 定位到包含 Catalog 信息的节点
        // NodeList rows = doc.getElementsByTagName("row");
        // for (int i = 0; i < rows.getLength(); i++) {
        // Map<String, Object> rowMap = new LinkedHashMap<>();
        // Element rowElement = (Element) rows.item(i);
        // NodeList children = rowElement.getChildNodes();
        // for (int j = 0; j < children.getLength(); j++) {
        // Node child = children.item(j);
        // if (child.getNodeType() == Node.ELEMENT_NODE) {
        // // 解码XML编码的特殊字符
        // String decodedName = child.getNodeName()
        // .replace("_x005B_", "[")
        // .replace("_x005D_", "]");
        //
        // rowMap.put(decodedName, child.getTextContent());
        // }
        // }
        // resultList.add(rowMap);
        // }

        NodeList elements = doc.getElementsByTagName("xsd:element");
        // 1. 解析Schema获取字段映射
        Map<String, String> fieldMapping = new HashMap<>();
        for (int i = 0; i < elements.getLength(); i++) {
            Element el = (Element) elements.item(i);
            String techName = el.getAttribute("name");
            String bizName = el.getAttribute("sql:field");
            if (!bizName.isEmpty()) {
                fieldMapping.put(techName, bizName);
            }
        }

        // 2. 解析实际数据
        NodeList rows = doc.getElementsByTagName("row");
        for (int i = 0; i < rows.getLength(); i++) {
            Element row = (Element) rows.item(i);
            NodeList cells = row.getChildNodes();

            Map<String, Object> rowMap = new LinkedHashMap<>();
            for (int j = 0; j < cells.getLength(); j++) {
                if (cells.item(j)instanceof Element cell) {
                    String techName = cell.getNodeName();
                    String bizName = fieldMapping.get(techName);
                    rowMap.put(bizName, cell.getTextContent());// 存储业务名称
                }
            }
            resultList.add(rowMap);
        }
        System.out.println(resultList);
    }
}
