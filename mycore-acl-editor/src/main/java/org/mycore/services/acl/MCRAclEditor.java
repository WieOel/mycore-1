package org.mycore.services.acl;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.jdom2.Element;
import org.mycore.common.MCRConfiguration;

/**
 * @author "Huu Chi Vu"
 *
 */
public abstract class MCRAclEditor {
    protected static Logger LOGGER = Logger.getLogger(MCRAclEditor.class);

    public abstract Element getACLEditor(HttpServletRequest request);

    public abstract Element dataRequest(HttpServletRequest request);

    public static MCRAclEditor instance() {
        return (MCRAclEditor) MCRConfiguration.instance().getInstanceOf("MCR.ACL.Editor.class");
    }

}