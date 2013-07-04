package com.lexiscn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;

/**
 * Servlet implementation class SuggestIndexManager
 */
@WebServlet("/SuggestIndexManager")
public class SuggestIndexManager extends HttpServlet {
	private static final long serialVersionUID = 1L;

    private String urlString = "http://localhost:8080/solr/termrelated/";
    private HttpSolrServer solr = null;
    private IndexWriter indexWriter = null;
    private Directory indexDir = null;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public SuggestIndexManager() {
        super();
    }

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init();
		String webroot = config.getServletContext().getRealPath("/");
        IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_43, null);
		try {
			indexDir = FSDirectory.open(new File(webroot+"/data/index"));
			indexWriter = new IndexWriter(indexDir, conf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		response.setContentType("text/html");
		response.setCharacterEncoding("utf-8");
		String webroot = request.getServletContext().getRealPath("/");
		
		ArrayList<String> lines = new ArrayList<String>();
		String line = null;
		BufferedReader reader = new BufferedReader(
				new InputStreamReader (new FileInputStream(webroot+"/mydic.txt"), "UTF-8"));
		while ((line = reader.readLine()) != null) {
			lines.add(line);
		}
		
		PrintWriter writer = response.getWriter();

        solr = new HttpSolrServer(urlString);

		SolrQuery query = new SolrQuery();
		query.setStart(0);
        query.setRows(0);
		
		String t1 = null, t2 = null;
		long tn1 = 0, tn2 = 0, n12 = 0, total = 0;
		float corr = 0.0f;
		
		// get total doc
		query.setQuery("*:*");
		total = getNumFound(solr, query);
		writer.println("<br>" + total + "<br>");
		
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
					corr = (float) (Math.log10(total/tn1) * Math.log10(total/tn2) * n12) / (tn1 + tn2 + n12);
					index(t1, t2, corr);
				}
				
				writer.println(t1 + " " + tn1 + " | " + 
							   t2 + " " + tn2 + " | " + 
							   t1 + "-" + t2 + " " + n12 + " | corr: " + corr + "<br>");
			}
		}
		indexWriter.commit();
		indexWriter.close();

		indexDir.close();
		
		writer.print("index success");
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

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		this.doGet(request, response);
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
