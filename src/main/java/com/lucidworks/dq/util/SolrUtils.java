package com.lucidworks.dq.util;

import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;

public class SolrUtils {


  // TODO: get ID field from server
  static String ID_FIELD = "id";
  // ^--ID also assumed to be a string
  static int ALL_ROWS = Integer.MAX_VALUE;
  
  static String DEFAULT_HOST = "localhost";
  static int DEFAULT_PORT = 8983;

  // Other constant fields at bottom after main()

  // Convenience Factory Methods
  // -------------------------------------------------------------------

  // Construct from:
  // Nothing -> localhost:8983 (no collection, but often collection1)
  // server url as String or URL
  // host + port (as int or String)
  // host + port (int or String) + collection name
  
  public static HttpSolrServer getServer() {
	  return getServer( DEFAULT_HOST, DEFAULT_PORT );
  }
  public static HttpSolrServer getServer( String serverUrl ) {
	  return new HttpSolrServer( serverUrl );
  }
  public static HttpSolrServer getServer( URL serverUrl ) {
	  return getServer( serverUrl.toExternalForm()  );
  }
  public static HttpSolrServer getServer( String host, int port ) {
	  return getServer( host, ""+port );
  }
  public static HttpSolrServer getServer( String host, int port, String collection ) {
	  return getServer( host, ""+port, collection );
  }
  public static HttpSolrServer getServer( String host, String port ) {
	  String url = "http://" + host + ":" + port + "/solr";
	  return getServer( url );
  }
  public static HttpSolrServer getServer( String host, String port, String collection ) {
	  String url = "http://" + host + ":" + port + "/solr/" + collection;
	  return getServer( url );
  }
  
  
  // Basic Queries and Stas
  // -------------------------------

  // Alias
  public static Set<String> getDeclaredFieldNames( HttpSolrServer server ) throws SolrServerException {
	  return getAllSchemaFieldNames( server );
  }
  // Makes multiple calls to server
  public static Set<String> getUnusedDeclaredFieldNames( HttpSolrServer server ) throws SolrServerException {
    Set<String> declaredFields = getDeclaredFieldNames( server );
    Set<String> actualFields = getActualFieldNames( server );
    return SetUtils.inBOnly_destructive( actualFields, declaredFields );
  }
  // Makes multiple calls to server
  public static Set<String> getAllDeclaredAndActualFieldNames( HttpSolrServer server ) throws SolrServerException {
    Set<String> declaredFields = getDeclaredFieldNames( server );
    Set<String> actualFields = getActualFieldNames( server );
    return SetUtils.union_destructive( declaredFields, actualFields );
  }
  // http://localhost:8985/solr/collection1/admin/luke
  // TODO: Find out if this will include fields that are Stored but NOT Indexed
  public static Set<String> getActualFieldNames( HttpSolrServer server ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/admin/luke");
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    SimpleOrderedMap fields = (SimpleOrderedMap) res2.get("fields");
    for ( int i=0; i<fields.size(); i++ ) {
      String name = fields.getName( i );
      out.add( name );
    }
    // System.out.println( "Luke Fields = " + fields );
//    for ( Object field : fields ) {
//       System.out.println( "Field = " + field );
//    }
    // out.addAll( fields.iterator())
	return out;
  }
  public static Set<String> _getActualFieldNames( HttpSolrServer server ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
    // Look at the first real document
    SolrQuery query = new SolrQuery( "*:*" );
    query.addField( "*" );
    query.setRows( 1 );
    QueryResponse res = server.query( query );
	SolrDocumentList docs = res.getResults();
	if ( ! docs.isEmpty() ) {
	  SolrDocument firstDoc = docs.get(0);
	  out.addAll( firstDoc.getFieldNames() );
	}
	return out;
  }
  // Alias
  public static Set<String> getDeclaredDynamicFieldPatterns( HttpSolrServer server ) throws SolrServerException {
    return getAllDynamicFieldPatterns( server );
  }
  // Makes multiple calls to server
  public static Set<String> getActualDynamicFieldNames( HttpSolrServer server ) throws SolrServerException {
    Set<String> declaredFields = getDeclaredFieldNames( server );
    Set<String> actualFields = getActualFieldNames( server );
    return SetUtils.inAOnly_destructive( actualFields, declaredFields );
  }

