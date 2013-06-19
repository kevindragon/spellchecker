package com.lexiscn;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

class NGramAnalyzer extends Analyzer {

	private int minGram;
	private int maxGram;

	public NGramAnalyzer(int minGram, int maxGram) {
		this.minGram = minGram;
		this.maxGram = maxGram;
	}

	@Override
	protected TokenStreamComponents createComponents(String arg0, Reader reader) {

		Tokenizer source = new StandardTokenizer(Version.LUCENE_43, reader);

		TokenStream filter = new ShingleFilter(source, minGram, maxGram);
		filter = new LowerCaseFilter(Version.LUCENE_43, filter);
		filter = new StopFilter(Version.LUCENE_43, filter,
				StopAnalyzer.ENGLISH_STOP_WORDS_SET);

		return new TokenStreamComponents(source, filter);
	}
}
