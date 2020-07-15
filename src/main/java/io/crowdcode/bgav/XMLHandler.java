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
     * read end write POM with XPAth, due to an error in MavenXpp3Writer
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    public String getPomVersion(File pomfile, String ticketID, String expression) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
            final String textContent = mavenHandler.determinePomVersion(oldPomVersion, ticketID);
            return textContent;
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not read POM Version: " + ex);
        }
    }

    /**
     * set a BGAV version the project version
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    boolean setBgavOnVersion(File pomfile, String ticketID) throws MojoExecutionException {
        return writeChangedPomWithXPath(pomfile,ticketID, "/project/version");
    }

    /**
     * set a BGAV version the project's parent version
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    boolean setBgavOnParentVersion(File pomfile, String ticketID) throws MojoExecutionException {
        return writeChangedPomWithXPath(pomfile,ticketID, "/project/parent/version");
    }


    /**
     * read end write POM with XPAth, due to an error in MavenXpp3Writer
     *
     * @param pomfile
     * @param ticketID
     * @throws MojoExecutionException
     */
    private boolean writeChangedPomWithXPath(File pomfile, String ticketID, String expression) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
            String oldPomVersion = nodeList.item(0).getTextContent();
            final String newVersion = mavenHandler.determinePomVersion(oldPomVersion, ticketID);
            if (!oldPomVersion.equals(newVersion)) {
                nodeList.item(0).setTextContent(newVersion);
                writePomFile(pomfile, document);
                return true;
            } else {
                log.debug("File "+pomfile.getAbsoluteFile()+" already has correct version");
            }
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
        return false;
    }

    /**
     * remote the BGAV version from the project version
     *
     * @param pomfile
     * @param pomVersion
     * @throws MojoExecutionException
     */
    boolean removeBgavFromVersion(File pomfile, String pomVersion) throws MojoExecutionException {
        String location = "/project/version";
        return setVersionInPom(pomfile, location, pomVersion);
    }


    /**
     * remote the BGAV version from the project's parent version
     *
     * @param pomfile
     * @param pomVersion
     * @throws MojoExecutionException
     */
    boolean removeBgavFromParentVersion(File pomfile, String pomVersion) throws MojoExecutionException {
        String location = "/project/parent/version";
        return setVersionInPom(pomfile, location, pomVersion);
    }

    boolean setVersionInPom(File pomfile, String location, String pomVersion) throws MojoExecutionException {
        try (final FileInputStream fileInputStream = new FileInputStream(pomfile)) {
            Document document = getDocument(fileInputStream);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.compile(location).evaluate(document, XPathConstants.NODESET);
            if (pomVersion.equals(nodeList.item(0).getTextContent())) {
                nodeList.item(0).setTextContent(pomVersion);
                writePomFile(pomfile, document);
                return true;
            }
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
        return false;
    }


    boolean alterDependency(File pomfile, String artifact, String newVersion) throws MojoExecutionException {
        boolean willWritePom = false;
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
                            if (!oldPomVersion.equals(newVersion)) {
                                children.item(j).setTextContent(newVersion);
                                willWritePom = true;
                            }
                        }
                    }
                }
            }
            if (willWritePom) {
                writePomFile(pomfile, document);
            }
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
        return willWritePom;
    }


    private void writePomFile(File pomfile, Document document) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(pomfile));
    }

    boolean alterProperty(File pomfile, String propertyName, String targetPomVersion) throws MojoExecutionException {
        boolean willWritePom = false;
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
                        if (!targetPomVersion.equals(child.getTextContent())) {
                            child.setTextContent(targetPomVersion);
                            willWritePom = true;
                        }
                    }
                }
            }
            if (willWritePom) {
                writePomFile(pomfile, document);
            }
        } catch (Exception ex) {
            log.error("IOException: " + ex);
            throw new MojoExecutionException("could not write POM: " + ex);
        }
        return willWritePom;
    }

    private Document getDocument(FileInputStream fileInputStream) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(fileInputStream);
    }
}
