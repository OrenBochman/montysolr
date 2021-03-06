package org.apache.lucene.queryParser.aqp.processors;

import org.apache.lucene.queryParser.aqp.nodes.AqpANTLRNode;
import org.apache.lucene.queryParser.core.QueryNodeException;
import org.apache.lucene.queryParser.core.config.QueryConfigHandler;
import org.apache.lucene.queryParser.core.nodes.QueryNode;

public class AqpAdslabsFixQPOSITIONProcessor extends AqpQProcessor {

	@Override
	public boolean nodeIsWanted(AqpANTLRNode node) {
		if (node.getTokenLabel().equals("QNORMAL") || node.getTokenLabel().equals("QPHRASE")) {
			return true;
		}
		return false;
	}

	public QueryNode createQNode(AqpANTLRNode node) throws QueryNodeException {
		
		AqpANTLRNode subChild = ((AqpANTLRNode) node.getChildren().get(0)); 
		String input = subChild.getTokenInput();
		
		// TODO: emit warnings
		
		if (node.getTokenLabel().equals("QNORMAL")) {
			if (input.endsWith("$")) {
				node.setTokenName("QPOSITION");
				node.setTokenLabel("QPOSITION");
			}
		}
		else {
			String testInput = input.substring(1, input.length()-1);
			if (testInput.startsWith("^") || testInput.endsWith("$")) {
				node.setTokenName("QPOSITION");
				node.setTokenLabel("QPOSITION");
				subChild.setTokenInput(testInput);
			}
		}
		
		return node;
	}

}
