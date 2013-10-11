package com.lexiscn;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
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
		
		lexicon = Lexicon.loadLexicon(webroot+prop.getProperty("phrase.dic"));
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
		String[] seg = seg(word);

		// 前面缺词，当分词后开头是一个字的时候才去查找候选词
		String[][] frontCandidates = null;
		String[] frontWord = null;
		long[] frontProbability = null;
		if (seg[0].length() == 1) {
			String[] candidatesStart = getStartSuggestions(seg[0]);
			if (candidatesStart.length > 0) {
				frontCandidates = new String[candidatesStart.length][seg.length];
				frontProbability = new long[candidatesStart.length];
				frontWord = new String[candidatesStart.length];
				for (int i=0; i<candidatesStart.length; i++) {
					frontCandidates[i][0] = candidatesStart[i];
					for (int j=1; j<seg.length; j++) {
						frontCandidates[i][j] = seg[j];
					}
					frontWord[i] = StringUtils.join(frontCandidates[i], "");
//					frontProbability[i] = query.getTotalHits(frontWord[i]);
//					if (frontProbability[i] > 1) {
//						addCandidate(candidates, probability, frontWord[i], frontProbability[i]);
//					}
				}
				Candidate[] candStarts = getGoResponse(frontWord);
				for (int i=0; i<candidatesStart.length; i++) {
					for (int j=0; j<candStarts.length; j++) {
						if (candStarts[j].word.equals(frontWord[i])) {
							frontProbability[i] = candStarts[j].prob;
							if (candStarts[j].prob >= minOccurrence) {
								addCandidate(candidates, probability, frontWord[i], candStarts[j].prob);
							}
							break;
						}
					}
				}
			}
		}

		// 后面缺词
		String[][] endCandidates = null;
		String[] endWord = null;
		long[] endProbability = null;
		String[] candidateEnd = getEndSuggestions(seg[seg.length-1]);
		if (candidateEnd.length > 0) {
			endCandidates = new String[candidateEnd.length][seg.length];
			endProbability = new long[candidateEnd.length];
			endWord = new String[candidateEnd.length];
			for (int i=0; i<candidateEnd.length; i++) {
				int j = 0;
				for (; j<seg.length-1; j++) {
					endCandidates[i][j] = seg[j];
				}
				endCandidates[i][j] = candidateEnd[i];
				endWord[i] = StringUtils.join(endCandidates[i], "");
//				endProbability[i] = query.getTotalHits(endWord[i]);
//				if (endProbability[i] > 1) {
//					addCandidate(candidates, probability, endWord[i], endProbability[i]);
//				}
			}
			Candidate[] candEnds = getGoResponse(endWord);
			for (int i=0; i<candidateEnd.length; i++) {
				for (int j=0; j<candEnds.length; j++) {
					if (candEnds[j].word.equals(endWord[i])) {
						endProbability[i] = candEnds[j].prob;
						if (candEnds[j].prob >= minOccurrence) {
							addCandidate(candidates, probability, endWord[i], candEnds[j].prob);
						}
						break;
					}
				}
			}
		}

		// 前后两端都缺词
		String[][] frontEndCandidates = null;
		ArrayList<String> fronEndWordal = new ArrayList<String>();
		String[] frontEndWords = null;
		if (frontCandidates != null && endCandidates != null) {
			frontEndCandidates = new String[frontCandidates.length*endCandidates.length][seg.length];
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
//					frontEndWord = StringUtils.join(frontEndCandidates[i], "");
					fronEndWordal.add(StringUtils.join(frontEndCandidates[i], ""));
//					long frontEndProbability = query.getTotalHits(frontEndWord);
//					if (frontEndProbability > 1) {
//						addCandidate(candidates, probability, frontEndWord, frontEndProbability);
//					}
				}
			}
			frontEndWords = new String[fronEndWordal.size()];
			fronEndWordal.toArray(frontEndWords);
			
			Candidate[] candFrontEnds = getGoResponse(frontEndWords);
			for (int i=0; i<frontEndWords.length; i++) {
				for (int j=0; j<candFrontEnds.length; j++) {
					if (candFrontEnds[j].word.equals(frontEndWords[i])) {
						if (candFrontEnds[j].prob >= minOccurrence) {
							addCandidate(candidates, probability, frontEndWords[i], candFrontEnds[j].prob);
						}
						break;
					}
				}
			}
		}

//		ArrayList<String> allGoCandList = new ArrayList<String>();
//		
//		// 前面缺词，当分词后开头是一个字的时候才去查找候选词
//		String[] candidatesStart = null;
//		if (seg[0].length() == 1) {
//			candidatesStart = getStartSuggestions(seg[0]);
//			for (int i=0; i<candidatesStart.length; i++) {
//				allGoCandList.add(candidatesStart[i]+word.substring(seg[0].length()));
//			}
//		}
//
//		// 后面缺词
//		String[] candidatesEnd = getEndSuggestions(seg[seg.length-1]);
//		if (candidatesEnd.length > 0) {
//			for (int i=0; i<candidatesEnd.length; i++) {
//				allGoCandList.add(word.substring(0, word.length()-seg[seg.length-1].length()+1) + candidatesEnd[i]);
//			}
//		}
//		
//		// 前后都缺词
//		if (null != candidatesStart && null != candidatesEnd) {
//			for (int i=0; i<candidatesStart.length; i++) {
//				for (int j=0; j<candidatesEnd.length; j++) {
//					allGoCandList.add(candidatesStart[i] + 
//							word.substring(seg[0].length(), word.length()-seg[seg.length-1].length()+1) + 
//							candidatesEnd[j]);
//				}
//			}
//		}
//
//		String[] allGoCand = new String[allGoCandList.size()];
//		allGoCandList.toArray(allGoCand);
	
		
		String[] allCandidates = new String[candidates.size()];
		candidates.toArray(allCandidates);
		// 去掉那些补全后分词仍然有一个字的字符串
		ArrayList<String> list = new ArrayList<String>();
		for (int i=0; i<allCandidates.length; i++) {
			if (!containSingleCharAfterSeg(allCandidates[i])) {
				list.add(allCandidates[i]);
			}
		}
		int maxNum = Math.min(10, list.size());
		String[] ret = new String[maxNum];
		for (int i=0; i<maxNum; i++) {
			ret[i] = list.get(i);
		}

		return ret;
	}
	
	protected Candidate[] getGoResponse(String[] allGoCand) {

		DefaultHttpClient httpClient = new DefaultHttpClient();
		//建立HttpPost对象
		HttpPost post = new HttpPost(prop.getProperty("goserver"));
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (int i=0; i<allGoCand.length; i++) {
			//建立一个NameValuePair数组，用于存储欲传送的参数
			params.add(new BasicNameValuePair("candidate", allGoCand[i]));
		}
		try {
			post.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		HttpResponse response = null;
		try {
			response = httpClient.execute(post);
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String result = null;
		if(response.getStatusLine().getStatusCode() == 200){
			try {
				result = EntityUtils.toString(response.getEntity());
			} catch (ParseException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		JSONObject jsonobj = new JSONObject(result);
		JSONArray words = jsonobj.getJSONArray("response");
		
		Candidate[] cand = new Candidate[words.length()];
		for (int i=0; i<words.length(); i++) {
			JSONObject e = words.getJSONObject(i);
			cand[i] = new Candidate(e.getString("Word"), e.getInt("Prob"));
		}
		
		return cand;
	}
	
}

class Candidate {
	public String word;
	public int prob;
	public Candidate(String word, int prob) {
		this.word = word;
		this.prob = prob;
	}
}
