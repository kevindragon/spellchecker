package com.lexiscn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;

public class IndexManager {

    private String urlString = "http://localhost:8080/solr/termrelated/";
    private HttpSolrServer solr = null;
    private IndexWriter indexWriter = null;
    private Directory indexDir = null;
    
    public boolean reIndex(String dir, String dicDir) throws IOException {
    	indexDir = FSDirectory.open(new File(dir));

    	ArrayList<String> lines = new ArrayList<String>();
		String line = null;
		BufferedReader reader = new BufferedReader(
				new InputStreamReader (
						new FileInputStream(dicDir), "UTF-8"));
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}

        solr = new HttpSolrServer(urlString);

		SolrQuery query = new SolrQuery();
		query.setStart(0).setRows(0);
		
		String t1 = null, t2 = null;
		long tn1 = 0, tn2 = 0, n12 = 0, total = 0;
		float corr = 0.0f;
		
		// get total doc
		query.setQuery("*:*");
		total = getNumFound(solr, query);

		if (!IndexWriter.isLocked(indexDir) && total>0) {
	        IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_43, null);
			indexWriter = new IndexWriter(indexDir, conf);
			indexWriter.deleteAll();
			
			for (int i=0; i<lines.size(); i++) {
				for (int j=i+1; j<lines.size(); j++) {
					t1 = lines.get(i);
					t2 = lines.get(j);
					
					query.setQuery("text:\"" + t1 + "\"");
					tn1 = getNumFound(solr, query);

					query.setQuery("text:\"" + t2 + "\"");
					tn2 = getNumFound(solr, query);;
					
					query.setQuery("text:\"" + t1 + "\" AND \"" + t2 + "\"");
					n12 = getNumFound(solr, query);
					
					if (n12>0) {
						corr = (float) ((Math.log10(total/tn1) * Math.log10(total/tn2) * n12) 
										 / (tn1 + tn2 + n12));
						index(t1, t2, corr);
					}
				}
			}
			indexWriter.commit();
			indexWriter.close();
		}

    	return true;
    }

	private void index(String t1, String t2, float corr) {
		Document doc = new Document();
		doc.add(new StringField("text", t1, Field.Store.YES));
		doc.add(new StringField("text", t2, Field.Store.YES));
		doc.add(new FloatField("corr", corr, Field.Store.YES));
		try {
			indexWriter.addDocument(doc);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private long getNumFound(HttpSolrServer server, SolrQuery query) {
		QueryResponse qrsp = null;
		SolrDocumentList docs = null;
		long num = 0;
		
		try {
			qrsp = server.query(query);
			docs = qrsp.getResults();
			num = docs.getNumFound();
		} catch (SolrServerException e) {
			e.printStackTrace();
		}
		return num;
	}

}
