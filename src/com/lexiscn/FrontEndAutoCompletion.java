package com.lexiscn;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Properties;

import javax.servlet.ServletConfig;

import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

/**
 * 返回自动补上前面缺少一个字，后面缺少1~2个字的一个字符串列表
 * 
 * @author Kevin Jiang kevin.jiang@lexisnexis.com
 *
 */
public class FrontEndAutoCompletion {

	/**
	 * 前向和后向的词典
	 */
	private Trie[] lexicon = null;
	
	private AutoCompleteExclude exclude = null;

	/**
	 * IKAnalyzer的分词器
	 */
	private IKSegmenter segmenter;
	
	/**
	 * lucene server的查询对象
	 */
	private LuceneQuery query = null;
	
	/**
	 * 保存从外部传入的servlet config
	 */
	private ServletConfig servletConfig = null;
	
	/**
	 * 程序的配置
	 */
	private Properties prop;
	
	/**
	 * 一个词在语料库中至少出来的次数
	 */
	private int minOccurrence = 1;
	
	/**
	 * 构造函数，初始化配置、词典、分词器
	 * @param config
	 */
	public FrontEndAutoCompletion(ServletConfig config) {
		servletConfig = config;
		String webroot = servletConfig.getServletContext().getRealPath("/");
		try {
		// 加载配置文件
		FileInputStream fis = new FileInputStream(webroot+"config.properties");
		prop = new Properties();
			prop.load(fis);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		lexicon = Lexicon.loadLexicon(webroot+prop.getProperty("phrase.dic"), 6);
		exclude = new AutoCompleteExclude(webroot+prop.getProperty("exclude.dic"));
		segmenter = new IKSegmenter(new StringReader(""), true);
	}

	/**
	 * 分词
	 * @param word
	 * @return String[]
	 */
	public synchronized String[] seg(String word) {
		String[] ret = null;

		ArrayList<String> terms = new ArrayList<String>();

		segmenter.reset(new StringReader(word));
		Lexeme lexeme = null;
		try {
			while ((lexeme = segmenter.next()) != null) {
				terms.add(lexeme.getLexemeText());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		ret = new String[terms.size()];
		terms.toArray(ret);

		return ret;
	}

	/**
	 * 一个字符串分词后是否含有一个字的词
	 * @param word
	 * @return
	 */
	public boolean containSingleCharAfterSeg(String word) {
		boolean bool = false;
		String[] segs = seg(word);
		for (int i=0; i<segs.length-1; i++) {
			if (segs[i].length() == 1) {
				bool = true;
				break;
			}
		}
		
		return bool;
	}
	
	/**
	 * 获取以word开头的词
	 * @param word
	 * @return String[]
	 */
	public String[] getEndSuggestions(String word) {
		String[] suggestions = null;

		Trie hm = lexicon[0];
		ArrayList<String> end = hm.search(word);
		ArrayList<String> candidate = new ArrayList<String>();
		for (int i=0; i<end.size(); i++) {
			if (!word.equals(end.get(i)) &&
					end.get(i).length() - word.length() <= 2) {
				candidate.add(end.get(i));
			}
		}
		suggestions = new String[candidate.size()];
		candidate.toArray(suggestions);
		
		return suggestions;
	}
	
	/**
	 * 获取以word结尾的词
	 * @param word
	 * @return String[]
	 */
	public String[] getStartSuggestions(String word) {
		String[] suggestions = null;

		Trie hm = lexicon[1];
		ArrayList<String> start = hm.search(new StringBuffer(word).reverse().toString());
		StringBuffer element = new StringBuffer();
		// 把倒序排列的后缀字符字典搬正
		for (int i=0; i<start.size(); i++) {
			element = new StringBuffer(start.get(i));
			String s = element.reverse().toString();
			start.set(i, s);
		}
		// 取不跟word相同的，且长度差为1的字符串
		ArrayList<String> candidate = new ArrayList<String>();
		for (int i=0; i<start.size(); i++) {
			if (!word.equals(start.get(i)) && 
					start.get(i).length() - word.length() == 1) {
				candidate.add(start.get(i));
			}
		}
		suggestions = new String[candidate.size()];
		candidate.toArray(suggestions);
		
		return suggestions;
	}

	/**
	 * 插入一个元素，按probability里的值从大到小排列
	 * @param word
	 * @param prob
	 */
	public void addCandidate (LinkedList<String> candidates, LinkedList<Long> probability, 
			String word, long prob) {
		int index = 0;
		for (int i=0; i<candidates.size(); i++) {
			if (probability.get(i) < prob) {
				index = i;
				break;
			}
			if (i == candidates.size()-1) {
				index = i+1;
			}
		}
		probability.add(index, prob);
		candidates.add(index, word);
	}
	
	/**
	 * 补全word前后缺的字，前面1个，后面1~2个
	 * @param word
	 * @return
	 */
	public String[] suggestAutoCompletion(String word) {

		// 存储所有候选词，包括前面缺词，后面缺词，前后都缺的。每一个请求都需要创建一个这个对象
		LinkedList<String> candidates = new LinkedList<String>();
		// 存储每个候选词的概率
		LinkedList<Long> probability = new LinkedList<Long>();
		query = new LuceneQuery(prop.getProperty("luceneserver.host") + 
				prop.getProperty("luceneserver.path"));
		String[] seg = seg(word);
		if (seg.length == 1 && seg[0].length() == 1) {
			return new String[]{};
		}

		// 前面缺词，当分词后开头是一个字的时候才去查找候选词
		long[] frontProbability = null;
		String[] candidatesStart = getStartSuggestions(seg[0]);
		if (seg.length > 0 && seg[0].length() == 1) {
			if (candidatesStart.length > 0) {
				frontProbability = new long[candidatesStart.length];
				String frontWord;
				for (int i=0; i<candidatesStart.length; i++) {
					frontWord = candidatesStart[i] + word.substring(seg[0].length());
					frontProbability[i] = query.getTotalHits(frontWord);
					if (frontProbability[i] >= minOccurrence) {
						addCandidate(candidates, probability, frontWord, frontProbability[i]);
					}
				}
			}
		}

		// 后面缺词
		long[] endProbability = null;
		String[] candidateEnd = getEndSuggestions(seg[seg.length-1]);
		if (candidateEnd.length > 0) {
			endProbability = new long[candidateEnd.length];
			String endWord;
			for (int i=0; i<candidateEnd.length; i++) {
				endWord = word.substring(0, word.length()-seg[seg.length-1].length()) + candidateEnd[i];
				endProbability[i] = query.getTotalHits(endWord);
				if (endProbability[i] >= minOccurrence) {
					addCandidate(candidates, probability, endWord, endProbability[i]);
				}
				
			}
		}

		// 前后两端都缺词
		if (frontProbability != null && endProbability != null) {
			String frontEndWord = null;
			for (int i=0; i<frontProbability.length; i++) {
				if (frontProbability[i] <= 0) {
					continue;
				}
				for (int j=0; j<endProbability.length; j++) {
					frontEndWord = candidatesStart[i] 
							+ word.substring(seg[0].length(), word.length()-seg[seg.length-1].length()) 
							+ candidateEnd[j];
					long frontEndProbability = query.getTotalHits(frontEndWord);
					if (frontEndProbability >= minOccurrence) {
						addCandidate(candidates, probability, frontEndWord, frontEndProbability);
					}
				}
			}
		}
		
		// 不分词的情况下，判断是否有词可以补全
		if (word.length() < 10) {
			String[] wholeAtStart = getStartSuggestions(word);
			for (int i=0; i<wholeAtStart.length; i++) {
				long candProb = query.getTotalHits(wholeAtStart[i]);
				if (candProb >= minOccurrence) {
					addCandidate(candidates, probability, wholeAtStart[i], candProb);
				}
			}
			String[] wholeAtEnd = getEndSuggestions(word);
			for (int i=0; i<wholeAtEnd.length; i++) {
				long candProb = query.getTotalHits(wholeAtEnd[i]);
				if (candProb >= minOccurrence) {
					addCandidate(candidates, probability, wholeAtEnd[i], candProb);
				}
			}
		}

		ArrayList<String> list = new ArrayList<String>();
		for (int i=0; i<candidates.size(); i++) {
			if (!list.contains(candidates.get(i)) && !exclude.contains(candidates.get(i))) {
				list.add(candidates.get(i));
			}
		}
		int maxNum = Math.min(20, list.size());
		String[] ret = new String[maxNum];
		for (int i=0; i<maxNum; i++) {
			ret[i] = list.get(i);
		}

		return ret;
	}
}
