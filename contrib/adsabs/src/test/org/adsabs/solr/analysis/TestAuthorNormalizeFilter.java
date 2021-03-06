package org.adsabs.solr.analysis;

import java.io.Reader;
import java.io.StringReader;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.BaseTokenTestCase;

public class TestAuthorNormalizeFilter extends BaseTokenTestCase {

	  public void testUppercase() throws Exception {
		  	Reader reader = new StringReader("Gómez, Hector Q");
		  	Tokenizer tokenizer = new KeywordTokenizer(reader);
		    AuthorNormalizeFilterFactory factory = new AuthorNormalizeFilterFactory();
		    TokenStream stream = factory.create(tokenizer);
		    assertTokenStreamContents(stream, new String[] { "GÓMEZ, HECTOR Q" });
	  }
	  public void testWhitespace() throws Exception {
		  	Reader reader = new StringReader("  Gómez,\n Hector    Q ");
		  	Tokenizer tokenizer = new KeywordTokenizer(reader);
		    AuthorNormalizeFilterFactory factory = new AuthorNormalizeFilterFactory();
		    TokenStream stream = factory.create(tokenizer);
		    assertTokenStreamContents(stream, new String[] { "GÓMEZ, HECTOR Q" });
	  }
	  public void testPuncuation() throws Exception {
		  	Reader reader = new StringReader("%$Gómez_Foo, He-ctor;  29Q.");
		  	Tokenizer tokenizer = new KeywordTokenizer(reader);
		    AuthorNormalizeFilterFactory factory = new AuthorNormalizeFilterFactory();
		    TokenStream stream = factory.create(tokenizer);
		    assertTokenStreamContents(stream, new String[] { "GÓMEZ_FOO, HE-CTOR 29Q" });
	  }
}
