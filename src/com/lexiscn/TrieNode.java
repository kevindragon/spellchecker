package com.lexiscn;

import java.util.HashMap;

/**
 * Trie的节点类
 * 
 * 默认节点的值为空字符串，
 * @author Kevin Jiang kevin.jiang@lexisnexis.com
 *
 */
public class TrieNode {
	
	/**
	 * 当前节点的值
	 */
	private String value;
	
	/**
	 * 包含所有的子节点
	 */
	private HashMap<String, TrieNode> children;
	
	/**
	 * 标识从根节点到此节点是否是一个词
	 */
	private boolean wordEnd;
	
	public TrieNode() {
		value = "";
		children = new HashMap<String, TrieNode>();
		wordEnd = false;
	}
	
	/**
	 * 返回当前节点的值
	 * @return
	 */
	public String getValue() {
		return value;
	}
	
	/**
	 * 设置当前节点的值
	 * @param c
	 */
	public void setValue(String c) {
		value = c;
	}
	
	/**
	 * 获取子节点
	 * @return
	 */
	public HashMap<String, TrieNode> getChildren() {
		return children;
	}
	
	/**
	 * 给当前节点添加一个子节点
	 * @param key
	 * @param node
	 */
	public void addChildren(String key, TrieNode node) {
		if (!children.keySet().contains(key)) {
			children.put(key, node);
		}
	}
	
	/**
	 * 设置当前节点是否为词语结束
	 * @param bool
	 * @return
	 */
	public Boolean setWordEnd(boolean bool) {
		wordEnd = bool;
		return wordEnd;
	}
	
	/**
	 * 获取当前节点是否为一个词语的结尾
	 * @return
	 */
	public Boolean getWordEnd() {
		return wordEnd;
	}
	
	/**
	 * 检查当前节点是否有c为值的子节点
	 * @param s
	 * @return
	 */
	public Boolean contains(String s) {
		return children.get(s) != null;
	}
}
