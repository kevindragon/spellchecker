package com.lexiscn;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class SuggestIndexManager
 */
@WebServlet("/SuggestIndexManager")
public class SuggestIndexManager extends HttpServlet {
	private static final long serialVersionUID = 1L;

	String webroot = "";
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public SuggestIndexManager() {
        super();
    }

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init();
		webroot = config.getServletContext().getRealPath("/");
	}

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		IndexManager im = new IndexManager();
		
		String indexStatus = "index failed";
		if (im.reIndex(webroot+"/data/index", webroot+"/mydic.txt")) {
			indexStatus = "index success";
		}
		
		PrintWriter writer = response.getWriter();
		writer.print(indexStatus);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		this.doGet(request, response);
	}
	
}
