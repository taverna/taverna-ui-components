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
package net.sf.taverna.t2.workbench.design.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import net.sf.taverna.t2.workbench.edits.CompoundEdit;
import net.sf.taverna.t2.workbench.edits.Edit;
import net.sf.taverna.t2.workbench.edits.EditException;
import net.sf.taverna.t2.workbench.edits.EditManager;
import net.sf.taverna.t2.workbench.icons.WorkbenchIcons;
import net.sf.taverna.t2.workbench.ui.DataflowSelectionManager;
import net.sf.taverna.t2.workflow.edits.RemoveDataLinkEdit;
import net.sf.taverna.t2.workflow.edits.RemoveDataflowOutputPortEdit;

import org.apache.log4j.Logger;

import uk.org.taverna.scufl2.api.core.DataLink;
import uk.org.taverna.scufl2.api.core.Workflow;
import uk.org.taverna.scufl2.api.port.OutputWorkflowPort;

/**
 * Action for removing an output port from the dataflow.
 *
 * @author David Withers
 */
public class RemoveDataflowOutputPortAction extends DataflowEditAction {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(RemoveDataflowOutputPortAction.class);

	private OutputWorkflowPort port;

	public RemoveDataflowOutputPortAction(Workflow dataflow, OutputWorkflowPort port, Component component, EditManager editManager, DataflowSelectionManager dataflowSelectionManager) {
		super(dataflow, component, editManager, dataflowSelectionManager);
		this.port = port;
		putValue(SMALL_ICON, WorkbenchIcons.deleteIcon);
		putValue(NAME, "Delete workflow output port");
	}

	public void actionPerformed(ActionEvent e) {
		try {
			List<DataLink> datalinks = scufl2Tools.datalinksTo(port);
			if (datalinks.isEmpty()) {
				editManager.doDataflowEdit(dataflow.getParent(), new RemoveDataflowOutputPortEdit(dataflow, port));
			} else {
				List<Edit<?>> editList = new ArrayList<Edit<?>>();
				for (DataLink datalink : datalinks) {
					editList.add(new RemoveDataLinkEdit(dataflow, datalink));
				}
				editList.add(new RemoveDataflowOutputPortEdit(dataflow, port));
				editManager.doDataflowEdit(dataflow.getParent(), new CompoundEdit(editList));
			}
			dataflowSelectionModel.removeSelection(port);
		} catch (EditException e1) {
			logger.debug("Delete workflow output port failed", e1);
		}
	}

}
