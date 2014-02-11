package com.lucidworks.dq.data;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
// import com.sun.tools.javac.util.List;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;









// x import org.apache.commons.lang.CharUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

import com.lucidworks.dq.util.SetUtils;
import com.lucidworks.dq.util.SolrUtils;
import com.lucidworks.dq.util.StatsUtils;
import com.lucidworks.dq.util.CharUtils;

// Using composition, not inheritence, from EmptyFieldStats
public class TermCodepointStats {

  static String HOST = "localhost";
  static int PORT = 8983;
  // static String COLL = "collection1";
  static String COLL = "demo_shard1_replica1";
  
  // Object we're leveraging
  EmptyFieldStats fieldStats;

  // Fields we care about, if any
  Set<String> targetFieldNames;

  // fieldName -> term -> count
  Map<String, Map<String,Long>> rawTermsMap;

  // fieldName -> classifierTuple -> terms
  // Map<String,Map<Set<String>,Set<String>>> categorizedTerms;
  // Map<String,Map<List<String>,Set<String>>> categorizedTerms;
  Map<  String,  Map<String, Set<String>>  > categorizedTerms;
  
  public TermCodepointStats( HttpSolrServer server ) throws SolrServerException {
	this( server, null );
  }
  public TermCodepointStats( HttpSolrServer server, Collection<String> targetFields ) throws SolrServerException {
	// this.server = server;
	this.fieldStats = new EmptyFieldStats( server );
	if ( null == targetFields ) {
	  targetFields = fieldStats.getFieldsWithIndexedValues();
	}
	this.targetFieldNames = new LinkedHashSet<>( targetFields );
	resetData( false );
	// TODO: should defer these?  Nice sanity check...
	// Don't chain the helper class since tey just did it
	doAllTabulations( false );
	  
  }

  public Set<String> getTargetFieldNames() {
    return targetFieldNames;
  }

  // Passthroughs to Helper class
  public long getTotalDocCount() {
	return fieldStats.getTotalDocCount();
  }
  public Set<String> getAllFieldNames() {
	return fieldStats.getAllFieldNames();
  }
  public Set<String> _getFieldsWithIndexedValues() {
	return fieldStats.getFieldsWithIndexedValues();
  }
  public Set<String> _getFieldsWithNoIndexedValues() {
	return fieldStats.getFieldsWithNoIndexedValues();
  }

  public Set<String> getFieldNamesWithTerms() {
	return rawTermsMap.keySet();
  }

  
  void resetData() throws SolrServerException {
	resetData( true );
  }
  void resetData( Boolean includeHelperClass ) throws SolrServerException {
	if ( null==includeHelperClass || includeHelperClass ) {
	  fieldStats.resetData();
	}
	categorizedTerms = new TreeMap<>();
	rawTermsMap = new LinkedHashMap<>();
  }
  void doAllTabulations() throws SolrServerException {
	doAllTabulations( true );
  }
  // We can skip this only when we've first called their constructor
  // TODO: a bit awkward... requires knowledge of helper class impl
  void doAllTabulations( Boolean includeHelperClass ) throws SolrServerException {
	if ( null==includeHelperClass || includeHelperClass ) {
	  fieldStats.doAllTabulations();
	}
	// tabulateAllFields();
	tabulateFields( getTargetFieldNames() );
  }

  // classifierTuple -> terms
  // Map< Set<String>, Set<String> > classifyTerms( Set<String> terms )
  // Map< List<String>, Set<String> > classifyTerms( Set<String> terms )
  Map< String, Set<String> > classifyTerms( Set<String> terms )
  {
	// Map< Set<String>, Set<String> > out = new TreeMap<>();
	// Map< List<String>, Set<String> > out = new TreeMap<>();
	Map< String, Set<String> > out = new TreeMap<>();
	for ( String t : terms ) {
	  // classificationKey -> charCount
	  // Map<String,Long> termStats = CharUtils.classifyString_LongForm( t );
	  Map<String,Long> termStats = CharUtils.classifyString_ShortForm( t );

	  // Set<String> classificationTuple = termStats.keySet();
	  // Set<String> classificationTuple = new LinkedHashSet<>( termStats.keySet() );
	  // List<String> classificationTuple = new LinkedList<>( termStats.keySet() );
	  // List<String> classificationTuple = Collections.unmodifiableList(  new LinkedList<>( termStats.keySet() )  );
	  // List<String> classificationTuple = Collections.unmodifiableList(  new ArrayList<>( termStats.keySet() )  );
	  List<String> classificationTuple = new ArrayList<>( termStats.keySet() );
	  String classificationKey = SetUtils.join( classificationTuple );
	  // if ( out.containsKey(classificationTuple) )
	  // Set<String> value = out.get(classificationTuple);
	  // if ( null!=value )
	  if ( out.containsKey(classificationKey) )
	  {
		// out.get(classificationTuple).add( t );
		// value.add( t );
		out.get(classificationKey).add( t );
	  }
	  else {
		// Preserve order of insertion
		Set<String> termVector = new LinkedHashSet<>();
		termVector.add( t );
		// out.put( classificationTuple, termVector );
		out.put( classificationKey, termVector );
	  }
	}
    return out;
  }

