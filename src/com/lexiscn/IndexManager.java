package com.lexiscn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.NGramDistance;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
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
    
    public boolean reIndexMI(String dir, String dicDir) throws IOException {
    	boolean bool = false;
    	indexDir = FSDirectory.open(new File(dir));

    	ArrayList<String> lines = new ArrayList<String>();
		String line = null;

		BufferedReader reader = new BufferedReader(
				new InputStreamReader (
						new FileInputStream(dicDir), "UTF-8"));
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}
		reader.close();

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
						if (corr > 0.01f) {
							index(t1, t2, corr);
						}
					}
				}
			}
			indexWriter.commit();
			indexWriter.close();
			bool = true;
		}
		
		indexDir.close();

    	return bool;
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
		} catch (Exception e) {
			e.printStackTrace();
		}
		return num;
	}
	
	public boolean reIndexSpellchecker(String dictFile, String indexDir) 
			throws IOException {
		boolean bool = this.delDir(indexDir);
		File spellIndexDir = new File(indexDir);
		
		Analyzer analyzer = new CJKAnalyzer(Version.LUCENE_43);
		SpellChecker spellchecker = new SpellChecker(
				FSDirectory.open(spellIndexDir), 
				new NGramDistance(2));
		IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_43, analyzer);
		spellchecker.indexDictionary(
				new PlainTextDictionary(
						new File(dictFile)), conf, false);
		spellchecker.close();
		
		return bool;
	}
	
	private boolean delDir(String dir) {
		boolean bool = true;
		File f = new File(dir);
		if(f.exists() && f.isDirectory()){
			if(f.listFiles().length == 0){
				bool &= f.delete();  
			} else {
				File delFile[] = f.listFiles();
				int i = f.listFiles().length;
				for(int j=0;j<i;j++){
					if(delFile[j].isDirectory()){
						bool &= this.delDir(delFile[j].getAbsolutePath());
					}
					bool &= delFile[j].delete();
				}
			}
		}
		return bool;
	}

}
