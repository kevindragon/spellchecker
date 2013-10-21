package com.lexiscn;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * Servlet implementation class Suggest
 */
@WebServlet("/Suggest")
public class Suggest extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Suggest() {
        super();
    }

    SpellChecker spellchecker = null;
    Analyzer analyzer = null;
    
    Directory trIndexDir = null;
	IndexReader ir = null;
	IndexSearcher is = null;
	FrontEndAutoCompletion ac = null;
    
	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		String webroot = config.getServletContext().getRealPath("/");
		// 初始化前后缺词的补全引擎
		ac = new FrontEndAutoCompletion(config);
		
		IndexManager im = new IndexManager();
		// 初始化lucene自带的spellchecker
		try {
			File spellIndexDir = new File(webroot+"/spellIndexDirectory");
			if (!spellIndexDir.exists()) {
				im.reIndexSpellchecker(webroot+"/dictsrc.txt", webroot+"/spellIndexDirectory");
			}
			
			analyzer = new CJKAnalyzer(Version.LUCENE_43);
			spellchecker = new SpellChecker(
					FSDirectory.open(spellIndexDir));
		} catch (IOException e) {
			e.printStackTrace();
		}
		// 初始化使用互信息计算的词典跟索引
		try {
			File indexFile = new File(webroot+"/data/index");
			if (!indexFile.exists()) {
				im.reIndexMI(webroot+"/data/index", webroot+"/mydic.txt");
			}
			trIndexDir = FSDirectory.open(new File(webroot+"/data/index"));
			ir = DirectoryReader.open(trIndexDir);
			is = new IndexSearcher(ir);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see Servlet#destroy()
	 */
	public void destroy() {
		try {
			spellchecker.close();
			ir.close();
			trIndexDir.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {

		// set response header
		response.setContentType("text/html");
		response.setCharacterEncoding("utf-8");
		PrintWriter writer = response.getWriter();
		
		// init response content
		String returnStr = "";
		
		// get parameter q
		String q = request.getParameter("q");
		if (null != q) q = q.trim();
		String accuracyParam = request.getParameter("accuracy");
		float accuracy = getAccuracy(accuracyParam, q);
		
		if (null != q && q.length() >= 2) {
			if (q.length() <= 3) {
				q = q+"  ";
			}

			long spst = System.currentTimeMillis();
			String[] suggestions = stringArrayTrim(spellchecker.suggestSimilar(q, 20, accuracy));
			long spet = System.currentTimeMillis();
			String cands1 = suggestions.length == 0 ? "[]" : "[\"" + StringUtils.join(suggestions, "\",\"") + "\"]";
			String spellcheckerStr = "{\"suggest\":" + cands1 + ", \"time\":" + (spet - spst) + 
					", \"accuracy\":" + accuracy + "}";
			
			ArrayList<String> text = getTermRelated(q.trim());
			String[] termRelated = new String[text.size()];
			text.toArray(termRelated);
			long tret = System.currentTimeMillis();
			String cands2 = termRelated.length == 0 ? "[]" : "[\"" + StringUtils.join(termRelated, "\",\"") + "\"]";
			String trStr = "{\"suggest\":" + cands2 + ", \"time\":" + (tret - spet) + "}";
			
			long acStart = System.currentTimeMillis();
			String[] autoCompletionWords = ac.suggestAutoCompletion(q.trim());
			long acEnd = System.currentTimeMillis();
			String cands3 = autoCompletionWords.length == 0 ? "[]" : "[\"" + StringUtils.join(autoCompletionWords, "\",\"") + "\"]";
			String acStr = "{\"suggest\":" + cands3 + ", \"time\":" + (acEnd - acStart) + "}";
			
			returnStr = "[" + spellcheckerStr + ", " + acStr + ", " + trStr + "]";
		} else {
			returnStr = "[{\"suggest\":[], \"time\":0, \"accuracy\":0.62}, {\"suggest\":[], \"time\":0}, {\"suggest\":[], \"time\":0}]";
		}
		
		writer.print(returnStr);
	}
	
	private ArrayList<String> getTermRelated(String term) throws IOException {
		ArrayList<String> related = new ArrayList<String>();

		int docNum = 0;
		try {
			docNum = ir.numDocs();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (docNum > 0) {
			Query query = new TermQuery(new Term("text", term));  
			TopDocs hits = is.search(query, 20, 
					new Sort(new SortField("corr", SortField.Type.FLOAT, true)));
			
			for (ScoreDoc scoreDoc: hits.scoreDocs) {
				Document doc = is.doc(scoreDoc.doc);
				String text[] = doc.getValues("text");
				for (int i=0; i<text.length; i++) {
					if (text[i].equals(term)) {
						continue;
					}
					related.add(text[i]);
				}
			}
		}
		
		return related;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		this.doGet(req, resp);
	}

	private float getAccuracy(String accuracyParam, String q) {
		float accuracy = 0.62f;
		
		if (null != accuracyParam) {
			try {
				Float flt = Float.valueOf(accuracyParam);
				accuracy = flt.floatValue();
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (null != q) {
			if (q.length() >=5 && q.length() < 10) {
				accuracy = 0.7f;
			} else if (q.length() >= 10) {
				accuracy = 0.8f;
			}
		}
		
		return accuracy;
	}
	
	private String[] stringArrayTrim(String[] array) {
		for (int i=0; i<array.length; i++) {
			array[i] = array[i].trim();
		}
		return array;
	}
}