  // TODO: lookup actual ID field via getUniqueKeyFieldName / getIdFieldName
  public static Set<String> getAllIds( HttpSolrServer server ) throws SolrServerException {
	  Set<String> out = new LinkedHashSet<>();
	  SolrQuery q = new SolrQuery( "*:*" );
	  q.addField( ID_FIELD );
	  q.setRows( ALL_ROWS );
	  QueryResponse res = server.query( q );
	  for ( SolrDocument doc : res.getResults() ) {
		  String id = (String) doc.get( ID_FIELD );
		  out.add( id );
	  }
	  return out;
  }

  public static long getTotalDocCount( HttpSolrServer server ) throws SolrServerException {
	  return getDocCountForQuery( server, "*:*" );
  }
  public static long getDocCountForField( HttpSolrServer server, String fieldName ) throws SolrServerException {
	  // NullPointerException for location
	  // com.spatial4j.core.io.ParseUtils.parsePoint(ParseUtils.java:42)
	  String queryStr = fieldName + ":[* TO *]";
	  try {
		  return getDocCountForQuery( server, queryStr );
	  }
	  catch( Exception e ) {
		  // TODO: will this wildcard expand to all terms?
		  queryStr = fieldName + ":*";
		  return getDocCountForQuery( server, queryStr );		  
	  }
  }
  public static long getDocCountForQuery( HttpSolrServer server, String query ) throws SolrServerException {
	  SolrQuery q = new SolrQuery( query );
	  return getDocCountForQuery( server, q );
  }
  // TODO: lookup actual ID field via getUniqueKeyFieldName / getIdFieldName
  public static long getDocCountForQuery( HttpSolrServer server, SolrQuery query ) throws SolrServerException {
	  query.addField( ID_FIELD );    // Minimize data
	  query.setRows( 0 );            // Minimize data
	  QueryResponse res = server.query( query );
	  SolrDocumentList docs = res.getResults();
	  long count = docs.getNumFound();
	  return count;
  }

  // http://localhost:8985/solr/collection1/terms
  // TODO: not returning term counts for now, not really what we're looking at
  public static Set<String> getAllTermsForField( HttpSolrServer server, String fieldName ) throws SolrServerException {
	return getTermsForField( server, fieldName, -1 );
  }
  // By default we'll get the top 10
  public static Set<String> getTermsForField( HttpSolrServer server, String fieldName ) throws SolrServerException {
	return getTermsForField( server, fieldName, null );
  }
  public static Set<String> getTermsForField( HttpSolrServer server, String fieldName, Integer optLimit ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/terms");
	q.addTermsField( fieldName );
	if ( null!=optLimit ) {
		q.setTermsLimit( optLimit );
	}
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    SimpleOrderedMap res3 = (SimpleOrderedMap) res2.get("terms");
    NamedList terms = (NamedList) res3.get( fieldName );
    for ( int i=0; i<terms.size(); i++ ) {
      String name = terms.getName( i );
      out.add( name );
    }
	return out;
  }
  // Get multiple fields at once
  public static Map<String,Map<String,Long>> getAllTermsForFields_ViaTermsRequest( HttpSolrServer server, Set<String> fieldNames ) throws SolrServerException {
	return getTermsForFields_ViaTermsRequest( server, fieldNames, -1 );
  }
  // By default gets the top 10
  public static Map<String,Map<String,Long>> getTermsForFields_ViaTermsRequest( HttpSolrServer server, Set<String> fieldNames ) throws SolrServerException {
	return getTermsForFields_ViaTermsRequest( server, fieldNames, null );
  }
  // Includes deleted Docs
  public static Map<String,Map<String,Long>> getTermsForFields_ViaTermsRequest( HttpSolrServer server, Set<String> fieldNames, Integer optLimit ) throws SolrServerException {
	Map<String,Map<String,Long>> out = new LinkedHashMap<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/terms");
	for ( String fieldName : fieldNames ) {
	  q.addTermsField( fieldName );
	}
	if ( null!=optLimit ) {
	  q.setTermsLimit( optLimit );
	}
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    SimpleOrderedMap res3 = (SimpleOrderedMap) res2.get("terms");
	for ( String fieldName : fieldNames ) {
      NamedList terms = (NamedList) res3.get( fieldName );
      Map<String,Long> termVector = new LinkedHashMap<>();
	  for ( int i=0; i<terms.size(); i++ ) {
	    String name = terms.getName( i );
	    Integer count = (Integer) terms.getVal( i );
	    termVector.put( name, new Long(count) );
	  }
      out.put( fieldName, termVector );
	}
    return out;
  }

