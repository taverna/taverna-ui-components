/*******************************************************************************
 * Copyright (C) 2007 The University of Manchester   
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
package net.sf.taverna.t2.workbench.views.results.workflow;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;

import net.sf.taverna.t2.facade.ResultListener;
import net.sf.taverna.t2.invocation.InvocationContext;
import net.sf.taverna.t2.invocation.WorkflowDataToken;
import net.sf.taverna.t2.reference.IdentifiedList;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.reference.T2ReferenceType;
import net.sf.taverna.t2.workbench.views.results.workflow.WorkflowResultTreeNode.ResultTreeNodeState;

import org.apache.log4j.Logger;

public class WorkflowResultTreeModel extends DefaultTreeModel implements ResultListener {

	private static final long serialVersionUID = 7154527821423588046L;

	private static Logger logger = Logger.getLogger(WorkflowResultTreeModel.class);
	
	// Name of the output port this class models results for
	private String portName;
	
	// Output port depth (0 for single result, 1 for list, 2 for list of lists ...)
	int depth;
	
	int depthSeen = -1;

	public WorkflowResultTreeModel(String portName, int depth) {
		super(new WorkflowResultTreeNode(ResultTreeNodeState.RESULT_TOP));
		this.portName = portName;
		this.depth = depth;
	}

	public void resultTokenProduced(final WorkflowDataToken dataToken, final String portName) {
		// Don't slow down workflow execution, do it in GUI thread
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				resultTokenProducedGui(dataToken, portName);
			}
		});
	}

	public void resultTokenProducedGui(WorkflowDataToken dataToken, String portName) {		
		int[] index = dataToken.getIndex();
		if (this.portName.equals(portName)) {
			if (depthSeen == -1) {
				depthSeen = index.length;
			}

			if (index.length >= depthSeen) {
				T2Reference reference = dataToken.getData();

				if (reference.getReferenceType() == T2ReferenceType.IdentifiedList) {

					try {
						WorkflowResultTreeNode parent = (WorkflowResultTreeNode) getRoot();
						parent = getChildAt(parent,0);
						changeState(parent, ResultTreeNodeState.RESULT_LIST);
						for (int i = 0; i < index.length; i++) {
							parent = getChildAt(parent, index[i]);
							changeState(parent, ResultTreeNodeState.RESULT_LIST);
						}
						
						IdentifiedList<T2Reference> list = dataToken
								.getContext().getReferenceService()
								.getListService().getList(reference);
						int[] elementIndex = new int[index.length + 1];
						for (int indexElement = 0; indexElement < index.length; indexElement++) {
							elementIndex[indexElement] = index[indexElement];
						}
						int c = 0;
						for (T2Reference id : list) {
							elementIndex[index.length] = c;
							resultTokenProducedGui(new WorkflowDataToken(dataToken
									.getOwningProcess(), elementIndex, id,
									dataToken.getContext()), portName);
							c++;
						}
//						if (c == 0) {
//							parent.setUserObject("Empty list (depth=" + reference.getDepth() + ")" + reference.getLocalPart());
//							nodeChanged(parent);
//						}
					} catch (NullPointerException e) {
						logger.error("Error resolving data entity list "
								+ reference, e);
					}
				} else {
					insertNewDataTokenNode(reference, index, dataToken
							.getOwningProcess(), dataToken.getContext());
				}
			}
		}

	}

	public void insertNewDataTokenNode(T2Reference reference, int[] index,
			String owningProcess, InvocationContext context) {
		WorkflowResultTreeNode parent = (WorkflowResultTreeNode) getRoot();
		if (index.length == depth) {
			if (depth == 0) {
				WorkflowResultTreeNode child = getChildAt(parent, 0);
				updateNodeWithData(child, reference,
						context);
			} else {
				parent = getChildAt(parent, 0);
				changeState(parent, ResultTreeNodeState.RESULT_LIST);
				for (int indexElement = 0; indexElement < depth; indexElement++) {
					WorkflowResultTreeNode child = getChildAt(parent,
							index[indexElement]);
					if (indexElement == (depth - 1)) { // leaf
						updateNodeWithData(child, reference, context);
					} else { // list
						child.setState(ResultTreeNodeState.RESULT_LIST);
						nodeChanged(child);
					}
					parent = child;
				}
			}
		} else if (reference.getReferenceType() == T2ReferenceType.ErrorDocument) {
			parent = getChildAt(parent, 0);
			for (int i = 0; i < index.length - 1; i++) {
				parent = getChildAt(parent, index[i]);
				parent = getChildAt(parent, 0);
				changeState(parent, ResultTreeNodeState.RESULT_LIST);
			}
			if (index.length > 0) {
				WorkflowResultTreeNode child = getChildAt(parent, index[index.length-1]);
				updateNodeWithData(child, reference, context);
			} else {
				updateNodeWithData(parent, reference, context);
			}
		}
	}

	private void updateNodeWithData(WorkflowResultTreeNode node, 
			T2Reference reference,
			InvocationContext context) {	
		node.setState(ResultTreeNodeState.RESULT_REFERENCE);
		node.setReference(reference);
		node.setContext(context);
		nodeChanged(node);
	}

	private WorkflowResultTreeNode getChildAt(WorkflowResultTreeNode parent, int i) {
		int childCount = getChildCount(parent);
		if (childCount <= i) {
			for (int x = childCount; x <= i; x++) {
				insertNodeInto(new WorkflowResultTreeNode(WorkflowResultTreeNode.ResultTreeNodeState.RESULT_WAITING),
						parent, x);
			}
		}

		return (WorkflowResultTreeNode) parent.getChildAt(i);
	}
	
	private void changeState(WorkflowResultTreeNode node, ResultTreeNodeState state) {
		if (!node.isState(state)) {
			node.setState(state);
			nodeChanged(node);
		}
		
	}

	// Normally used for past workflow runs where data is obtained from provenance
	public void createTree(T2Reference t2Ref, InvocationContext context,  WorkflowResultTreeNode parentNode){
		
		// If reference contains a list of data references
		if (t2Ref.getReferenceType() == T2ReferenceType.IdentifiedList) {
			try {
				IdentifiedList<T2Reference> list = context.getReferenceService()
						.getListService().getList(t2Ref);
				if (list == null) {
					logger.warn("Could not resolve " + t2Ref);
					return;
				}
				WorkflowResultTreeNode listNode = new WorkflowResultTreeNode(t2Ref, context, ResultTreeNodeState.RESULT_LIST); // list node
				listNode.setContext(context);
				insertNodeInto(listNode, parentNode, parentNode.getChildCount());
				for (T2Reference ref : list) {
					createTree(ref, context, listNode);
				}
			} catch (NullPointerException e) {
				logger .error("Error resolving data entity list "
						+ t2Ref, e);
			}
		} else { // reference to single data or an error
			// insert data node
		    WorkflowResultTreeNode dataNode = new WorkflowResultTreeNode(t2Ref, context, ResultTreeNodeState.RESULT_REFERENCE); // data node
		    insertNodeInto(dataNode, parentNode, parentNode.getChildCount());
		}	
	}
	
}
