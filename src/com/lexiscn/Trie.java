package com.lexiscn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 * Trie数据结构
 * <p>
 * 一个Trie的实例为一个<a href="http://zh.wikipedia.org/zh-cn/Trie">Trie</a>
 * </p>
 * @author Kevin Jiang kevin.jiang@lexisnexis.com
 *
 */
public class Trie {
	/**
	 * 根节点
	 */
	public TrieNode root;
	
	/**
	 * 初始化根节点为空的值
	 */
	public Trie() {
		root = new TrieNode();
	}
	
	/**
	 * 把一个字符串添加到Trie当中
	 * @param str
	 */
	public void add(String str) {
		add(root, str);
	}
	
	/**
	 * 把一个字符串添加到指定的node当中
	 * @param node
	 * @param str
	 */
	private void add(TrieNode node, String str) {
		if (str.length() == 0) {
			node.setWordEnd(true);
		} else {
			String c = str.substring(0, 1);
			if (node.contains(c)) {
				HashMap<String, TrieNode> child = node.getChildren();
				TrieNode childNode = child.get(c);
				add(childNode, str.substring(1));
			} else {
				TrieNode newChildNode = new TrieNode();
				newChildNode.setValue(c);
				node.addChildren(c, newChildNode);
				add(newChildNode, str.substring(1));
			}
		}
	}
	
	/**
	 * 使用递归的方式，搜索指定以prefix开头的字符串，结果会放入words当中
	 * @param node
	 * @param prefix
	 * @param words
	 */
	public void traverse(TrieNode node, String prefix, ArrayList<String> words) {
		prefix += node.getValue();
		HashMap<String, TrieNode> children = node.getChildren();
		if (node.getWordEnd()) {
			words.add(prefix);
		}
		Set<String> keys = children.keySet();
		for (String key: keys) {
			TrieNode childNode = children.get(key);
			traverse(childNode, prefix, words);
		}
	}
	
	/**
	 * 获取pfx开头的节点
	 * @param node
	 * @param pfx
	 * @return
	 */
	private TrieNode getNode(TrieNode node, String pfx) {
		if (pfx.length() == 1) {
			HashMap<String, TrieNode> child = node.getChildren();
			return child.get(pfx);
		} else {
			String c = pfx.substring(0, 1);
			HashMap<String, TrieNode> child = node.getChildren();
			if (child == null) {
				return node;
			} else {
				TrieNode childNode = child.get(c);
				if (childNode == null && pfx.length() == 0) {
					return childNode;
				} else if (childNode == null && pfx.length() > 0) {
					return null;
				}
				return getNode(childNode, pfx.substring(1));
			}
		}
	}
	
	/**
	 * 搜索所有以pfx开头的字符串
	 * @param pfx
	 * @return
	 */
	public ArrayList<String> search(String pfx) {
		ArrayList<String> words = new ArrayList<String>();
		if (pfx.length() == 0) {
			traverse(root, "", words);
		} else {
			TrieNode node = getNode(root, pfx);
			if (node != null) {
				traverse(node, "", words);
				for (int i=0; i<words.size(); i++) {
					String element = words.get(i);
					element = pfx.substring(0, pfx.length()-1)+element;
					words.set(i, element);
				}
			}
		}
		return words;
	}

}
