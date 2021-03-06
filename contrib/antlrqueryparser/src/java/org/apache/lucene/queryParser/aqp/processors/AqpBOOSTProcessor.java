package org.apache.lucene.queryParser.aqp.processors;

import java.util.List;

import org.apache.lucene.messages.MessageImpl;
import org.apache.lucene.queryParser.aqp.config.DefaultFieldAttribute;
import org.apache.lucene.queryParser.aqp.nodes.AqpANTLRNode;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.messages.QueryParserMessages;
import org.apache.lucene.queryParser.core.nodes.BoostQueryNode;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryParser.standard.config.BoostAttribute;
import org.apache.lucene.queryParser.standard.config.FuzzyAttribute;


/**
 * Sets the node into the BoostQueryNode, this processor requires that
 * {@link AqpTMODIFIERProcessor} ran before. Because we depend on the proper
 * tree shape.
 * <br/>
 * 
 * If BOOST node contains only one child, we return that child and do 
 * nothing.
 * <br/>
 * 
 * If BOOST node contains two children, we take the first and check its
 * input, eg.
 * <pre>
 * 					BOOST
 *                  /  \
 *               ^0.1  rest
 * </pre>
 * 
 * We create a new node  BoostQueryNode(rest, 0.1) and return that node.
 * <br/>
 * 
 * Presence of the BOOST node child means user specified at least "^"
 * We'll use the default from the configuration {@see BoostAttribute}
 * 
 * @see AqpTMODIFIERProcessor
 * @see AqpFUZZYProcessor
 */
public class AqpBOOSTProcessor extends QueryNodeProcessorImpl implements
		QueryNodeProcessor {

	@Override
	protected QueryNode preProcessNode(QueryNode node)
			throws QueryNodeException {
		if (node instanceof AqpANTLRNode && ((AqpANTLRNode) node).getTokenLabel().equals("BOOST")) {
			
			if (node.getChildren().size()==1) {
				return node.getChildren().get(0);
			}
			
			Float boost = getBoostValue(node);
			if (boost==null) {
				return node.getChildren().get(node.getChildren().size()-1);
			}
			return new BoostQueryNode(node.getChildren().get(node.getChildren().size()-1), boost);
			
		}
		return node;
		
	}

	@Override
	protected QueryNode postProcessNode(QueryNode node)
			throws QueryNodeException {
		return node;
	}

	@Override
	protected List<QueryNode> setChildrenOrder(List<QueryNode> children)
			throws QueryNodeException {
		return children;
	}
	
	private Float getBoostValue(QueryNode boostNode) throws QueryNodeException {
		if (boostNode.getChildren()!=null) {
			
			AqpANTLRNode child = ((AqpANTLRNode) boostNode.getChildren().get(0));
			String input = child.getTokenInput();
			float boost;
			
			if (input.equals("^")) {
				QueryConfigHandler queryConfig = getQueryConfigHandler();
				if (queryConfig == null || !queryConfig.hasAttribute(BoostAttribute.class)) {
					throw new QueryNodeException(new MessageImpl(
			                QueryParserMessages.LUCENE_QUERY_CONVERSION_ERROR,
			                "Configuration error: " + BoostAttribute.class.toString() + " is missing"));
				}
				boost = queryConfig.getAttribute(BoostAttribute.class).getBoost();
			}
			else {
				boost = Float.valueOf(input.replace("^", ""));
			}
			
			return boost;
			
		}
		return null;
	}

}