  // Returns only active documents
  // BUT slow for large sets
  // http://localhost:8983/solr/collection1/select?q=*:*&rows=0&facet=true&fl=id&facet.limit=-1&facet.field=class&facet.field=type&rows=0
  public static Map<String,Map<String,Long>> getTermsForFields_ViaSearchFacets( HttpSolrServer server, Set<String> fieldNames, Integer optLimit ) throws SolrServerException {
	Map<String,Map<String,Long>> out = new LinkedHashMap<>();
	SolrQuery q = new SolrQuery( "*:*" );
	q.addField( ID_FIELD );    // Minimize data
	q.setRows( 0 );            // Minimize data
	for ( String fieldName : fieldNames ) {
	  q.addFacetField( fieldName );
	}
	if ( null!=optLimit ) {
	  q.setFacetLimit( optLimit );
	}
	QueryResponse res = server.query( q );
	List<FacetField> facets = res.getFacetFields();
	// Foreach Field
	for ( FacetField facet : facets ) {
      Map<String,Long> termVector = new LinkedHashMap<>();
      String fieldName = facet.getName();
      Integer facetValuesCount = (Integer) facet.getValueCount();
      List<Count> vals = facet.getValues();
      System.out.println( fieldName + " has " + facetValuesCount + " entries" );
	  for ( Count val : facet.getValues() ) {
		String term = val.getName();
		// seems to always return 0 ?
		long termCount = val.getCount();
		termVector.put( term, new Long(termCount) );
		// FacetField ffield = val.getFacetField();
		// Class<? extends Count> fclass = val.getClass();
		// String filter = val.getAsFilterQuery();
	  }
	  out.put( fieldName, termVector );
	}
	return out;
  }
  
  
  // Info From REST API Calls
  // -------------------------------------------------------------------------------

  // https://cwiki.apache.org/confluence/display/solr/Schema+API
  // TODO: These are also in SchemaFromRestAdHock, should they also be here?
  // - Maybe handy to still have them here for quick lookup (vs. deailed reports)
  // - Ad-Hock can get some info that fancier shema class can't (at this time)
  //   Eg: getSimilarityModelClassName, getDefaultOperator

  public static float getSchemaVersion( HttpSolrServer server ) throws SolrServerException {
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/version"); 
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      float version = (float) res2.get("version");
      // float version = (float) res.getResponse().get("version");
      return version;
  }
  public static String getSchemaName( HttpSolrServer server ) throws SolrServerException {
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/name"); 
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      String name = (String) res2.get("name");
      // float version = (float) res.getResponse().get("version");
      return name;
  }
  // Alias
  // Common Name
  public static String getIdFieldName( HttpSolrServer server ) throws SolrServerException {
	  return getUniqueKeyFieldName( server );
  }
  // REST Name
  public static String getUniqueKeyFieldName( HttpSolrServer server ) throws SolrServerException {
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/uniquekey");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      String key = (String) res2.get("uniqueKey");
      // float version = (float) res.getResponse().get("version");
      return key;
  }
  public static String getSimilarityModelClassName( HttpSolrServer server ) throws SolrServerException {
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/similarity");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      NamedList<Object> sim = (NamedList<Object>) res2.get("similarity");
      String className = (String) sim.get("class");
      // float version = (float) res.getResponse().get("version");
      // return sim;
      return className;
  }
  public static String getDefaultOperator( HttpSolrServer server ) throws SolrServerException {
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/solrqueryparser/defaultoperator");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      String op = (String) res2.get("defaultOperator");
      // float version = (float) res.getResponse().get("version");
      return op;
  }
 
