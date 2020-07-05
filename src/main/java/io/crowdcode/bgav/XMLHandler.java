package io.crowdcode.bgav;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * @author andreas
 */
public class XMLHandler {

    private final Log log;
    private final boolean suppressCommit;
    private final boolean suppressPush;
    private final MavenHandler mavenHandler;

    public XMLHandler(boolean suppresCommit, boolean suppressPush, MavenHandler mavenHandler) {
        this.suppressCommit = suppresCommit;
        this.suppressPush = suppressPush;
        this.mavenHandler = mavenHandler;
        log = null;
    }

    public XMLHandler(Log log, boolean suppresCommit, boolean suppressPush, MavenHandler mavenHandler) {
        this.log = log;
        this.suppressCommit = suppresCommit;
        this.suppressPush = suppressPush;
        this.mavenHandler = mavenHandler;
    }

    /**
     * set a BGAV version the project version
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    void setBgavOnVersion(File pomfile, String ticketID) throws MojoExecutionException {
        writeChangedPomWithXPath(pomfile,ticketID, "/project/version");
    }

    /**
     * set a BGAV version the project's parent version
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    void setBgavOnParentVersion(File pomfile, String ticketID) throws MojoExecutionException {
        writeChangedPomWithXPath(pomfile,ticketID, "/project/parent/version");
    }

    /**
     * read end write POM with XPAth, due to an error in MavenXpp3Writer
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    private void writeChangedPomWithXPath(File pomfile, String ticketID, String expression) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
            final String textContent = mavenHandler.determinePomVersion(oldPomVersion, ticketID);
            nodeList.item(0).setTextContent(textContent);
            writePomFile(pomfile, document);
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }

    /**
     * remote the BGAV version from the project version
     *
     * @param pomfile
     * @param pomVersion
     * @throws MojoExecutionException
     */
    void removeBgavFromVersion(File pomfile, String pomVersion) throws MojoExecutionException {
        String location = "/project/version";
        setVersionInPom(pomfile, location, pomVersion);
    }


    /**
     * remote the BGAV version from the project's parent version
     *
     * @param pomfile
     * @param pomVersion
     * @throws MojoExecutionException
     */
    void removeBgavFromParentVersion(File pomfile, String pomVersion) throws MojoExecutionException {
        String location = "/project/parent/version";
        setVersionInPom(pomfile, location, pomVersion);
    }

    void setVersionInPom(File pomfile, String location, String pomVersion) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile(location).evaluate(document, XPathConstants.NODESET);
            nodeList.item(0).setTextContent(pomVersion);
            writePomFile(pomfile, document);
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }


    void alterDependency(File pomfile, String artifact, String newVersion) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//dependencies/dependency";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                if (nodeList.item(i).getTextContent().contains(artifact)) {
                    NodeList children = nodeList.item(i).getChildNodes();
                    log.info("found artifact: " + artifact + ", change version " + newVersion);
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j).getNodeType() == Node.ELEMENT_NODE && children.item(j).getNodeName().equalsIgnoreCase("version")) {
                            String oldPomVersion = children.item(j).getTextContent();
                            children.item(j).setTextContent(newVersion);
                        }
                    }
                }
            }
            writePomFile(pomfile, document);
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }


    private void writePomFile(File pomfile, Document document) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(pomfile));
    }

    void alterProperty(File pomfile, String propertyName, String targetPomVersion) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "//properties";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                final Node item = nodeList.item(i);
                NodeList childNodes = item.getChildNodes();
                for (int j = 0; j < childNodes.getLength(); j++) {
                    Node child = childNodes.item(j);
                    // log.debug(child.getNodeName() + " -> " + child.getTextContent());
                    if (child.getNodeName().equals(propertyName)) {
                        log.info("found property: " + propertyName + ", change to version " + targetPomVersion);
                        child.setTextContent(targetPomVersion);
                    }
                }
            }
            writePomFile(pomfile, document);
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
    }

    private Document getDocument(FileInputStream fileInputStream) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(fileInputStream);
    }
}
