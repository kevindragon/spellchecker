package com.lexiscn;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.spell.NGramDistance;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.spell.SpellChecker;
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
        // TODO Auto-generated constructor stub
    }

    SpellChecker spellchecker = null;
    Analyzer analyzer = null;
    
	/**
	 * @see Servlet#init(ServletConfig)
	 */
	public void init(ServletConfig config) throws ServletException {
		String webroot = config.getServletContext().getRealPath("/");
		try {
//			analyzer = new NGramAnalyzer(2, 2);
//			spellchecker = new SpellChecker(FSDirectory.open(new File(webroot+"/spellIndexDirectory")), 
//					new NGramDistance(2));
			analyzer = new CJKAnalyzer(Version.LUCENE_43);
			spellchecker = new SpellChecker(
					FSDirectory.open(new File(webroot+"/spellIndexDirectory")), 
					new NGramDistance(2));
			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_43, analyzer);
			spellchecker.indexDictionary(
					new PlainTextDictionary(
							new File(webroot+"/dictsrc.txt")), conf, false);
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		Long st = System.currentTimeMillis();
		// set response header
		response.setContentType("text/html");
		response.setCharacterEncoding("utf-8");
		PrintWriter writer = response.getWriter();
		// init response content
		String suggestStr = "";
		String returnStr = "";
		// get parameter q
		String q = request.getParameter("q");
		if (null != q && q.length() >= 2) {
			q = q.length() <= 3 ? q+" " : q;
			String[] suggestions = spellchecker.suggestSimilar(q, 10, 0.6f);
			for (String word : suggestions) {
				suggestStr += word.trim()+"|";
			}
		}
		Long et = System.currentTimeMillis();
		if (suggestStr.endsWith("|")) {
			suggestStr = suggestStr.substring(0, suggestStr.length()-1);
		}
		returnStr = "{\"suggest\":\"" + suggestStr + "\", \"time\":" + (et - st) + "}";
		writer.print(returnStr);
	}

}
