/*
 * $Id$
 * $Revision: 5697 $ $Date: Oct 9, 2013 $
 *
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */

package org.mycore.common.xml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.Vector;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.xerces.util.XMLCatalogResolver;
import org.mycore.common.MCRCache;
import org.mycore.common.config.MCRConfiguration;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 * MCREntityResolver uses {@link XMLCatalogResolver} for resolving entities or - for compatibility reasons - looks in classpath to resolve XSD and DTD files.
 * @author Thomas Scheffler (yagee)
 * @since 2013.10
 */
public class MCREntityResolver implements EntityResolver2, LSResourceResolver {

    public static final Logger LOGGER = Logger.getLogger(MCREntityResolver.class);

    private static final String CONFIG_PREFIX = "MCR.URIResolver.";

    private MCRCache<String, byte[]> bytesCache;

    XMLCatalogResolver catalogResolver;

    private static class MCREntityResolverHolder {
        public static MCREntityResolver instance = new MCREntityResolver();
    }

    public static MCREntityResolver instance() {
        return MCREntityResolverHolder.instance;
    }

    private MCREntityResolver() {
        Enumeration<URL> systemResources;
        try {
            systemResources = this.getClass().getClassLoader().getResources("catalog.xml");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        Vector<String> catalogURIs = new Vector<>();
        while (systemResources.hasMoreElements()) {
            URL catalogURL = systemResources.nextElement();
            LOGGER.info("Using XML catalog: " + catalogURL);
            catalogURIs.add(catalogURL.toString());
        }
        String[] catalogs = catalogURIs.toArray(new String[catalogURIs.size()]);
        catalogResolver = new XMLCatalogResolver(catalogs);
        int cacheSize = MCRConfiguration.instance().getInt(CONFIG_PREFIX + "StaticFiles.CacheSize", 100);
        bytesCache = new MCRCache<String, byte[]>(cacheSize, "URIResolver Resources");
    }

    /* (non-Javadoc)
     * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MessageFormat.format("Resolving: \npublicId: {0}\nsystemId: {1}", publicId, systemId));
        }
        InputSource entity = catalogResolver.resolveEntity(publicId, systemId);
        if (entity != null) {
            return resolvedEntity(entity);
        }
        return resolveEntity(null, publicId, null, getFileName(systemId));
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.EntityResolver2#getExternalSubset(java.lang.String, java.lang.String)
     */
    @Override
    public InputSource getExternalSubset(String name, String baseURI) throws SAXException, IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MessageFormat.format("External Subset: \nname: {0}\nbaseURI: {1}", name, baseURI));
        }
        InputSource externalSubset = catalogResolver.getExternalSubset(name, baseURI);
        if (externalSubset != null) {
            return resolvedEntity(externalSubset);
        }
        return resolveEntity(name, null, baseURI, null);
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.EntityResolver2#resolveEntity(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public InputSource resolveEntity(String name, String publicId, String baseURI, String systemId)
        throws SAXException, IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MessageFormat.format("Resolving: \nname: {0}\npublicId: {1}\nbaseURI: {2}\nsystemId: {3}",
                name, publicId, baseURI, systemId));
        }
        InputSource entity = catalogResolver.resolveEntity(name, publicId, baseURI, systemId);
        if (entity != null) {
            return resolvedEntity(entity);
        }
        if (systemId == null) {
            return null; // Use default resolver
        }

        if (systemId.length() == 0) {
            // if you overwrite SYSTEM by empty String in XSL
            return new InputSource(new StringReader(""));
        }
        InputStream is = getCachedResource("/" + systemId);
        if (is == null) {
            return null;
        }
        return new InputSource(is);
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MessageFormat.format(
                "Resolving resource: \ntype: {0}\nnamespaceURI: {1}\npublicId: {2}\nsystemId: {3}\nbaseURI: {4}", type,
                namespaceURI, publicId, systemId, baseURI));
        }
        return catalogResolver.resolveResource(type, namespaceURI, publicId, systemId, baseURI);
    }

    private InputSource resolvedEntity(InputSource entity) {
        String msg = "Resolved  to: " + entity.getSystemId() + ".";
        LOGGER.info(msg);
        return entity;
    }

    private InputStream getCachedResource(String classResource) throws IOException {
        byte[] bytes = bytesCache.get(classResource);

        if (bytes == null) {
            LOGGER.debug("Resolving resource " + classResource);
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream in = this.getClass().getResourceAsStream(classResource)) {
                if (in == null) {
                    LOGGER.debug(classResource + " not found");
                    return null;
                }
                IOUtils.copy(in, baos);
                bytes = baos.toByteArray();
            }
            bytesCache.put(classResource, bytes);
        }

        return new ByteArrayInputStream(bytes);
    }

    /**
     * Returns the filename part of a path if path is absolute URI
     * 
     * @param path
     *            the path of a file
     * @return the part after the last / or \\
     * @throws URISyntaxException 
     */
    private String getFileName(String path) {
        int posA = path.lastIndexOf("/");
        int posB = path.lastIndexOf("\\");
        int pos = posA == -1 ? posB : posA;

        return pos == -1 ? path : path.substring(pos + 1);
    }

}