package com.lexiscn;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.servlet.ServletConfig;

import org.apache.commons.lang3.StringUtils;
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

	/**
	 * IKAnalyzer的分词器
	 */
	private IKSegmenter segmenter;
	
	/**
	 * lucene server的查询对象
	 */
	private LuceneQuery query = null;
	
	public FrontEndAutoCompletion(ServletConfig config) {
		String webroot = config.getServletContext().getRealPath("/");
		lexicon = Lexicon.loadLexicon(webroot+"/phrase.dic");
		segmenter = new IKSegmenter(new StringReader(""), true);
//		query = LuceneQuery.getInstance();
	}

	/**
	 * 分词
	 * @param word
	 * @return String[]
	 */
	public String[] seg(String word) {
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
		for (int i=0; i<segs.length; i++) {
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
		ArrayList<String> start = hm.search(word);
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
		query = new LuceneQuery();
		
		String[] seg = seg(word);

		// 前面缺词，当分词后开头是一个字的时候才去查找候选词
		String[][] frontCandidates = null;
		long[] frontProbability = null;
		if (seg[0].length() == 1) {
			String[] candidatesStart = getStartSuggestions(seg[0]);
			if (candidatesStart.length > 0) {
				frontCandidates = new String[candidatesStart.length][seg.length];
				frontProbability = new long[candidatesStart.length];
				String frontWord;
				for (int i=0; i<candidatesStart.length; i++) {
					frontCandidates[i][0] = candidatesStart[i];
					for (int j=1; j<seg.length; j++) {
						frontCandidates[i][j] = seg[j];
					}
					frontWord = StringUtils.join(frontCandidates[i], "");
					frontProbability[i] = query.getTotalHits(frontWord);
					if (frontProbability[i] > 1) {
						addCandidate(candidates, probability, frontWord, frontProbability[i]);
					}
				}
			}
		}

		// 后面缺词
		String[][] endCandidates = null;
		long[] endProbability = null;
		String[] candidateEnd = getEndSuggestions(seg[seg.length-1]);
		if (candidateEnd.length > 0) {
			endCandidates = new String[candidateEnd.length][seg.length];
			endProbability = new long[candidateEnd.length];
			String endWord;
			for (int i=0; i<candidateEnd.length; i++) {
				int j = 0;
				for (; j<seg.length-1; j++) {
					endCandidates[i][j] = seg[j];
				}
				endCandidates[i][j] = candidateEnd[i];
				endWord = StringUtils.join(endCandidates[i], "");
				endProbability[i] = query.getTotalHits(endWord);
				if (endProbability[i] > 1) {
					addCandidate(candidates, probability, endWord, endProbability[i]);
				}
				
			}
		}

		// 前后两端都缺词
		String[][] frontEndCandidates = null;
		if (frontCandidates != null && endCandidates != null) {
			frontEndCandidates = new String[frontCandidates.length*endCandidates.length][seg.length];
			String frontEndWord = null;
			for (int i=0; i<frontCandidates.length; i++) {
				if (frontProbability[i] <= 0) {
					continue;
				}
				for (int j=0; j<endCandidates.length; j++) {
					int k = 0;
					for (; k<frontCandidates[i].length-1; k++) {
						frontEndCandidates[i][k] = frontCandidates[i][k];
					}
					frontEndCandidates[i][k] = endCandidates[j][endCandidates[j].length-1];
					frontEndWord = StringUtils.join(frontEndCandidates[i], "");
					long frontEndProbability = query.getTotalHits(frontEndWord);
					if (frontEndProbability > 1) {
						addCandidate(candidates, probability, frontEndWord, frontEndProbability);
					}
				}
			}
		}

		String[] allCandidates = new String[candidates.size()];
		candidates.toArray(allCandidates);
		// 去掉那些补全后分词仍然有一个字的字符串
		ArrayList<String> list = new ArrayList<String>();
		for (int i=0; i<allCandidates.length; i++) {
			if (!containSingleCharAfterSeg(allCandidates[i])) {
				list.add(allCandidates[i]);
			}
		}
		int maxNum = Math.min(5, list.size());
		String[] ret = new String[maxNum];
		for (int i=0; i<maxNum; i++) {
			ret[i] = list.get(i);
		}

		return ret;
	}
}
