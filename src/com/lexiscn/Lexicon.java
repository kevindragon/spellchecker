package com.lexiscn;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * 词典操作
 * 
 * @author Kevin Jiang kevin.jiang@lexisnexis.com
 *
 */
public class Lexicon {

	/**
	 * 保存一个前向和一个后向的Trie
	 * <pre>
	 * 比如有两个字符串：中国，中华
	 * 
	 *                                         / [国]
	 * lexicon[0]为前向的Trie： [root] [中] - |
	 *                                         \ [华]
	 *                                         
	 *                                  / [国] - [中]
	 * lexicon[1]为后向的Trie：[root] -|
	 *                                  \ [华] - [中]
	 * </pre>
	 */
	private static Trie[] lexicon = null;
	
	/**
	 * 从文件加载词语，同时生成Trie和反向的Trie
	 * @return Trie[]
	 */
	public static Trie[] loadLexicon(String filename, int maxLength) {
		if (null != lexicon) {
			return lexicon;
		}

		Trie[] lexicon = {new Trie(), new Trie()};

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() <= 1 || line.length() > maxLength) {
					continue;
				}
				StringBuffer sb = new StringBuffer(line);
				lexicon[0].add(line);
				lexicon[1].add(sb.reverse().toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return lexicon;
	}

	
}
