/*
 * 
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

import java.io.StringReader;

import java.text.ParseException;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.jdom.Element;
import org.mycore.common.MCRNormalizer;
import org.mycore.common.MCRUtils;
import org.mycore.services.fieldquery.MCRFieldDef;

/**
 * This class builds a Lucene Query from XML query (specified by Frank
 * L�tzenkirchen)
 * 
 * @author Harald Richter
 * 
 * @version $Revision$ $Date$
 * 
 */
public class MCRBuildLuceneQuery {
    private static final Logger LOGGER = Logger.getLogger(MCRBuildLuceneQuery.class);

    static {
        BooleanQuery.setMaxClauseCount(10000);
    }

    /**
     * Build Lucene Query from XML
     * 
     * @return Lucene Query
     * 
     */
    public static Query buildLuceneQuery(BooleanQuery r, boolean reqf, List<Element> f, Analyzer analyzer) throws Exception {
        for (int i = 0; i < f.size(); i++) {
            org.jdom.Element xEle = f.get(i);
            String name = xEle.getName();
            if ("boolean".equals(name))
                name = xEle.getAttributeValue("operator").toLowerCase();
            Query x = null;

            boolean reqfn = reqf;
            boolean prof = false;

            @SuppressWarnings("unchecked")
            List<Element> children = xEle.getChildren();
            if (name.equals("and")) {
                x = buildLuceneQuery(null, true, children, analyzer);
            } else if (name.equalsIgnoreCase("or")) {
                x = buildLuceneQuery(null, false, children, analyzer);
            } else if (name.equalsIgnoreCase("not")) {
                x = buildLuceneQuery(null, false, children, analyzer);
                reqfn = false; // javadoc lucene: It is an error to specify a
                // clause as both required and prohibited
                prof = true;
            } else if (name.equalsIgnoreCase("condition")) {
                String field = xEle.getAttributeValue("field", "");
                String operator = xEle.getAttributeValue("operator", "");
                String value = xEle.getAttributeValue("value", "");

                LOGGER.debug("field: " + field + " operator: " + operator + " value: " + value);

                String fieldtype = MCRFieldDef.getDef(field).getDataType();

                if ("name".equals(fieldtype)) {
                    fieldtype = "text";
                }
                
                if("index".equals(fieldtype)){
                	fieldtype="identifier";
                	value = MCRNormalizer.normalizeString(value, true);
                }
                
                x = handleCondition(field, operator, value, fieldtype, reqf, analyzer);
            }

            if (null != x) {
                if (null == r) {
                    r = new BooleanQuery();
                }

                BooleanClause.Occur occur = BooleanClause.Occur.MUST;

                if (reqfn && !prof)
                    ;
                else if (!reqfn && !prof)
                    occur = BooleanClause.Occur.SHOULD;
                else if (!reqfn && prof)
                    occur = BooleanClause.Occur.MUST_NOT;
                BooleanClause bq = new BooleanClause(x, occur);
                r.add(bq);
            }
        } // for

        return r;
    }

    private static Query handleCondition(String field, String operator, String value, String fieldtype, boolean reqf, Analyzer analyzer)
            throws Exception {
        if ("text".equals(fieldtype) && "contains".equals(operator)) {
            BooleanQuery bq = null;

            Term te;
            TermQuery tq = null;

            TokenStream ts = analyzer.tokenStream(field, new StringReader(value));

            while (ts.incrementToken()) {
                TermAttribute ta = (TermAttribute) ts.getAttribute(TermAttribute.class);
                te = new Term(field, ta.term());

                if ((null != tq) && (null == bq)) // not first token
                {
                    bq = new BooleanQuery();
                    if (reqf)
                        bq.add(tq, BooleanClause.Occur.MUST);
                    else
                        bq.add(tq, BooleanClause.Occur.SHOULD);
                }

                tq = new TermQuery(te);

                if (null != bq) {
                    if (reqf)
                        bq.add(tq, BooleanClause.Occur.MUST);
                    else
                        bq.add(tq, BooleanClause.Occur.SHOULD);
                }
            }

            if (null != bq) {
                return bq;
            }
            return tq;
        } else if (("text".equals(fieldtype) || "identifier".equals(fieldtype)) && "like".equals(operator)) {
            Term te;

            String help = value.endsWith("*") ? value.substring(0, value.length() - 1) : value;

            if ((-1 != help.indexOf("*")) || (-1 != help.indexOf("?"))) {
                LOGGER.debug("WILDCARD");

                te = new Term(field, value);
                return new WildcardQuery(te);
            }

            te = new Term(field, help);
            return new PrefixQuery(te);
        } else if ("text".equals(fieldtype) && ("phrase".equals(operator) || "=".equals(operator))) {
            Term te;
            PhraseQuery pq = new PhraseQuery();
            TokenStream ts = analyzer.tokenStream(field, new StringReader(value));

            while (ts.incrementToken()) {
                TermAttribute ta = (TermAttribute) ts.getAttribute(TermAttribute.class);
                te = new Term(field, ta.term());
                pq.add(te);
            }

            return pq;
        } else if ("text".equals(fieldtype) && "fuzzy".equals(operator)) // 1.9.05
        // future use
        {
            Term te;
            value = fixQuery(value);
            te = new Term(field, value);

            return new FuzzyQuery(te);
        } else if ("text".equals(fieldtype) && "range".equals(operator)) // 1.9.05
        // future use
        {
            String lower = null;
            String upper = null;
            TokenStream ts = analyzer.tokenStream(field, new StringReader(value));
            ts.incrementToken();

            TermAttribute ta = (TermAttribute) ts.getAttribute(TermAttribute.class);
            lower = ta.term();
            if (ts.incrementToken()) {
                ta = (TermAttribute) ts.getAttribute(TermAttribute.class);
                upper = ta.term();
            }

            return new TermRangeQuery(field, lower, upper, true, true);
        } else if ("date".equals(fieldtype) || "time".equals(fieldtype) || "timestamp".equals(fieldtype)) {
            return DateQuery2(field, operator, value);
        } else if ("identifier".equals(fieldtype) && "=".equals(operator)) {
            Term te = new Term(field, value);

            return new TermQuery(te);
        } else if ("boolean".equals(fieldtype)) {
            Term te = new Term(field, "true".equals(value) ? "1" : "0");

            return new TermQuery(te);
        } else if ("decimal".equals(fieldtype)) {
            return NumberQuery(field, "decimal", operator, Float.parseFloat(value));
        } else if ("integer".equals(fieldtype)) {
            return NumberQuery(field, "integer", operator, Long.parseLong(value));
        } else if ("text".equals(fieldtype) && "lucene".equals(operator)) // value
        // contains query for lucene, use query parser
        {
            QueryParser qp = new QueryParser(field, analyzer);
            Query query = qp.parse(fixQuery(value));

            LOGGER.debug("Lucene query: " + query.toString());

            return query;
        } else {
            LOGGER.info("Not supported, fieldtype: " + fieldtype + " operator: " + operator);
        }

        return null;
    }

