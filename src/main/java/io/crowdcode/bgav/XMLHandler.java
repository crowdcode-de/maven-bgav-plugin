package io.crowdcode.bgav;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;

/**
 *
 * @author andreas
 */
public class XMLHandler {

    private final Log log;

    public XMLHandler() {
        log = null;
    }

    public XMLHandler(Log log) {
        this.log = log;
    }

    /**
     * read end write POM with XPAth, due to an error in MavenXpp3Writer
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    void writeChangedPomWithXPath(File pomfile, String ticketID ) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/project/version";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
//            log.info("nodeList: " + nodeList.getLength());
//            log.info("nodeList: " + nodeList.item(0).getTextContent());
            nodeList.item(0).setTextContent(new MavenHandler(log).setPomVersion(oldPomVersion, ticketID));
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(pomfile));
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }
}
