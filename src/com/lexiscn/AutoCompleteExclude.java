package com.lexiscn;

import java.util.ArrayList;

import com.lexiscn.Lexicon;
import com.lexiscn.Trie;


public class AutoCompleteExclude {

	private String filepath;
	private Trie[] dic;
	
	public AutoCompleteExclude(String filepath) {
		this.filepath = filepath;

		dic = Lexicon.loadLexicon(this.filepath, 100);
	}
	
	public boolean contains(String word) {
		ArrayList<String> list = dic[0].search(word);
		if (list.size() > 0) {
			for (int i=0; i<list.size(); i++) {
				if (word.equals(list.get(i))) {
					return true;
				}
			}
		}
		return false;
	}
}
