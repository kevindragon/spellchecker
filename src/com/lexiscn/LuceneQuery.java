package com.lexiscn;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;

/**
 * 查询Lucene Server的操作集合
 * 
 * 设计为单例模式，并且使query对象为线程安全的
 * 
 * @author Kevin Jiang kevin.jiang@lexisnexis.com
 *
 */
public class LuceneQuery {

	private static String urlString = "http://10.123.4.210:8080/solr/termrelated/";
	private static HttpSolrServer solr;
	private static SolrQuery query;
	private static LuceneQuery instance = null;
	
	public LuceneQuery() {
		solr = new HttpSolrServer(urlString);
		query = new SolrQuery();
		query.setStart(0).setRows(0);
		try {
			solr.query(query);
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 单例模式的外部接口
	 * @return
	 */
	public static LuceneQuery getInstance() {
		if (instance == null) {
			instance = new LuceneQuery();
		}
		return instance;
	}

	/**
	 * 获取精确word的结果数量，在text域里面查询
	 * @param word
	 * @return 
	 */
	public long getTotalHits(String word) {
		QueryResponse qrsp = null;
		SolrDocumentList docs = null;
		long num = 0;
//		synchronized (query) {
			query.setQuery("text:\"" + word.replace("\"", "") + "\"");
			try {
				qrsp = solr.query(query);
				docs = qrsp.getResults();
				num = docs.getNumFound();
			} catch (Exception e) {
				e.printStackTrace();
			}
//		}
		return num;
	}

}
