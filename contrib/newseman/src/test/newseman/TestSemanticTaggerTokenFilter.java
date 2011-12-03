package newseman;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.Version;

import newseman.SemanticTaggerTokenFilter;
import newseman.SemanticTagger;
import newseman.MontySolrBaseTokenStreamTestCase;

import invenio.montysolr.jni.MontySolrVM;
import invenio.montysolr.jni.PythonMessage;

import java.io.StringReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

public class TestSemanticTaggerTokenFilter extends MontySolrBaseTokenStreamTestCase {
	
	private String url;
	private SemanticTagger tagger;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		
		url = "sqlite:///:memory:";
		
		
		tagger = new SemanticTagger(url);
		tagger.configureTagger("czech", 2, "add", "purge");
		
		// fill the db with test data
		PythonMessage message = MontySolrVM.INSTANCE.createMessage(
				"fill_newseman_dictionary")
				.setParam("url", tagger.getName());
		MontySolrVM.INSTANCE.sendMessage(message);
	}
	
	@Override
	public String getModuleName() {
		//return "monty_newseman.tests.bridge.Bridge";
		return "montysolr.java_bridge.SimpleBridge";
	}


	public void testSemanticTokenFilter() throws IOException {
		String text = "velká světová revoluce byla velká říjnová revoluce " +
        "s velkou extra říjnovou revolucí komunistická";
    
	
		StringReader reader = new StringReader(text);
		SemanticTaggerTokenFilter stream = 
			new SemanticTaggerTokenFilter(
				new StopFilter( Version.LUCENE_31,
					new StandardTokenizer(Version.LUCENE_31, reader),
					new HashSet(Arrays.asList(new String[] {"s", "a"}))
				),
				tagger
			);
		
		assertTrue(stream != null);
		CharTermAttribute termAtt = stream
				.getAttribute(CharTermAttribute.class);
		PositionIncrementAttribute posIncrAtt = stream
				.getAttribute(PositionIncrementAttribute.class);
		
		FlagsAttribute flagsAtt = stream.getAttribute(FlagsAttribute.class);
		OffsetAttribute offsetAtt = stream.getAttribute(OffsetAttribute.class);
		TypeAttribute typeAtt = stream.getAttribute(TypeAttribute.class);
		PayloadAttribute payloadAtt = stream.getAttribute(PayloadAttribute.class);
		SemanticTagAttribute semAtt = stream.getAttribute(SemanticTagAttribute.class);
		

		StringBuffer buf = new StringBuffer();
		while (stream.incrementToken()) {
			buf.append(termAtt.toString());
			buf.append("[");
			buf.append(offsetAtt.startOffset() + ":" + offsetAtt.endOffset());
			buf.append("]");
			buf.append("/");
			buf.append(posIncrAtt.getPositionIncrement());
			
			buf.append(" ");
			
		}
		
		System.out.println(buf);
		
		assertEquals(buf.toString().trim(),
				"velká[0:5]/1 r0[0:5]/0 světová[6:13]/1 r1[6:13]/0 revoluce[14:22]/1 r2[14:22]/0 byla[23:27]/1 r4[23:27]/0 " + 
				"velká[28:32]/1 velká říjnová revoluce[28:32]/0 velk říjn revol[28:32]/0 XXX[28:32]/0 " +
				"říjnová[33:40]/1 revoluce[41:50]/1 " +
				"velkou[53:83]/2 velkou říjnovou revolucí[53:83]/0 velk říjn revol[53:83]/0 XXX[53:83]/0 " + 
				"extra[60:65]/1 říjnovou[66:74]/1 revolucí[75:83]/1 komunistická[84:96]/1 r6[84:96]/0 r7[84:96]/0");
				
	}
	
}