  void tabulateAllFields() throws SolrServerException {
	tabulateFields( getFieldNamesWithTerms() );
  }
  void tabulateFields( Set<String> fieldNames ) throws SolrServerException {
	System.out.println( "Fetching ALL terms, this may take a while ..." );
	long start = System.currentTimeMillis();

	// Includes Deleted Docs
	// Note: report includes statement about whether deleted docs are included or not
	// so if you change the impl, also change the report text
	rawTermsMap = SolrUtils.getAllTermsForFields_ViaTermsRequest( fieldStats.getServer(), fieldNames );
	long stop = System.currentTimeMillis();
	long diff = stop - start;
	System.out.println( "Via TermsRequest took " + diff + " ms" );

	// Try to get just active docs ...
	// ... slow, unstable ...
	// termsMap = SolrUtils.getTermsForFields_ViaSearchFacets( fieldStats.getServer(), fieldNames, -1 );
	// termsMap = SolrUtils.getTermsForFields_ViaSearchFacets( fieldStats.getServer(), fieldNames, 100 );
	// Set<String> tmpFieldNames = new LinkedHashSet<>();
	// tmpFieldNames.addAll( Arrays.asList(new String[]{ "class", "mpaaRating", "type" }) );
	// termsMap = SolrUtils.getTermsForFields_ViaSearchFacets( fieldStats.getServer(), tmpFieldNames, 100 );
	// termsMap = SolrUtils.getTermsForFields_ViaSearchFacets( fieldStats.getServer(), tmpFieldNames, -1 );
	// long stop = System.currentTimeMillis();
	// long diff = stop - start;
	// System.out.println( "Via SearchFacets took " + diff + " ms" );
	
	System.out.println( "Tabulating retrieved terms ..." );
	// TODO: total term instances
	for ( String field : fieldNames ) {
	  if ( rawTermsMap.containsKey(field) ) {
		Map<String,Long> terms = rawTermsMap.get(field);
		// Classify and tabulate terms into Unicode groupings
		// Map< Set<String>, Set<String> > classifications = classifyTerms( terms.keySet() );
		// Map< List<String>, Set<String> > classifications = classifyTerms( terms.keySet() );
		Map< String, Set<String> > classifications = classifyTerms( terms.keySet() );
		categorizedTerms.put( field, classifications );
	  }
	  // TODO: else... maybe override getFieldsWithNoIndexedValues
	}
  }

