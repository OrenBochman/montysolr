package org.apache.lucene.search;

import montysolr.jni.MontySolrVM;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;


import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;


/**
 * {@link InvenioQuery} extends {@link Query} by adding ability 
 * to query and retrieve data from the Python process using
 * {@link MontySolrVM}
 * 
 * The real work is done by the {@link InvenioWeight} class,
 * which queries the underlying Python process and returns
 * the doc ids (they will be translated from the Invenio recids
 * into lucene docids) on the fly.
 * 
 * @author rchyla
 *
 */

public class InvenioQuery extends Query {


	private static final long serialVersionUID = -5151676153419588281L;
	
	private float boost = 1.0f; // query boost factor
	protected Query query;
	protected String idField = null;
	protected String searchField = null;
	protected String pythonResponder = null;
	
	public InvenioQuery(Query query, String idField, String searchField) {
		this.query = query;
		this.idField = idField;
		this.searchField = searchField;
	}
	
	public InvenioQuery(Query query, String idField, String searchField, String pythonResponder) {
		this(query, idField, searchField);
		this.pythonResponder = pythonResponder;
	}

	/**
	 * Sets the boost for this query clause to <code>b</code>. Documents
	 * matching this clause will (in addition to the normal weightings) have
	 * their score multiplied by <code>b</code>.
	 */
	public void setBoost(float b) {
		query.setBoost(b);
	}

	/**
	 * Gets the boost for this clause. Documents matching this clause will (in
	 * addition to the normal weightings) have their score multiplied by
	 * <code>b</code>. The boost is 1.0 by default.
	 */
	public float getBoost() {
		return query.getBoost();
	}

	/**
	 * Expert: Constructs an appropriate Weight implementation for this query.
	 *
	 * <p>
	 * Only implemented by primitive queries, which re-write to themselves.
	 */
	public Weight createWeight(IndexSearcher searcher) throws IOException {

		InvenioWeight w = new InvenioWeight((IndexSearcher) searcher, this, idField);
		if (pythonResponder != null) {
			w.setPythonResponder(pythonResponder);
		}
		return w;
	}


	/**
	 * Expert: called to re-write queries into primitive queries. For example, a
	 * PrefixQuery will be rewritten into a BooleanQuery that consists of
	 * TermQuerys.
	 */
	public Query rewrite(IndexReader reader) throws IOException {
		Query rewritten = query.rewrite(reader);
		if (rewritten != query) {
			InvenioQuery clone = (InvenioQuery) this.clone();
			clone.query = rewritten;
			return clone;
		} else {
			return this;
		}
	}


	/**
	 * Expert: adds all terms occurring in this query to the terms set. Only
	 * works if this query is in its {@link #rewrite rewritten} form.
	 *
	 * @throws UnsupportedOperationException
	 *             if this query is not yet rewritten
	 */
	public void extractTerms(Set<Term> terms) {
		query.extractTerms(terms);
	}


	/** Prints a user-readable version of this query. */
	public String toString(String s) {
		StringBuffer buffer = new StringBuffer();
		buffer.append("<(");
		buffer.append("ints,");
		buffer.append(idField);
		buffer.append(")");
		//buffer.append(query.toString());
		buffer.append(getInvenioQuery());
		buffer.append(">");
		return buffer.toString();
	}

	/** Returns true iff <code>o</code> is equal to this. */
	public boolean equals(Object o) {
		if (o instanceof InvenioQuery) {
			InvenioQuery fq = (InvenioQuery) o;
			return (query.equals(fq.query) && getBoost() == fq.getBoost());
		}
		return false;
	}

	/** Returns a hash code value for this object. */
	public int hashCode() {
		return query.hashCode() ^ Float.floatToRawIntBits(getBoost());
	}

	public String getInvenioQuery() {
		//String qfield = ((TermQuery) query).getTerm().field();
		String qval = getInvenioQueryValue(new StringBuffer(), query).toString();
		
		if (searchField != null) {
			qval = searchField + ":" + qval;
		}
		return qval;
	}
	
	public StringBuffer getInvenioQueryValue(StringBuffer out, Query query) {
		if (query instanceof TermQuery) {
			out.append(((TermQuery) query).getTerm().text());
		}
		else if (query instanceof MatchAllDocsQuery) {
			out.append("");
		}
		else if (query instanceof TermRangeQuery) {
			TermRangeQuery q = (TermRangeQuery) query;
			//out.append(q.includesLower() ? '[' : '{'); // invenio doesn't understand these
			BytesRef lt = q.getLowerTerm();
			BytesRef ut = q.getUpperTerm();
			if (lt == null) {
				out.append('*');
			} else {
				out.append(lt.utf8ToString());
			}

			out.append("->");

			if (ut == null) {
				out.append('*');
			} else {
				out.append(ut.utf8ToString());
			}
		}
		else if (query instanceof NumericRangeQuery) {
			NumericRangeQuery q = (NumericRangeQuery) query;
			//out.append(q.includesLower() ? '[' : '{'); // invenio doesn't understand these
			//TODO: verify Invneio is using int ranges only
			Number lt = q.getMin();
			Number ut = q.getMax();
			if (lt == null) {
				out.append("-999999");
			} else {
				out.append(lt.intValue());
			}

			out.append("->");

			if (ut == null) {
				out.append("999999");
			} else {
				out.append(ut.intValue());
			}
		} 
		else if (query instanceof BooleanQuery) {
			BooleanQuery q = (BooleanQuery) query;
			List<BooleanClause>clauses = q.clauses();
			out.append("(");
			for (int i=0;i<clauses.size();i++) {
				BooleanClause c = clauses.get(i);
				Query qq = c.getQuery();
				out.append(c.getOccur().toString());
				out.append(" ");
				getInvenioQueryValue(out, qq);
			}
			out.append(")");
		} 
		else if (query instanceof PrefixQuery) {
			PrefixQuery q = (PrefixQuery) query;
			Term prefix = q.getPrefix();
			out.append(prefix.text());
			out.append('*');
		} 
		else if (query instanceof WildcardQuery) {
			WildcardQuery q = (WildcardQuery) query;
			Term t = q.getTerm();
			out.append(t.text());
		} 
		else if (query instanceof PhraseQuery) {
			PhraseQuery q = (PhraseQuery) query;
			Term[] terms = q.getTerms();
			String slop = q.getSlop() > 0 ? "'" : "\"";
			out.append(slop);
			for (int i=0;i<terms.length;i++) {
				if (i != 0) {
					out.append(" ");
				}
				out.append(terms[i].text());
			}
			out.append(slop);
		} 
		else if (query instanceof FuzzyQuery) {
			// do nothing
		} 
		else if (query instanceof ConstantScoreQuery) {
			// do nothing
		} else {
			throw new IllegalStateException(query.toString());
		}
		
		return out;
	}
	
	public Query getInnerQuery() {
		return query;
	}
}

