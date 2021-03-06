package org.apache.lucene.queryParser.aqp.config;

import org.apache.lucene.queryParser.aqp.builders.AqpFunctionQueryBuilderProvider;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.builders.QueryBuilder;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.util.Attribute;

public interface AqpFunctionQueryBuilderConfig extends Attribute {
	
	
	public void addProvider(AqpFunctionQueryBuilderProvider provider);
	
	public void setBuilder(String funcName, QueryBuilder builder);
	
	public QueryBuilder getBuilder(String funcName, QueryNode node, QueryConfigHandler config)
		throws QueryNodeException;
	
}