  public static Set<String> getAllSchemaFieldNames( HttpSolrServer server ) throws SolrServerException {
	  Set<String> out = new LinkedHashSet<>();
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/fields");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("fields");
      //System.out.println( "fields=" + fields );
      for ( SimpleOrderedMap f : fields ) {
    	  //System.out.println( "f=" + f );
    	  String name = (String)f.get( "name" );
    	  out.add( name );
      }
      return out;
  }
  public static Set<String> getAllDynamicFieldPatterns( HttpSolrServer server ) throws SolrServerException {
	  Set<String> out = new LinkedHashSet<>();
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/dynamicfields");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("dynamicFields");
      // System.out.println( "fields=" + fields );
      for ( SimpleOrderedMap f : fields ) {
    	  // System.out.println( "f=" + f );
    	  String name = (String)f.get( "name" );
    	  out.add( name );
      }
      return out;
  }
  public static Set<String> getAllFieldTypeNames( HttpSolrServer server ) throws SolrServerException {
	  Set<String> out = new LinkedHashSet<>();
	  SolrQuery q = new SolrQuery();
	  q.setRequestHandler("/schema/fieldtypes");
      QueryResponse res = server.query( q );
      NamedList<Object> res2 = res.getResponse();
      Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("fieldTypes");
      // System.out.println( "fields=" + fields );
      for ( SimpleOrderedMap f : fields ) {
    	  // System.out.println( "f=" + f );
    	  String name = (String)f.get( "name" );
    	  out.add( name );
      }
      return out;
  }

  public static Set<String> getAllCopyFieldSourceNames( HttpSolrServer server ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/schema/copyfields");
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("copyFields");
    // System.out.println( "fields=" + fields );
    for ( SimpleOrderedMap f : fields ) {
      // System.out.println( "f=" + f );
      String name = (String)f.get( "source" );
      out.add( name );
    }
    return out;
  }
  public static Set<String> getAllCopyFieldDestinationNames( HttpSolrServer server ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/schema/copyfields");
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("copyFields");
    // System.out.println( "fields=" + fields );
    for ( SimpleOrderedMap f : fields ) {
      // System.out.println( "f=" + f );
      String name = (String)f.get( "dest" );
      out.add( name );
    }
    return out;
  }
  public static Set<String> getCopyFieldDestinationsForSource( HttpSolrServer server, String sourceName ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/schema/copyfields");
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("copyFields");
    // System.out.println( "fields=" + fields );
    for ( SimpleOrderedMap f : fields ) {
      // System.out.println( "f=" + f );
      String source = (String)f.get( "source" );
      String dest = (String)f.get( "dest" );
      if ( source.equals(sourceName) ) {
    	  out.add( dest );
      }
    }
    return out;
  }
  public static Set<String> getCopyFieldSourcesForDestination( HttpSolrServer server, String sourceName ) throws SolrServerException {
	Set<String> out = new LinkedHashSet<>();
	SolrQuery q = new SolrQuery();
	q.setRequestHandler("/schema/copyfields");
    QueryResponse res = server.query( q );
    NamedList<Object> res2 = res.getResponse();
    Collection<SimpleOrderedMap> fields = (Collection<SimpleOrderedMap>)res2.get("copyFields");
    // System.out.println( "fields=" + fields );
    for ( SimpleOrderedMap f : fields ) {
      // System.out.println( "f=" + f );
      String source = (String)f.get( "source" );
      String dest = (String)f.get( "dest" );
      if ( dest.equals(sourceName) ) {
    	  out.add( source );
      }
    }
    return out;
  }


