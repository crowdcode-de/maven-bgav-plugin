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
import org.w3c.dom.Node;

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
    void writeChangedPomWithXPath(File pomfile, String ticketID) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/project/version";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
            nodeList.item(0).setTextContent(new MavenHandler(log).setPomVersion(oldPomVersion, ticketID));
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(pomfile));
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }

    /**
     * read end write POM with XPAth, due to an error in MavenXpp3Writer
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    void writeNonBgavPomWithXPath(File pomfile, String pomVersion) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/project/version";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
            nodeList.item(0).setTextContent(pomVersion);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(pomfile));
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }

    /**
     * write changed POM file with new branched feature version
     *
     * @param pomfile
     * @param artefact
     * @param ticketID
     * @throws MojoExecutionException
     */
    void writeChangedPomWithChangedDependency(File pomfile, String artefact, String ticketID) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//dependencies/dependency";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getTextContent().contains(artefact)) {
                    NodeList children = nodeList.item(i).getChildNodes();
                    log.info("found artefact: " + artefact + ", change to feature branched version");
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j).getNodeType() == Node.ELEMENT_NODE && children.item(j).getNodeName().equalsIgnoreCase("version")) {
                            String oldPomVersion = children.item(j).getTextContent();
                            children.item(j).setTextContent(new MavenHandler(log).setPomVersion(oldPomVersion, ticketID));
                        }
                    }
                }
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(pomfile));
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }
        
    void writeChangedNonBgavPomWithChangedDependency(File pomfile, String artefact) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//dependencies/dependency";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getTextContent().contains(artefact)) {
                    NodeList children = nodeList.item(i).getChildNodes();
                    log.info("found artefact: " + artefact + ", change to feature branched version");
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j).getNodeType() == Node.ELEMENT_NODE && children.item(j).getNodeName().equalsIgnoreCase("version")) {
                            String oldPomVersion = children.item(j).getTextContent();
                            children.item(j).setTextContent(new MavenHandler(log).setNonBgavPomVersion( oldPomVersion));
                        }
                    }
                }
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(document), new StreamResult(pomfile));
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }
}
