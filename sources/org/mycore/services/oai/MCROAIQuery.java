/**
 * $RCSfile: MCROAIQuery.java,v $
 * $Revision: 1.7 $ $Date: 2003/01/31 11:56:25 $
 *
 * This file is part of ** M y C o R e **
 * Visit our homepage at http://www.mycore.de/ for details.
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
 * along with this program, normally in the file license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 **/

package org.mycore.services.oai;

import java.util.List;

import org.jdom.Element;

/**
 * @author Werner Gresshoff
 *
 * @version $Revision: 1.7 $ $Date: 2003/01/31 11:56:25 $
 * 
 * This is an interface which encapsulates the functions needed for
 * the communication with the datastore. All functions which are
 * MyCoRe or Miless specific should be implemented here.
 */
public interface MCROAIQuery {
	
	/**
	 * Method exists. Checks if the given ID exists in the data repository
	 * @param id The ID to be checked
	 * @return boolean
	 */
	public boolean exists(String id);

	/**
	 * Method listSets. Gets a list of classificationId's and Labels for a given ID
	 * @param classificationId
	 * @param instance the Servletinstance
	 * @return List A list that contains an array of three Strings: the category id, 
	 * 				the label and a description
	 */
	public List listSets(String instance);
	
	/**
	 * Method listIdentifiers.Gets a list of identifiers
	 * @param set the category (if known) is in the first element
	 * @param from the date (if known) is in the first element
	 * @param until the date (if known) is in the first element
	 * @param instance the Servletinstance
	 * @return List A list that contains an array of three Strings: the identifier,
	 * 				a datestamp (modification date) and a string with a blank
	 * 				separated list of categories the element is classified in
	 */
	public List listIdentifiers(String[] set, String[] from, String[] until, String instance);

	/**
	 * Method getRecord. Gets a metadata record with the given <i>id</id>.
	 * @param id The id of the object.
	 * @param instance the Servletinstance
	 * @return List A list that contains an array of three Strings: the identifier,
	 * 				a datestamp (modification date) and a string with a blank
	 * 				separated list of categories the element is classified in
	 * 				and a JDOM element with the metadata of the record
	 */
	public List getRecord(String id, String instance);
	
	/**
	 * Method listRecords.Gets a list of metadata records
	 * @param set the category (if known) is in the first element
	 * @param from the date (if known) is in the first element
	 * @param until the date (if known) is in the first element
	 * @param instance the Servletinstance
	 * @return List A list that contains an array of three Strings: the identifier,
	 * 				a datestamp (modification date) and a string with a blank
	 * 				separated list of categories the element is classified in
	 */
	public List listRecords(String[] set, String[] from, String[] until, String instance);
		
}
