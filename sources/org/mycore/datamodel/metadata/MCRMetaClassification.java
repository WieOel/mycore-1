/**
 * $RCSfile$
 * $Revision$ $Date$
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

package mycore.datamodel;

import mycore.common.MCRException;
import mycore.classifications.MCRCategoryItem;
import mycore.classifications.MCRClassificationItem;

/**
 * This class implements all method for handling with the MCRMetaClassification
 *  part of a metadata object. The MCRMetaClassification class present a 
 * link to a category of a classification.
 * <p>
 * &lt;tag class="MCRMetaClassification" heritable="..."&gt;<br>
 * &lt;subtag classid="..." categid="..." /&gt;<br>
 * &lt;/tag&gt;<br>
 *
 * @author Jens Kupferschmidt
 * @version $Revision$ $Date$
 **/
final public class MCRMetaClassification extends MCRMetaDefault 
  implements MCRMetaInterface 
{

// MCRMetaClassification data
private String classid;
private String categid;

/**
 * This is the constructor. <br>
 * The language element was set to <b>en</b>.
 * The boolean value was set to false.
 */
public MCRMetaClassification()
  {
  super();
  classid = "";
  categid = "";
  }

/**
 * This is the constructor. <br>
 * The language element was set to <b>en</b>.
 * The subtag element was set
 * to the value of <em>set_subtag<em>. If the value of <em>set_subtag</em>
 * is null or empty an exception was throwed. The type element was set to
 * the value of <em>set_type<em>, if it is null, an empty string was set
 * to the type element. The boolean string <em>set_value<em>
 * was set to a boolean element, if it is null, false was set.
 *
 * @param set_datapart     the global part of the elements like 'metadata'
 *                         or 'service'
 * @param set_subtag       the name of the subtag
 * @param set_classid      the classification ID
 * @param set_categid      the category ID
 * @exception MCRException if the set_subtag value, the set_classid value or
 * the set_categid are null or empty
 */
public MCRMetaClassification(String set_datapart, String set_subtag, 
  String default_lang, String set_classid, String set_categid) 
  throws MCRException
  {
  super(set_datapart,set_subtag,"en","");
  if ((set_classid==null) || ((set_classid=set_classid.trim()).length()==0)) {
    throw new MCRException("The classid is not empty."); }
  if ((set_categid==null) || ((set_categid=set_categid.trim()).length()==0)) {
    throw new MCRException("The categid is not empty."); }
  classid = set_classid;
  categid = set_categid;
  }

/**
 * This method set values of classid and categid.
 *
 * @param set_classid      the classification ID
 * @param set_categid      the category ID
 **/
public final void setValue(String set_classid, String set_categid)
  {
  if ((set_classid==null) || ((set_classid=set_classid.trim()).length()==0)) {
    throw new MCRException("The classid is not empty."); }
  if ((set_categid==null) || ((set_categid=set_categid.trim()).length()==0)) {
    throw new MCRException("The categid is not empty."); }
  classid = set_classid;
  categid = set_categid;
  }

/**
 * This method read the XML input stream part from a DOM part for the
 * metadata of the document.
 *
 * @param element a relevant JDOM element for the metadata
 **/
public final void setFromDOM(org.jdom.Element element)
  {
  super.setFromDOM(element);
  String set_classid = element.getAttributeValue("classid");
  if ((set_classid==null) || ((set_classid=set_classid.trim()).length()==0)) {
    throw new MCRException("The classid is not empty."); }
  String set_categid = element.getAttributeValue("categid");
  if ((set_categid==null) || ((set_categid=set_categid.trim()).length()==0)) {
    throw new MCRException("The categid is not empty."); }
  classid = set_classid;
  categid = set_categid;
  }

/**
 * This method create a XML stream for all data in this class, defined
 * by the MyCoRe XML MCRMetaClassification definition for the given subtag.
 *
 * @exception MCRException if the content of this class is not valid
 * @return a JDOM Element with the XML MCRClassification part
 **/
public final org.jdom.Element createXML() throws MCRException
  {
  if (!isValid()) {
    debug();
    throw new MCRException("The content is not valid."); }
  org.jdom.Element elm = new org.jdom.Element(subtag);
  elm.setAttribute("classid",classid); 
  elm.setAttribute("categid",categid); 
  return elm;
  }

/**
 * This methode create a typed content list for all data in this instance.
 *
 * @param parametric true if the data should parametric searchable
 * @param textsearch true if the data should text searchable
 * @exception MCRException if the content of this class is not valid
 * @return a MCRTypedContent with the data of the MCRObject data
 **/
public final MCRTypedContent createTypedContent(boolean parametric,
  boolean textsearch) throws MCRException
  {
  if (!isValid()) {
    debug();
    throw new MCRException("The content is not valid."); }
  MCRTypedContent tc = new MCRTypedContent();
  tc.addTagElement(tc.TYPE_SUBTAG,subtag.toUpperCase());
  tc.addClassElement(classid,parametric,false);
  tc.addCategElement(categid,parametric,false);
  return tc;
  }

/**
 * This method check the validation of the content of this class.
 * The method returns <em>true</em> if
 * <ul>
 * <li> the subtag is not null or empty
 * </ul>
 * otherwise the method return <em>false</em>
 *
 * @return a boolean value
 **/
public final boolean isValid()
  {
  if (!super.isValid()) { return false; }
  try {
    MCRClassificationItem cl = 
      MCRClassificationItem.getClassificationItem(classid);
    if (cl==null) { return false; }
    MCRCategoryItem ci = cl.getCategoryItem(categid);
    if (ci==null) { return false; }
    }
  catch (Exception e) { return false; }
  return true;
  }

/**
 * This method print all data content from the MCRMetaClassification class.
 **/
public final void debug()
  {
  System.out.println("MCRMetaClassification debug start:");
  super.debug();
  System.out.println("ClassificationID = "+classid);
  System.out.println("CategoryID = "+categid);
  System.out.println("MCRMetaClassification debug end"+NL);
  }

}