  public static void main( String[] argv ) throws SolrServerException {
	String host = "localhost";
	int port = 8983;
	String coll = "demo_shard1_replica1";
	// int port = 8985;
	// String coll = "collection1";
	HttpSolrServer s = getServer( host, port, coll );

	// String fieldName = "name";
	String fieldName = "color";
	// color, condition, department, format, genre, manufacturer, mpaaRating
	// class, subclass, studio, softwareGrade, mpaaRating, albumLabel
	// categoryIds, categoryNames
	Set<String> terms = getAllTermsForField( s, fieldName );
	System.out.println( "Field " + fieldName + " has " + terms.size() + " terms" );
//    System.out.println( "Terms for field " + fieldName + " = " + terms );
//	Map<String,Set<String>> terms = getTermsForFields( s, getActualFieldNames(s) );
//    System.out.println( "Terms = " + terms );

//	Set<String> declFields = getDeclaredFieldNames( s );
//    System.out.println( "Declared Fields = " + declFields );
//	Set<String> patterns = getDeclaredDynamicFieldPatterns( s );
//    System.out.println( "Dynamic Patterns = " + patterns );
//    Set<String> dynFields = getActualDynamicFieldNames( s );
//    System.out.println( "Dynamic fields = " + dynFields );
//
//    // Experiment
//    Set<String> declaredFields = getDeclaredFieldNames( s );
//    Set<String> actualFields = getActualFieldNames( s );
//    Set<String> schemaOnlyFields = SetUtils.inBOnly_destructive( actualFields, declaredFields );
//    System.out.println( "Experiment: Schema-Only fields = " + schemaOnlyFields );

//	HttpSolrServer s1 = new HttpSolrServer( URL1 );
//    HttpSolrServer s2 = new HttpSolrServer( URL2 );
//
//    float versA = getSchemaVersion( s1 );
//    // float versB = getSchemaVersion( s2 );
//    System.out.println( "Schema Version A: " + versA );
//    String nameA = getSchemaName( s1 );
//    System.out.println( "Schema Name A: " + nameA );
//    String keyA = getUniqueKeyFieldName( s1 );
//    System.out.println( "Key Field A: " + keyA );
//    String defOpA = getDefaultOperator( s1 );
//    System.out.println( "Default Operator A: " + defOpA );
//    String simA = getSimilarityModelClassName( s1 );
//    System.out.println( "Similarity Class Name A: " + simA + ", is-a " + simA.getClass().getName() );
//    
//    // getAllSchemaFieldNames
//    Set<String> fieldsA = getAllSchemaFieldNames( s1 );
//    // Set<String> fieldsB = getAllSchemaFieldNames( s2 );
//    System.out.println( "Fields A: " + fieldsA );
//    // System.out.println( "Feilds B: " + fieldsB );
//
//    Set<String> dynFieldsA = getAllDynamicFieldPatterns( s1 );
//    // Set<String> dynFieldsB = getAllDynamicFieldPatterns( s2 );
//    System.out.println( "Dynamic field Patterns A: " + dynFieldsA );
//    // System.out.println( "Dynamic feild Patterns B: " + dynFieldsB );
//  
//    // getAllFieldTypeNames
//    Set<String> typeNamesA = getAllFieldTypeNames( s1 );
//    // Set<String> typeNamesB = getAllFieldTypeNames( s2 );
//    System.out.println( "Types A: " + typeNamesA );
//    // System.out.println( "Types B: " + typeNamesB );
//    
//    // getAllCopyFieldSourceNames
//    Set<String> sourceNamesA = getAllCopyFieldSourceNames( s1 );
//    // Set<String> sourceNamesB = getAllCopyFieldSourceNames( s2 );
//    System.out.println( "Copy Sources A: " + sourceNamesA );
//    // System.out.println( "Copy Sources B: " + sourceNamesB );
//
//    // getCopyFieldDestinationsForSource
//    for ( String source : sourceNamesA ) {
//    	Set<String> tmpDests = getCopyFieldDestinationsForSource( s1, source );
//    	System.out.println( "\tFrom: '"+ source + "' To " + tmpDests );
//    }
//
//    // getAllCopyFieldDestinationNames
//    Set<String> destNamesA = getAllCopyFieldDestinationNames( s1 );
//    // Set<String> destNamesB = getAllCopyFieldDestinationNames( s2 );
//    System.out.println( "Copy Destinations A: " + destNamesA );
//    // System.out.println( "Copy Destinations B: " + destNamesB );
//
//    // getCopyFieldSourcesForDestination
//    for ( String dest : destNamesA ) {
//    	Set<String> tmpSrcs = getCopyFieldSourcesForDestination( s1, dest );
//    	System.out.println( "\tDest: '"+ dest + "' From " + tmpSrcs );
//    }

  }

  
  static String HOST0 = "localhost";
  static String PORT0 = "8983";
  static String COLL0 = "demo_shard1_replica1";
  static String URL0 = "http://" + HOST0 + ":" + PORT0 + "/solr/" + COLL0;
	  // + "/select?q=*:*&rows=" + ROWS + "&fl=id&wt=json&indent=on"

  static String HOST1 = "localhost";
  static String PORT1 = "8984"; // "8983";
  static String COLL1 = "collection1";
  static String URL1 = "http://" + HOST1 + ":" + PORT1 + "/solr/" + COLL1;

  static String HOST2 = "localhost";
  static String PORT2 = "8985"; // "8983";
  static String COLL2 = "collection1";
  static String URL2 = "http://" + HOST1 + ":" + PORT2 + "/solr/" + COLL2;

}