  // TODO: could include label as a settable member field
  public String generateReport( String optLabel ) throws Exception {
	return generateReportForFields( optLabel, getTargetFieldNames()  );
  }
  String generateReportForFields( String optLabel, Set<String> fieldNames ) throws Exception {
	// *if* not done in constructor, nor by specific all,
	// then do it now
	// doAllTabulations();

	// TODO: compare, warn:
	// getTargetFieldNames()    - if null/empty, nothing to do
	// getAllFieldNames()       - if targets not here, Solr error
	// getFieldNamesWithTerms() - if targets not here, no term stats
	  
	StringWriter sw = new StringWriter();
    PrintWriter out = new PrintWriter(sw);

    if ( null!=optLabel ) {
    	out.println( "----------- " + optLabel + " -----------" );
    }
    addSimpleStatToReport( out, "Total Active Docs", getTotalDocCount(), null );

    out.println();
    out.println( "All Fields: " + getAllFieldNames() );

    out.println();
    // out.println( "Fields with Indexed Values: " + getFieldsWithIndexedValues() );
    out.println( "Fields with Terms: " + getFieldNamesWithTerms() );

    out.println();
    out.println( "Target Fields to Analyze: " + getFieldNamesWithTerms() );

    out.println();
    // addAllFieldStatsToReport( out );
    addFieldStatsToReport( out, fieldNames );
    
    String outStr = sw.toString();
    return outStr;
  }
  void addSimpleStatToReport( PrintWriter out, String label, long stat, String optIndent ) {
	if ( null!=optIndent ) {
		out.print( optIndent );
	}
	String statStr = NumberFormat.getNumberInstance().format( stat );
	out.println( "" + label + ": " + statStr );
  }
  void addStatPairToReport( PrintWriter out, String label, long statA, long statB, String optIndent ) {
	if ( null!=optIndent ) {
		out.print( optIndent );
	}
	String statAStr = NumberFormat.getNumberInstance().format( statA );
	String statBStr = NumberFormat.getNumberInstance().format( statB );
	out.println( "" + label + ": " + statAStr + " / " + statBStr );
  }
  void addAllFieldStatsToReport( PrintWriter out ) {
	addFieldStatsToReport( out, getFieldNamesWithTerms() );
  }
  void addFieldStatsToReport( PrintWriter out, Set<String> fieldNames ) {
	addFieldToReport( out, fieldNames, 5 );
  }
  void addFieldToReport( PrintWriter out, Set<String> fieldNames, int sampleSliceSize ) {
	// Whether includes deleted or not controlled by tabulateFieldsWithIndexedValues
	out.println( "Unicode Term Categories, for each Field (terms include deleted docs):" );
	// Foreach Field
	for ( String field : fieldNames ) {
	  out.println();
	  out.println( "Field: " + field );
	  // Map< Set<String>, Set<String> > stats = categorizedTerms.get( field );
	  // Map< List<String>, Set<String> > stats = categorizedTerms.get( field );
	  Map< String, Set<String> > stats = categorizedTerms.get( field );
	  // for ( Entry< Set<String>, Set<String> > entry : stats.entrySet() )
	  // for ( Entry< List<String>, Set<String> > entry : stats.entrySet() )
	  for ( Entry< String, Set<String> > entry : stats.entrySet() )
	  {
	    // Set<String> tuple = entry.getKey();
	    // List<String> tuple = entry.getKey();
	    String tuple = entry.getKey();
	    Set<String> terms = entry.getValue();
	    out.println( "\tCharacter Classes: [" + tuple + "]" );
	    int displayedCount = 0;
	    boolean brokeEarly = false;
	    for ( String t : terms ) {
	      displayedCount++;
	      if ( displayedCount > sampleSliceSize ) {
	    	brokeEarly = true;
	    	break;
	      }
	      out.println( "\t\t" + t );
	    }
	    if ( ! brokeEarly ) {
	      out.println( "\t\t\t(showing all " + terms.size() + " terms)" );
	    }
	    else {
	      out.println( "\t\t\t... (" + terms.size() + " terms)" );
	    }
	  }
	}
  }

//		// Whether to show "..."
//		boolean hadMore = false;
//		int i = 0;
//		// Do until we run out of terms OR have displayed enough examples
//		for ( Entry<String, Long> entry : terms.entrySet() ) {
//		  i++;
//		  String term = entry.getKey();
//		  if ( term.length() < expectedMin ) {
//		    if ( displayedSamples >= sampleSliceSize ) {
//			  hadMore = true;
//			  break;
//		    }
//		    Long count = entry.getValue();
//		    String countStr = NumberFormat.getNumberInstance().format( count );
//		    String iStr = NumberFormat.getNumberInstance().format( i+1 );
//		    out.println( "\t\t\t" + iStr + ": " + term + ", len=" + term.length() );
//		    displayedSamples++;
//		  }
//		}
//		if ( hadMore ) {
//		  out.println( "\t\t\t..." );			
//		}
//	  }
  
  public static void main( String [] argv ) throws Exception {
	HttpSolrServer s = SolrUtils.getServer( HOST, PORT, COLL );
	// TermCodepointStats tcp = new TermCodepointStats( s );
	List<String> fieldNames = Arrays.asList( new String[]{"categoryNames", "class", "color", "department", "genre", "mpaaRating"} );
	TermCodepointStats tcp = new TermCodepointStats( s, fieldNames );
	String report = tcp.generateReport( s.getBaseURL() );
	System.out.println( report );
  }
}