    // code from Otis Gospodnetic http://www.jguru.com/faq/view.jsp?EID=538312
    // Question Are Wildcard, Prefix, and Fuzzy queries case sensitive?
    // Yes, unlike other types of Lucene queries, Wildcard, Prefix, and Fuzzy
    // queries are case sensitive. That is because those types of queries are
    // not passed through the Analyzer, which is the component that performs
    // operations such as stemming and lowercasing.
    public static String fixQuery(String aQuery) {
        aQuery = MCRUtils.replaceString(aQuery, "'", "\""); // handle phrase

        StringTokenizer _tokenizer = new StringTokenizer(aQuery, " \t\n\r", true);
        StringBuffer _fixedQuery = new StringBuffer(aQuery.length());
        boolean _inString = false;

        while (_tokenizer.hasMoreTokens()) {
            String _token = _tokenizer.nextToken();

            if ((!"NOT".equals(_token) && !"AND".equals(_token) && !"OR".equals(_token) && !"TO".equals(_token)) || _inString) {
                _fixedQuery.append(_token.toLowerCase());
            } else {
                _fixedQuery.append(_token);
            }

            int _nbQuotes = count(_token, "\""); // Count the "
            int _nbEscapedQuotes = count(_token, "\\\""); // Count the \"

            if (((_nbQuotes - _nbEscapedQuotes) % 2) != 0) {
                // there is an odd number of string delimiters
                _inString = !_inString;
            }
        }

        String qu = _fixedQuery.toString();
        qu = MCRUtils.replaceString(qu, "�", "a");
        qu = MCRUtils.replaceString(qu, "�", "o");
        qu = MCRUtils.replaceString(qu, "�", "u");
        qu = MCRUtils.replaceString(qu, "�", "ss");

        return qu;
    }

    private static int count(String aSourceString, String aCountString) {
        int fromIndex = 0;
        int foundIndex = 0;
        int count = 0;

        while ((foundIndex = aSourceString.indexOf(aCountString, fromIndex)) > -1) {
            count++;
            fromIndex = ++foundIndex;
        }

        return count;
    }

    /***************************************************************************
     * NumberQuery ()
     **************************************************************************/
    private static Query NumberQuery(String fieldname, String type, String Op, Number value) {
        if (value == null) {
            return null;
        }
        if (type.equals("decimal")) {
            float valueNumber = value.floatValue();
            float lower = 0.0f - Float.MAX_VALUE;
            float upper = Float.MAX_VALUE;
            if (Op.equals(">") || Op.equals(">=")) {
                lower = valueNumber;
                return NumericRangeQuery.newFloatRange(fieldname, lower, upper, Op.length() == 2, true);
            }
            if (Op.equals("<") || Op.equals("<=")) {
                upper = valueNumber;
                return NumericRangeQuery.newFloatRange(fieldname, lower, upper, true, Op.length() == 2);
            }
            if (Op.equals("=")) {
                return NumericRangeQuery.newFloatRange(fieldname, valueNumber, valueNumber, true, true);
            }
        }
        if (type.equals("integer")) {
            long valueNumber = value.longValue();
            long lower = Long.MIN_VALUE;
            long upper = Long.MAX_VALUE;
            if (Op.equals(">") || Op.equals(">=")) {
                lower = valueNumber;
                return NumericRangeQuery.newLongRange(fieldname, lower, upper, Op.length() == 2, true);
            }
            if (Op.equals("<") || Op.equals("<=")) {
                upper = valueNumber;
                return NumericRangeQuery.newLongRange(fieldname, lower, upper, true, Op.length() == 2);
            }
            if (Op.equals("=")) {
                return NumericRangeQuery.newLongRange(fieldname, valueNumber, valueNumber, true, true);
            }
        }
        LOGGER.info("Invalid operator for Number: " + Op);

        return null;
    }

    /***************************************************************************
     * DateQuery2 ()
     * @throws ParseException 
     **************************************************************************/
    private static Query DateQuery2(String fieldname, String Op, String value) throws ParseException {
        long numberValue = MCRLuceneTools.getLongValue(value);
        return NumberQuery(fieldname, "integer", Op, numberValue);
    }
}
