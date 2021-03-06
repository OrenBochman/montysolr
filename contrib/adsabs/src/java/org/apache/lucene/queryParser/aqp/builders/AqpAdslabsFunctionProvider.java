package org.apache.lucene.queryParser.aqp.builders;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.aqp.NestedParseException;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.builders.QueryBuilder;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.solr.search.AqpFunctionQParser;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.ValueSourceParser;
import org.apache.solr.search.function.PositionSearchFunction;
import org.apache.solr.search.function.ValueSource;

public class AqpAdslabsFunctionProvider implements
		AqpFunctionQueryBuilderProvider {
	
	public static Map<String, ValueSourceParser> parsers = new HashMap<String, ValueSourceParser>();
	
	static {
		parsers.put("pos", new ValueSourceParser() {
	      @Override
	      public ValueSource parse(FunctionQParser fp) throws ParseException {
    		  PositionSearchFunction o = new PositionSearchFunction(
    			  fp.parseId(),
    			  fp.parseId(),
    			  fp.parseInt(),
    			  fp.parseInt());
    		  if (fp.hasMoreArguments()) {
    			  throw new NestedParseException("Wrong number of arguments");
    		  }
    		  return o;
	      }
	    });
	};

	public QueryBuilder getBuilder(String funcName, QueryNode node, QueryConfigHandler config) 
		throws QueryNodeException {
		
		
		ValueSourceParser provider = parsers.get(funcName);
		if (provider == null)
			return null;
			
		// the params are all null because we know we are not using solr request handler params
		AqpFunctionQParser parser = new AqpFunctionQParser(null, null, null, null);
		
		AqpFunctionQueryTreeBuilder.flattenChildren(node); // convert into opaque nodes
		AqpFunctionQueryTreeBuilder.simplifyValueNode(node); // remove func name, leave only values
		
		return new AqpFunctionQueryTreeBuilder(provider, parser);
				
	}

}
