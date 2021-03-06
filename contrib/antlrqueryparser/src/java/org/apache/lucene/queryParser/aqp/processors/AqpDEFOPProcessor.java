package org.apache.lucene.queryParser.aqp.processors;

import java.util.List;

import org.apache.lucene.messages.MessageImpl;
import org.apache.lucene.queryParser.aqp.AqpQueryParser;
import org.apache.lucene.queryParser.aqp.config.DefaultFieldAttribute;
import org.apache.lucene.queryParser.aqp.nodes.AqpANTLRNode;
import org.apache.lucene.queryParser.aqp.util.AqpUtils;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.messages.QueryParserMessages;
import org.apache.lucene.queryParser.core.nodes.BooleanQueryNode;
import org.apache.lucene.queryParser.core.nodes.QueryNode;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessor;
import org.apache.lucene.queryParser.core.processors.QueryNodeProcessorImpl;
import org.apache.lucene.queryParser.standard.config.DefaultOperatorAttribute;
import org.apache.lucene.queryParser.standard.config.DefaultOperatorAttribute.Operator;

/**
 * Finds the {@link AqpANTLRNode} with tokenLabel <pre>DEFOP</pre> and 
 * sets their @{code tokenInput} to be the name of the default
 * operator.
 * 
 * If there is only one child, the child is returned and we remove the 
 * operator. This happens mainly for simple queries such as
 * 
 * <pre> field:value </pre>
 * 
 * But also for queries which are itself clauses, like:
 * 
 * <pre> +(this that) </pre>
 * 
 * which produces:
 * 
 * <pre>
 *            DEFOP
 *              |
 *           MODIFIER
 *            /   \
 *               TMODIFIER
 *                  |
 *                CLAUSE
 *                  | 
 *                DEFOP
 *                /   \
 *          MODIFIER MODIFIER   
 *             |        |
 * </pre>
 *              
 *              
 * @see DefaultOperatorAttribute
 * @see AqpQueryParser#setDefaultOperator(org.apache.lucene.queryParser.standard.config.DefaultOperatorAttribute.Operator)
 * 
 */
public class AqpDEFOPProcessor extends QueryNodeProcessorImpl implements
		QueryNodeProcessor {

	@Override
	protected QueryNode preProcessNode(QueryNode node)
			throws QueryNodeException {
		
		if (node instanceof AqpANTLRNode && ((AqpANTLRNode) node).getTokenLabel().equals("DEFOP")) {
			
			// only one child, we'll simplify
			if (node.getChildren().size()==1) {
				return node.getChildren().get(0);
			}
			
			AqpANTLRNode thisNode = (AqpANTLRNode) node;
			Operator op = getDefaultOperator();
			
			// Turn the DEFOP into the default operator
			thisNode.setTokenLabel(op.name());
			
			List<QueryNode> children = node.getChildren();
			if (children!=null && children.size()==1) {
				AqpANTLRNode child = (AqpANTLRNode) children.get(0);
				if (child.getTokenName().equals("OPERATOR")
						|| child.getTokenLabel().equals("CLAUSE")
						|| child.getTokenLabel().equals("ATOM")) {
					return child;
				}
			}
			else if(children!=null && children.size() > 1) {
				// several childeren (=clauses) below the operator
				// we check if we can put them together, ie
				// (this) AND (that) --> this AND that
				
				String thisOp = thisNode.getTokenLabel();
				String last = ((AqpANTLRNode)children.get(0)).getTokenLabel();
				boolean rewriteSafe = true;
				
				
				for (int i=1;i<children.size();i++) {
					AqpANTLRNode t = (AqpANTLRNode) children.get(i);
					String tt = t.getTokenLabel();
					if (!(tt.equals(last) && t.getTokenLabel().equals(thisOp))) {
						rewriteSafe = false;
						break;
					}
				}
				
				if (rewriteSafe==true) {
					QueryNode firstChild = children.get(0);
					List<QueryNode> childrenList = firstChild.getChildren();
					
					for (int i=1;i<children.size();i++) {
						QueryNode otherChild = children.get(i);
						for (QueryNode nod: otherChild.getChildren()) {
							childrenList.add(nod);
						}
					}
					
					children.clear();
					thisNode.set(childrenList);
				}
			}
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
	
	private Operator getDefaultOperator() throws QueryNodeException {
		QueryConfigHandler queryConfig = getQueryConfigHandler();
		
		if (queryConfig != null) {
			
			if (queryConfig.hasAttribute(DefaultOperatorAttribute.class)) {
				return queryConfig.getAttribute(
						DefaultOperatorAttribute.class).getOperator();
			}
		}
		throw new QueryNodeException(new MessageImpl(
                QueryParserMessages.LUCENE_QUERY_CONVERSION_ERROR,
                "Configuration error: " + DefaultFieldAttribute.class.toString() + " is missing"));
	}

}

