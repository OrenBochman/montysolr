package org.apache.lucene.queryParser.aqp.processors;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.queryParser.aqp.nodes.AqpFunctionQueryNode;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.standard.processors.AnalyzerQueryNodeProcessor;

/**
 * This processor prevents analysis to happen for nodes that are 
 * inside certain node types. This is needed especially for the 
 * functional queries where we do not know how the values are to 
 * be processed and the function itself should decide.
 * 
 * @author rchyla
 *
 */
public class AqpAdslabsAnalyzerProcessor extends AnalyzerQueryNodeProcessor {
	
	private boolean enteredCleanZone = false;
	private int counter = 0;
	
	@Override
	protected QueryNode preProcessNode(QueryNode node) throws QueryNodeException {
		if (enteredCleanZone == true) {
			counter++;
		}
		else if (node instanceof AqpFunctionQueryNode) {
			enteredCleanZone = true;
			counter++;
		}
		
		return super.preProcessNode(node);
	}
	
	@Override
	protected QueryNode postProcessNode(QueryNode node)
			throws QueryNodeException {
		if (enteredCleanZone == true) {
			counter--;
			if (counter == 0) {
				enteredCleanZone = false;
			}
			return node;
		}
		
		return super.postProcessNode(node);
	}


}
