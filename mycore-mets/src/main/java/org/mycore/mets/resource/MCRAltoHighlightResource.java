package org.mycore.mets.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.mycore.frontend.jersey.MCRJerseyUtil;
import org.mycore.solr.MCRSolrClientFactory;

/**
 * <p>Solr resource for alto text highlighting.</p>
 *
 * <b>Example: rsc/alto/highlight/mycore_derivate_00000001?q="jena paradies"</b>
 *
 * <pre>
 * [
 *     {
 *         "id": "mycore_derivate_00000001:/alto/alto_1.xml",
 *         "hits": [
 *              {
 *                  "hl": "Es ist schön in <em>Jena</em> <em>Paradies</em>.",
 *                  "positions":[
 *                      {
 *                          "content": "Jena",
 *                          "xpos": 1566,
 *                          "vpos": 1100,
 *                          "width": 105,
 *                          "height": 44
 *                      },
 *                      {
 *                         "content": "Paradies",
 *                          "xpos": 1671,
 *                          "vpos": 1100,
 *                          "width": 185,
 *                          "height": 47
 *                      }
 *                  ]
 *              },
 *              {
 *                  "hl": ...,
 *                  "positions": [
 *                      ...
 *                  ]
 *              }
 *              ...
 *         ]
 *     },
 *
 *     {
 *         "id": "alto/alto_2.xml",
 *         ...
 *     },
 *
 *     ...
 *
 * ]
 * </pre>
 */
@Path("/alto/highlight")
public class MCRAltoHighlightResource {

    @GET
    @Path("{derivateId}")
    @Produces(MCRJerseyUtil.APPLICATION_JSON_UTF8)
    public Response query(@PathParam("derivateId") String derivateId, @QueryParam("q") String query) {
        ModifiableSolrParams p = new ModifiableSolrParams();
        p.set("q", buildQuery(query));
        p.add("fq", "derivateID:" + derivateId);
        p.set("fl", "id");
        p.set("rows", 9999);
        p.set("hl", "on");
        p.set("hl.method", "unified");
        p.set("hl.fl", "alto_content,alto_words");
        p.set("hl.snippets", 9999);
        p.set("hl.bs.type", "WORD");
        p.set("hl.fragsize", 70);
        try {
            QueryResponse solrResponse = MCRSolrClientFactory.getSolrClient().query(p);
            JsonArray response = buildQueryResponse(solrResponse.getHighlighting());
            return Response.ok().entity(new Gson().toJson(response)).build();
        } catch (Exception exc) {
            throw new WebApplicationException("Unable to highlight '" + query + "' of derivate " + derivateId, exc,
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    protected String buildQuery(String query) {
        String fixedQuery = query.trim().replaceAll("\\s\\s", " ");
        List<String> words = new ArrayList<>();
        StringBuilder currentWord = new StringBuilder();
        boolean split = true;
        for (int i = 0; i < fixedQuery.length(); i++) {
            char c = fixedQuery.charAt(i);
            if (c == ' ' && split) {
                addWord(words, currentWord.toString());
                currentWord = new StringBuilder();
            } else if (c == '"') {
                split = !split;
            } else {
                currentWord.append(c);
            }
        }
        addWord(words, currentWord.toString());
        return String.join(" ", words);
    }

    protected void addWord(List<String> words, String word) {
        if (word.isEmpty()) {
            return;
        }
        String field = "alto_content:";
        words.add(word.contains(" ") ? field + "\"" + word + "\"" : field + word);
    }

    private JsonArray buildQueryResponse(Map<String, Map<String, List<String>>> highlighting) {
        JsonArray response = new JsonArray();
        highlighting.forEach((id, fields) -> buildPageObject(id, fields).ifPresent(response::add));
        return response;
    }

    private Optional<JsonObject> buildPageObject(String id, Map<String, List<String>> fields) {
        JsonObject page = new JsonObject();
        JsonArray hits = new JsonArray();
        page.addProperty("id", toMCRPathId(id));
        page.add("hits", hits);
        List<String> altoContentList = fields.get("alto_content");
        List<String> altoWords = fields.get("alto_words");
        if (altoContentList.size() == 0 || altoWords.size() == 0) {
            return Optional.empty();
        }
        int wordIndex = 0;
        for (String altoContent : altoContentList) {
            JsonObject hit = new JsonObject();
            hit.addProperty("hl", altoContent);
            JsonArray positions = new JsonArray();
            int wordCount = StringUtils.countMatches(altoContent, "<em>");
            for (int i = wordIndex; i < wordIndex + wordCount; i++) {
                positions.add(buildPositionObject(altoWords.get(i)));
            }
            wordIndex += wordCount;
            hit.add("positions", positions);
            hits.add(hit);
        }
        return Optional.of(page);
    }

    private String toMCRPathId(String ifsId) {
        return ifsId.replaceFirst("ifs:/", "");
    }

    private JsonObject buildPositionObject(String altoWord) {
        JsonObject positionObject = new JsonObject();
        String plainWord = altoWord.replaceAll("<em>|</em>", "");
        String[] split = plainWord.split("\\|");
        positionObject.addProperty("content", split[0]);
        positionObject.addProperty("xpos", Integer.parseInt(split[1]));
        positionObject.addProperty("vpos", Integer.parseInt(split[2]));
        positionObject.addProperty("width", Integer.parseInt(split[3]));
        positionObject.addProperty("height", Integer.parseInt(split[4]));
        return positionObject;
    }

}
