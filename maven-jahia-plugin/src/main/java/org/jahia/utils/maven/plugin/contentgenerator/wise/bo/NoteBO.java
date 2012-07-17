package org.jahia.utils.maven.plugin.contentgenerator.wise.bo;

import org.jahia.utils.maven.plugin.contentgenerator.properties.ContentGeneratorCst;
import org.jdom.Element;

public class NoteBO {
	
	private Element note;
	
	private String name = "new-note";
	
	private String title = "New note";
	
	private String body = "Note content (generated)";
	
	public Element getElement() {
		if (note == null) {
			note = new Element(name);
			note.setAttribute("originWS", "default", ContentGeneratorCst.NS_J);
			note.setAttribute("primaryType", "docnt:note", ContentGeneratorCst.NS_JCR);
			note.setAttribute("title", title, ContentGeneratorCst.NS_JCR);
			note.setAttribute("body", title, ContentGeneratorCst.NS_JCR);
		}
		return note;
	}
}
