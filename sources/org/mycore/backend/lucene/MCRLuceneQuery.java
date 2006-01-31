/*
 * $RCSfile$
 * $Revision$ $Date$
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

package org.mycore.backend.lucene;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.jdom.Element;
import org.mycore.parsers.bool.MCRCondition;
import org.mycore.parsers.bool.MCRConditionVisitor;
import org.mycore.services.fieldquery.MCRFieldDef;
import org.mycore.services.fieldquery.MCRFieldValue;
import org.mycore.services.fieldquery.MCRHit;
import org.mycore.services.fieldquery.MCRResults;

/**
 * Helper class for generating lucene query from mycore query
 * 
 * @author Harald Richter
 * 
 */
public class MCRLuceneQuery implements MCRConditionVisitor {
    /** The logger */
    public static Logger LOGGER = Logger.getLogger(MCRLuceneQuery.class);

    private String IndexDir = "";

    private Query luceneQuery;

    private int maxResults = 200;

    public MCRLuceneQuery(MCRCondition cond, int maxResults, String IndexDir) {
        try {
            this.maxResults = maxResults;
            this.IndexDir = IndexDir;

            List f = new ArrayList();
            f.add(cond.toXML());

            boolean reqf = true; // required flag Term with AND (true) or OR
                                    // (false) combined
            luceneQuery = MCRBuildLuceneQuery.buildLuceneQuery(null, reqf, f);
            LOGGER.debug("Lucene Query: " + luceneQuery.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * method does lucene query
     * 
     * @return result set
     */
    public MCRResults getLuceneHits() throws Exception {
        IndexSearcher searcher = new IndexSearcher(IndexDir);

        // Hits hits = searcher.search( luceneQuery );
        // int found = hits.length();
        TopDocs hits = searcher.search(luceneQuery, null, maxResults);
        int found = hits.scoreDocs.length;

        LOGGER.info("Number of Objects found : " + found);

        MCRResults result = new MCRResults();

        for (int i = 0; i < found; i++) {
            // org.apache.lucene.document.Document doc = hits.doc(i);
            org.apache.lucene.document.Document doc = searcher.doc(hits.scoreDocs[i].doc);
            String id;

            id = doc.get("mcrid");
            LOGGER.debug("ID of MCRObject found: " + id);
            /*
             * TODO if (MCRAccessManager.checkReadAccess( id,
             * MCRSessionMgr.getCurrentSession()))
             */{
                MCRHit hit = new MCRHit(id);

                Enumeration fields = doc.fields();
                while (fields.hasMoreElements()) 
                {
                  Field field = (Field) fields.nextElement();
                  if ( field.isStored() && !"mcrid".equals(field.name()) )
                  {
                    MCRFieldDef fd = MCRFieldDef.getDef( field.name() );
                    MCRFieldValue fv = new MCRFieldValue( fd, field.stringValue() );
                    hit.addMetaData( fv );
                    if (fd.isSortable()) hit.addSortData( fv );
                  }
                }

                result.addHit(hit);
             } // MCRAccessManager
        }

        searcher.close();

        return result;
    }

    /**
     * interface implementation (visitor pattern) for condition types: on each
     * new type a xml-element will be added to an internal stack which holds the
     * number of children to process
     */
    public void visitType(Element element) {
    }

    /**
     * interface implementation (visitor pattern) for field type
     */
    public void visitQuery(MCRCondition entry) {
    }

    /**
     * method returns the string representation of given query
     */
    public String toString() {
        return luceneQuery.toString();
    }
}
