/*******************************************************************************
 * Copyright (C) 2009 The University of Manchester   
 * 
 *  Modifications to the initial code base are copyright of their
 *  respective authors, or their employers as appropriate.
 * 
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2.1 of
 *  the License, or (at your option) any later version.
 *    
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *    
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 ******************************************************************************/
package net.sf.taverna.t2.workbench.views.results.processor;

import org.apache.log4j.Logger;

import javax.swing.tree.DefaultTreeModel;

import net.sf.taverna.t2.reference.IdentifiedList;
import net.sf.taverna.t2.reference.ReferenceService;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.reference.T2ReferenceType;

/**
 * Tree model for the results of a processor (workflow's intermediate results).
 * 
 * @author Alex Nenadic
 *
 */
@SuppressWarnings("serial")
public class ProcessorResultsTreeModel extends DefaultTreeModel{
	
	// Tree root
	private ProcessorResultTreeNode root;

	private ReferenceService referenceService;

	private Logger logger = Logger.getLogger(ProcessorResultsTreeModel.class);
	
	public ProcessorResultsTreeModel(T2Reference t2Reference, ReferenceService referenceService) {
		
		super(new ProcessorResultTreeNode());
		root = (ProcessorResultTreeNode)getRoot();
		this.referenceService = referenceService;
		
		createTree(t2Reference, root);
	}

	private void createTree(T2Reference t2Ref, ProcessorResultTreeNode parentNode){
		
		// If reference contains a list of data references
		if (t2Ref.getReferenceType() == T2ReferenceType.IdentifiedList) {
			IdentifiedList<T2Reference> list = referenceService
					.getListService().getList(t2Ref);
			if (list == null) {
				logger.error("Could not resolve list " + t2Ref + ", was run with in-memory storage?");
			}
			ProcessorResultTreeNode listNode = new ProcessorResultTreeNode(list.size(), t2Ref, referenceService); // list node
			parentNode.add(listNode);
			for (T2Reference ref : list) {
				createTree(ref, listNode);
			}
		} else { // reference to single data or an error
			insertDataNode(t2Ref, parentNode);
		}	
	}

	private void insertDataNode(T2Reference t2Ref, ProcessorResultTreeNode parent) {
		ProcessorResultTreeNode dataNode = new ProcessorResultTreeNode(t2Ref, referenceService); // data node
		parent.add(dataNode);
	}

}
